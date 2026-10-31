package io.bucketeer.sdk.android.e2e

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.BKTEvaluationDetails
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Streaming E2E tests with a direct connection to the backend, as close as a real app. This is
 * the only E2E run where the SDK's own stream connection meets the real gateway (TLS, HTTP/2,
 * real chunking and buffering).
 *
 * These tests check only what an app can see: values, reasons and listener calls. They cannot
 * tell whether a value came from the stream or from the polling fallback;
 * [BKTClientStreamingProxyTests] checks that for the same cases. See `README.md` in this folder.
 */
@RunWith(AndroidJUnit4::class)
class BKTClientStreamingDirectTests {
  private lateinit var context: Context

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    setProcessLifecycleState(Lifecycle.State.STARTED)
  }

  @After
  fun tearDown() {
    destroyClientAndClearData(context)
    setProcessLifecycleState(Lifecycle.State.CREATED)
  }

  @Test
  fun update_user_attributes_reevaluates_over_the_stream() {
    // feature-android-e2e-string has a targeting rule on the E2E backend: app_version "0.0.1"
    // resolves to "value-2" with reason RULE instead of the default "value-1".
    assertThat(initializeClient(context, streamingConfig())).isNull()
    val client = BKTClient.getInstance()
    assertThat(client.stringVariation(FEATURE_ID_STRING, "")).isEqualTo("value-1")

    val listener = RecordingEvaluationListener { client.stringVariation(FEATURE_ID_STRING, "") }
    client.addEvaluationUpdateListener(listener)

    client.updateUserAttributes(mapOf("app_version" to "0.0.1"))

    assertEventually(StreamingTimeout.VALUE_MILLIS, { "value-2 did not arrive" }) {
      client.stringVariation(FEATURE_ID_STRING, "") == "value-2"
    }
    // The listener must see value-2 itself. A call for any other update does not count. The
    // listener runs on the main thread a moment after the value is stored, so wait for it.
    assertEventually(StreamingTimeout.VALUE_MILLIS, { "the listener never saw value-2; observed: ${listener.observed}" }) {
      listener.observed.contains("value-2")
    }
    assertThat(client.stringVariationDetails(FEATURE_ID_STRING, "").reason)
      .isEqualTo(BKTEvaluationDetails.Reason.RULE)
  }

  @Test
  fun server_pushed_patch_reaches_the_app() {
    // Fails here, naming the setting, when the write key is missing.
    FeatureFlagApi.publicApiKey()

    assertThat(initializeClient(context, streamingConfig())).isNull()
    val client = BKTClient.getInstance()

    // Both are sent back with the new value: the server replaces the whole variation.
    val details = client.stringVariationDetails(FEATURE_ID_STREAMING_DIRECT, "")
    assertWithMessage("$FEATURE_ID_STREAMING_DIRECT has no evaluation for tag $FEATURE_TAG; create it in the console")
      .that(details.variationId)
      .isNotEmpty()

    val value = FeatureFlagApi.newStreamingTestValue()
    val listener = RecordingEvaluationListener { client.stringVariation(FEATURE_ID_STREAMING_DIRECT, "") }
    client.addEvaluationUpdateListener(listener)

    // No wait for the stream to open: an app cannot see that. If the write lands before the
    // stream opens, the value may never arrive and this test fails on its own message.
    FeatureFlagApi.setVariationValue(FEATURE_ID_STREAMING_DIRECT, details.variationId, details.variationName, value)

    // Every observed value is checked, not only the latest: an overlapping run can overwrite
    // the flag right after our write.
    assertEventually(StreamingTimeout.VALUE_MILLIS, { "$value never reached the app; observed: ${listener.observed}" }) {
      listener.observed.contains(value)
    }
  }
}
