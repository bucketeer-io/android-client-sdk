package io.bucketeer.sdk.android.internal.scheduler

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.BuildConfig
import io.bucketeer.sdk.android.internal.LoggerHolder
import io.bucketeer.sdk.android.internal.evaluation.CachedEvaluationsState
import io.bucketeer.sdk.android.internal.streaming.fakes.RecordingLogger
import io.bucketeer.sdk.android.internal.streaming.fakes.StreamingTaskHarness
import io.bucketeer.sdk.android.internal.streaming.fakes.StreamingTaskHarness.Companion.body
import io.bucketeer.sdk.android.internal.streaming.fakes.StreamingTaskHarness.Companion.payload
import org.junit.After
import org.junit.Before
import org.junit.Test

class StreamingTaskTest {
  private lateinit var h: StreamingTaskHarness
  private lateinit var logger: RecordingLogger

  @Before
  fun setup() {
    LoggerHolder.clearLoggers()
    logger = RecordingLogger()
    LoggerHolder.addLogger(logger)
    h = StreamingTaskHarness()
  }

  @After
  fun tearDown() {
    LoggerHolder.clearLoggers()
  }

  @Test
  fun `request is a POST to stream_evaluations with the full headers and body`() {
    h.startStreaming()

    val request = h.latest.openedRequests.single()
    assertThat(request.url.toString()).isEqualTo("https://example.com/v1/gateway/stream_evaluations")
    assertThat(request.method).isEqualTo("POST")
    assertThat(request.header("Authorization")).isEqualTo("key")
    assertThat(request.header("Accept")).isEqualTo("text/event-stream")
    assertThat(request.body!!.contentType()!!.type).isEqualTo("application")
    assertThat(request.body!!.contentType()!!.subtype).isEqualTo("json")
    val json = body(request)
    assertThat(json.keys().asSequence().toSet())
      .containsExactly("tag", "user", "sourceId", "sdkVersion", "userEvaluationsId", "evaluatedAt")
    assertThat(json.getString("tag")).isEqualTo("android")
    assertThat(json.getJSONObject("user").getString("id")).isEqualTo("user-1")
    assertThat(json.getJSONObject("user").getJSONObject("data").getString("age")).isEqualTo("28")
    assertThat(json.get("sourceId")).isEqualTo(1)
    assertThat(json.getString("sdkVersion")).isEqualTo(BuildConfig.SDK_VERSION)
  }

  @Test
  fun `request sends the stored userEvaluationsId and evaluatedAt`() {
    h.interactor.cachedState = CachedEvaluationsState(userEvaluationsId = "stored-id", evaluatedAt = "1700000123")

    h.startStreaming()

    val json = body(h.latest.openedRequests.single())
    assertThat(json.getString("userEvaluationsId")).isEqualTo("stored-id")
    assertThat(json.getString("evaluatedAt")).isEqualTo("1700000123")
  }

  @Test
  fun `put event is applied`() {
    h.startOpenStream()

    h.event("put", payload(userEvaluationsId = "put-id"))

    assertThat(h.interactor.applied).hasSize(1)
    assertThat(
      h.interactor.applied[0]
        .first.userEvaluationsId,
    ).isEqualTo("put-id")
  }

  @Test
  fun `patch event is applied`() {
    h.startOpenStream()

    h.event("patch", payload(userEvaluationsId = "patch-id"))

    assertThat(h.interactor.applied).hasSize(1)
    assertThat(
      h.interactor.applied[0]
        .first.userEvaluationsId,
    ).isEqualTo("patch-id")
  }

  @Test
  fun `error event is logged and never applied`() {
    h.startOpenStream()
    h.event("put", payload())
    assertThat(h.interactor.applied).hasSize(1)

    h.event("error", """{"code":"503","message":"server is busy"}""")

    assertThat(h.interactor.applied).hasSize(1)
    val warning = logger.messages(Log.WARN).single()
    assertThat(warning).contains("503")
    assertThat(warning).contains("server is busy")
  }

  @Test
  fun `unnamed message is applied through onUnhandledMessage`() {
    h.startOpenStream()

    h.message(payload(userEvaluationsId = "unnamed-id"))

    assertThat(h.interactor.applied).hasSize(1)
    assertThat(
      h.interactor.applied[0]
        .first.userEvaluationsId,
    ).isEqualTo("unnamed-id")
  }

  @Test
  fun `payload missing forceUpdate is dropped`() {
    h.startOpenStream()
    h.event("put", payload())
    assertThat(h.interactor.applied).hasSize(1)

    h.event(
      "put",
      """{"userEvaluationsId":"x","evaluations":{"id":"","evaluations":[],"createdAt":"1","archivedFeatureIds":[]}}""",
    )

    assertThat(h.interactor.applied).hasSize(1)
  }

  @Test
  fun `invalid JSON is ignored`() {
    h.startOpenStream()
    h.event("put", payload())
    assertThat(h.interactor.applied).hasSize(1)

    h.event("put", "{not json")

    assertThat(h.interactor.applied).hasSize(1)
    assertThat(h.latest.closed.get()).isFalse()
    assertThat(h.sources).hasSize(1)
  }

  @Test
  fun `valid JSON with the wrong shape is ignored`() {
    h.startOpenStream()
    h.event("put", payload())
    assertThat(h.interactor.applied).hasSize(1)

    h.event("put", """{"foo":1}""")

    assertThat(h.interactor.applied).hasSize(1)
    assertThat(h.latest.closed.get()).isFalse()
    assertThat(h.sources).hasSize(1)
  }

  @Test
  fun `payload missing createdAt is dropped`() {
    h.startOpenStream()
    h.event("put", payload())
    assertThat(h.interactor.applied).hasSize(1)

    h.event(
      "put",
      """{"userEvaluationsId":"x","evaluations":{"id":"","evaluations":[],"forceUpdate":false,"archivedFeatureIds":[]}}""",
    )

    assertThat(h.interactor.applied).hasSize(1)
  }

  @Test
  fun `data queued before stop is not applied`() {
    h.startOpenStream()
    h.event("put", payload())
    assertThat(h.interactor.applied).hasSize(1)

    h.latest.simulateEvent("put", payload(userEvaluationsId = "queued"))
    h.task.stop()
    h.scheduler.runCurrent()

    assertThat(h.interactor.applied).hasSize(1)
  }

  @Test
  fun `shouldNotify flips false right after stop`() {
    h.startOpenStream()
    h.event("put", payload())
    val shouldNotify =
      h.interactor.applied
        .single()
        .second
    assertThat(shouldNotify()).isTrue()

    h.task.stop()

    assertThat(shouldNotify()).isFalse()
  }

  @Test
  fun `an exception while applying a payload is logged and the next event still applies`() {
    h.startOpenStream()
    h.interactor.applyThrowsOnce = IllegalStateException("apply failed")

    h.event("put", payload(userEvaluationsId = "first"))
    h.event("put", payload(userEvaluationsId = "second"))

    assertThat(logger.throwables(Log.ERROR).map { it.message }).containsExactly("apply failed")
    assertThat(h.interactor.applied.map { it.first.userEvaluationsId }).containsExactly("second")
  }

  @Test
  fun `an exception inside posted work is logged and the task keeps working`() {
    h.dependencies.makeEventSourceThrowsOnce = IllegalStateException("source failed")

    h.startStreaming()

    assertThat(logger.throwables(Log.ERROR).map { it.message }).containsExactly("source failed")
    assertThat(h.sources).isEmpty()

    h.reconnect()

    assertThat(h.sources).hasSize(1)
  }
}
