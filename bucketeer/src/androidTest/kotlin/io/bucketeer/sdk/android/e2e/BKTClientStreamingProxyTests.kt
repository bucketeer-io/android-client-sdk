package io.bucketeer.sdk.android.e2e

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.BKTEvaluationDetails
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.BuildConfig
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Streaming E2E tests that can see the network. The SDK is pointed at a proxy server inside the
 * test ([RecordingProxy]) through the public `apiEndpoint` setting; the proxy forwards to the
 * real backend and records every request. That lets these tests check that values arrive over
 * the stream, not through a REST fetch.
 *
 * What they cannot see: the SDK's own connection to the real gateway (TLS, HTTP/2, the
 * gateway's chunking). [BKTClientStreamingDirectTests] runs the user-visible checks without the
 * proxy for that. See `README.md` in this folder.
 */
@RunWith(AndroidJUnit4::class)
class BKTClientStreamingProxyTests {
  private lateinit var context: Context
  private lateinit var proxy: RecordingProxy

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    proxy = RecordingProxy(BuildConfig.API_ENDPOINT)
    setProcessLifecycleState(Lifecycle.State.STARTED)
  }

  @After
  fun tearDown() {
    destroyClientAndClearData(context)
    // After destroy(): the old stream's traffic is never counted, and nothing is left running.
    proxy.stop()
    setProcessLifecycleState(Lifecycle.State.CREATED)
  }

  @Test
  fun stream_opens_after_initialize() {
    assertThat(initializeClient(context, config())).isNull()

    // The stream opens once the init fetch has finished. Waiting for its reply, not only its
    // request, means a 401 or a 404 never passes.
    assertEventually(StreamingTimeout.STREAM_OPEN_MILLIS, { "the stream did not open; requests: ${proxy.records}" }) {
      streamOpenCount() >= 1
    }

    // The init fetch is the only get_evaluations call. A second one would mean the stream gave
    // up and the polling fallback fetched instead.
    assertWithMessage("requests: ${proxy.records}").that(proxy.count(GET_EVALUATIONS_PATH)).isEqualTo(1)

    // Exactly one stream request, and it has not failed. A stream that opened and then broke
    // would show up here as a retry or an error.
    val streamRequests = proxy.records.filter { it.path.endsWith(STREAM_EVALUATIONS_PATH) }
    assertWithMessage("expected one stream request; requests: ${proxy.records}").that(streamRequests).hasSize(1)
    assertThat(streamRequests.single().error).isNull()
    assertWithMessage("the stream ended right after opening; requests: ${proxy.records}")
      .that(streamRequests.single().ended)
      .isFalse()
  }

  @Test
  fun evaluations_arrive_over_the_stream_when_the_init_fetch_times_out() {
    assertThat(initializeClient(context, config(), timeoutMillis = 1))
      .isInstanceOf(BKTException.TimeoutException::class.java)
    // The stream still opens after a failed init fetch, and its snapshot fills the cache.
    val client = BKTClient.getInstance()

    assertEventually(StreamingTimeout.VALUE_MILLIS, { "value-1 did not arrive; requests: ${proxy.records}" }) {
      client.stringVariation(FEATURE_ID_STRING, "") == "value-1"
    }

    // The timed-out init fetch is cancelled and never retried, and within 1 ms it may not even
    // reach the proxy, so it counts 0 or 1. A second call would mean the polling fallback
    // filled the cache instead of the stream.
    assertWithMessage("requests: ${proxy.records}").that(proxy.count(GET_EVALUATIONS_PATH)).isAtMost(1)

    // The count above also passes when the stream gave up and the fallback fetched once.
    // Require a clean stream reply, and no REST fetch after the first stream request.
    assertWithMessage("the stream never opened cleanly; requests: ${proxy.records}")
      .that(streamOpenCount())
      .isAtLeast(1)
    val records = proxy.records
    val firstStream = records.indexOfFirst { it.path.endsWith(STREAM_EVALUATIONS_PATH) }
    assertWithMessage("no stream request reached the proxy; requests: $records").that(firstStream).isAtLeast(0)
    val fetchesAfterStream = records.drop(firstStream).filter { it.path.endsWith(GET_EVALUATIONS_PATH) }
    assertWithMessage("a REST fetch came after the stream request; requests: $records").that(fetchesAfterStream).isEmpty()
  }

  @Test
  fun update_user_attributes_reconnects_and_reevaluates_over_the_stream() {
    // feature-android-e2e-string has a targeting rule on the E2E backend: app_version "0.0.1"
    // resolves to "value-2" with reason RULE instead of the default "value-1".
    // BKTClientTest.evaluation_update_flow checks the same rule through a REST fetch; this test
    // goes through the stream reconnect that updateUserAttributes starts.
    assertThat(initializeClient(context, config())).isNull()
    val client = BKTClient.getInstance()
    assertEventually(StreamingTimeout.STREAM_OPEN_MILLIS, { "the stream did not open; requests: ${proxy.records}" }) {
      streamOpenCount() >= 1
    }
    assertThat(client.stringVariation(FEATURE_ID_STRING, "")).isEqualTo("value-1")

    val listener = RecordingEvaluationListener { client.stringVariation(FEATURE_ID_STRING, "") }
    client.addEvaluationUpdateListener(listener)

    client.updateUserAttributes(mapOf("app_version" to "0.0.1"))

    assertEventually(StreamingTimeout.VALUE_MILLIS, { "value-2 did not arrive; requests: ${proxy.records}" }) {
      client.stringVariation(FEATURE_ID_STRING, "") == "value-2"
    }
    // The listener must see value-2 itself. A call for any other update does not count. The
    // listener runs on the main thread a moment after the value is stored, so wait for it.
    assertEventually(StreamingTimeout.VALUE_MILLIS, { "the listener never saw value-2; observed: ${listener.observed}" }) {
      listener.observed.contains("value-2")
    }
    assertThat(client.stringVariationDetails(FEATURE_ID_STRING, "").reason)
      .isEqualTo(BKTEvaluationDetails.Reason.RULE)

    // The init fetch is the only get_evaluations call. A second one would mean the new
    // attributes were evaluated by a REST fetch (for example the polling fallback after a
    // failed reconnect) instead of the stream reconnect.
    assertWithMessage("requests: ${proxy.records}").that(proxy.count(GET_EVALUATIONS_PATH)).isEqualTo(1)
    assertWithMessage("expected a new stream request after the attribute update; requests: ${proxy.records}")
      .that(proxy.count(STREAM_EVALUATIONS_PATH))
      .isAtLeast(2)
  }

  @Test
  fun server_pushed_patch_reaches_the_open_stream() {
    // Fails here, naming the setting, when the write key is missing.
    FeatureFlagApi.publicApiKey()

    assertThat(initializeClient(context, config())).isNull()
    val client = BKTClient.getInstance()

    // Both are sent back with the new value: the server replaces the whole variation.
    val details = client.stringVariationDetails(FEATURE_ID_STREAMING, "")
    assertWithMessage("$FEATURE_ID_STREAMING has no evaluation for tag $FEATURE_TAG; create it in the console")
      .that(details.variationId)
      .isNotEmpty()

    val value = FeatureFlagApi.newStreamingTestValue()
    val listener = RecordingEvaluationListener { client.stringVariation(FEATURE_ID_STREAMING, "") }
    client.addEvaluationUpdateListener(listener)

    // Without this, a fast PATCH can land before the stream is open, and the push goes to
    // nobody.
    assertEventually(StreamingTimeout.STREAM_OPEN_MILLIS, { "the stream did not open; requests: ${proxy.records}" }) {
      streamOpenCount() >= 1
    }

    FeatureFlagApi.setVariationValue(FEATURE_ID_STREAMING, details.variationId, details.variationName, value)

    // Every observed value is checked, not only the latest: an overlapping run can overwrite
    // the flag right after our write.
    assertEventually(StreamingTimeout.VALUE_MILLIS, { "$value never reached the app; observed: ${listener.observed}" }) {
      listener.observed.contains(value)
    }

    // The init fetch is the only get_evaluations call. A second one would mean the value came
    // from a REST fetch instead of the stream.
    assertWithMessage("requests: ${proxy.records}").that(proxy.count(GET_EVALUATIONS_PATH)).isEqualTo(1)
  }

  private fun config() = streamingConfig(apiEndpoint = proxy.url)

  /**
   * Stream requests the backend answered with 200 that are still open: not failed and not
   * ended. A stream that replied 200 and then broke, or ended normally, does not count.
   */
  private fun streamOpenCount(): Int =
    proxy.records.count {
      it.path.endsWith(STREAM_EVALUATIONS_PATH) && it.status == 200 && it.error == null && !it.ended
    }
}
