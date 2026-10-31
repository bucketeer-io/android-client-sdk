package io.bucketeer.sdk.android.e2e

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.BKTUser
import io.bucketeer.sdk.android.BuildConfig
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.database.OpenHelperCallback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * The flag the streaming patch tests write to. Each run writes a fresh value, and no test reads
 * it back as a baseline, so it is never restored.
 */
const val FEATURE_ID_STREAMING = "feature-android-e2e-sse-string"

const val GET_EVALUATIONS_PATH = "/get_evaluations"
const val STREAM_EVALUATIONS_PATH = "/${Constants.Streaming.STREAM_EVALUATIONS_PATH}"

/**
 * Timeouts for the streaming E2E waits. With streaming off, the E2E config polls every 10
 * minutes, so a poll can never deliver a value within these waits and hide a broken stream.
 */
object StreamingTimeout {
  /** The stream's reply, as seen by the proxy server. */
  const val STREAM_OPEN_MILLIS = 20_000L

  /** A value or a listener call, as seen by the app. */
  const val VALUE_MILLIS = 20_000L

  /** The test's own PATCH to the backend. */
  const val PATCH_REQUEST_MILLIS = 10_000L
}

fun streamingConfig(apiEndpoint: String = BuildConfig.API_ENDPOINT): BKTConfig =
  BKTConfig
    .builder()
    .apiKey(BuildConfig.API_KEY)
    .apiEndpoint(apiEndpoint)
    .featureTag(FEATURE_TAG)
    .appVersion("1.2.3")
    .enableStreaming(true)
    .build()

fun streamingUser(): BKTUser =
  BKTUser
    .builder()
    .id(USER_ID)
    .build()

/**
 * Initializes on the main thread, as the other E2E tests do, but waits off it: the SDK opens
 * the stream from a block it posts to the main thread.
 */
fun initializeClient(
  context: Context,
  config: BKTConfig,
  timeoutMillis: Long = 5_000,
): BKTException? {
  lateinit var future: Future<BKTException?>
  InstrumentationRegistry.getInstrumentation().runOnMainSync {
    future = BKTClient.initialize(context, config, streamingUser(), timeoutMillis)
  }
  return future.get()
}

/**
 * These tests start no activity, so the app never reaches the foreground on its own, and the
 * SDK opens the stream only in the foreground. Set STARTED before a test and CREATED after it.
 */
fun setProcessLifecycleState(state: Lifecycle.State) {
  InstrumentationRegistry.getInstrumentation().runOnMainSync {
    (ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry).currentState = state
  }
}

fun destroyClientAndClearData(context: Context) {
  InstrumentationRegistry.getInstrumentation().runOnMainSync {
    BKTClient.destroy()
  }
  context.deleteDatabase(OpenHelperCallback.FILE_NAME)
  context
    .getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE)
    .edit()
    .clear()
    .commit()
}

/** Polls [condition] until it holds or [timeoutMillis] passes, then fails with [message]. */
fun assertEventually(
  timeoutMillis: Long,
  message: () -> String,
  condition: () -> Boolean,
) {
  if (!awaitUntil(timeoutMillis, condition)) {
    assertWithMessage(message()).fail()
  }
}

/**
 * Records every listener call and the value read at that moment, instead of only the latest
 * value: an overlapping run can overwrite the patch test's flag right after our write.
 * Listeners are called on the main thread; tests read [observed] from the test thread.
 */
class RecordingEvaluationListener(
  private val read: () -> String,
) : BKTClient.EvaluationUpdateListener {
  val observed: MutableList<String> = CopyOnWriteArrayList()

  override fun onUpdate() {
    observed.add(read())
  }
}

/**
 * Writes flag values on the backend for the streaming patch tests. Calls go straight to
 * `API_ENDPOINT`, never through the proxy server, which only carries the SDK's traffic.
 */
object FeatureFlagApi {
  private val client =
    OkHttpClient
      .Builder()
      .callTimeout(StreamingTimeout.PATCH_REQUEST_MILLIS, TimeUnit.MILLISECONDS)
      .build()

  /**
   * The write key. Fails with a message naming the setting, instead of a 401 from the server
   * that doesn't say what is missing.
   */
  fun publicApiKey(): String {
    val apiKey = BuildConfig.PUBLIC_API_KEY
    if (apiKey.isEmpty()) {
      assertWithMessage(
        "public_api_key (local.properties) or PUBLIC_API_KEY (environment) is not set (needed by the streaming patch tests)",
      ).fail()
    }
    return apiKey
  }

  /** A value no other run writes, so two overlapping runs never wait for the same value. */
  fun newStreamingTestValue(): String = "android-sse-e2e-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}"

  /**
   * Replaces the value of one variation. The server replaces the whole variation, so its
   * current name must be sent along with the new value.
   */
  fun setVariationValue(
    featureId: String,
    variationId: String,
    variationName: String,
    value: String,
  ) {
    val variation =
      JSONObject()
        .put("id", variationId)
        .put("name", variationName)
        .put("value", value)
    val body =
      JSONObject()
        .put("id", featureId)
        .put("comment", "android e2e streaming test")
        .put("variationChanges", JSONArray().put(JSONObject().put("changeType", "UPDATE").put("variation", variation)))
    val request =
      Request
        .Builder()
        .url("${BuildConfig.API_ENDPOINT.removeSuffix("/")}/v1/feature")
        .header("Authorization", publicApiKey())
        .patch(body.toString().toByteArray().toRequestBody("application/json".toMediaType()))
        .build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        assertWithMessage("failed to update $featureId: HTTP ${response.code} ${response.body?.string()}").fail()
      }
    }
  }
}
