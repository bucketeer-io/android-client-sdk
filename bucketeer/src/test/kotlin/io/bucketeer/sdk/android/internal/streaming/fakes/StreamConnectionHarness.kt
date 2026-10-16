package io.bucketeer.sdk.android.internal.streaming.fakes

import com.google.common.truth.Truth.assertWithMessage
import io.bucketeer.sdk.android.internal.streaming.StreamBackoff
import io.bucketeer.sdk.android.internal.streaming.StreamConnection
import io.bucketeer.sdk.android.internal.streaming.StreamConnectionCallbacks
import io.bucketeer.sdk.android.internal.streaming.StreamConnectionErrorInfo
import okhttp3.Request

/**
 * Builds a [StreamConnection] with the fakes and records what happens. Jitter is pinned to 0, so
 * retry delays are exactly 1000, 2000, 4000 ms and so on, up to 30000.
 */
internal class StreamConnectionHarness(
  eventNames: List<String> = listOf("put", "patch", "error"),
  withUnhandledMessage: Boolean = true,
) {
  val scheduler = FakeStreamScheduler()

  /** Every event source the connection created, oldest first. */
  val sources = mutableListOf<FakeEventSource>()
  val latest: FakeEventSource
    get() {
      assertWithMessage("no event source was created").that(sources).isNotEmpty()
      return sources.last()
    }

  /** Calls to the request builder, including ones that threw. */
  var requestBuilderCalls = 0
    private set

  /** When set, the request builder throws it. */
  var requestBuilderThrows: Throwable? = null

  var openCount = 0
    private set
  val errors = mutableListOf<StreamConnectionErrorInfo>()

  /** (event name, data) in delivery order. Unnamed events are recorded as [UNHANDLED]. */
  val delivered = mutableListOf<Pair<String, String>>()

  val connection =
    StreamConnection(
      makeEventSource = { FakeEventSource(scheduler).also { sources.add(it) } },
      requestBuilder = {
        requestBuilderCalls++
        requestBuilderThrows?.let { throw it }
        Request.Builder().url("https://example.com/stream?attempt=$requestBuilderCalls").build()
      },
      events = eventNames.associateWith { name -> { data: String -> delivered.add(name to data) } },
      onUnhandledMessage = if (withUnhandledMessage) ({ data: String -> delivered.add(UNHANDLED to data) }) else null,
      callbacks =
        StreamConnectionCallbacks(
          onOpen = { openCount++ },
          onError = { errors.add(it) },
        ),
      scheduler = scheduler,
      backoff = StreamBackoff(random = { 0.0 }),
    )

  // Shortcuts for tests that don't care about queueing: post on the latest source, then run.

  fun open() {
    latest.simulateOpen()
    scheduler.runCurrent()
  }

  fun bytes() {
    latest.simulateBytes()
    scheduler.runCurrent()
  }

  fun event(
    name: String,
    data: String,
    withBytes: Boolean = true,
  ) {
    latest.simulateEvent(name, data, withBytes)
    scheduler.runCurrent()
  }

  fun message(
    data: String,
    withBytes: Boolean = true,
  ) {
    latest.simulateMessage(data, withBytes)
    scheduler.runCurrent()
  }

  fun error(status: Int?) {
    latest.simulateError(status)
    scheduler.runCurrent()
  }

  /**
   * Advances 1 ms at a time until [timeMillis], failing every new event source with a network
   * error as soon as it is created. Simulates a stream endpoint that stays down.
   */
  fun failNewSourcesUntil(timeMillis: Long) {
    while (scheduler.nowMillis < timeMillis) {
      val before = sources.size
      scheduler.advanceBy(1)
      if (sources.size > before) error(null)
    }
  }

  companion object {
    const val UNHANDLED = "<unhandled>"
  }
}
