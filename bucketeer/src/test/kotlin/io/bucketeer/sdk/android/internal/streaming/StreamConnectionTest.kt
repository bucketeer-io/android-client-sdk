package io.bucketeer.sdk.android.internal.streaming

import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.internal.LoggerHolder
import io.bucketeer.sdk.android.internal.streaming.fakes.StreamConnectionHarness
import io.bucketeer.sdk.android.internal.streaming.fakes.StreamConnectionHarness.Companion.UNHANDLED
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class StreamConnectionTest {
  @Before
  fun setUp() {
    // StreamConnection logs a failing request builder; no Android logger may be left over.
    LoggerHolder.clearLoggers()
  }

  @After
  fun tearDown() {
    LoggerHolder.clearLoggers()
  }

  @Test
  fun `start opens one event source with the built request`() {
    val h = StreamConnectionHarness()

    h.connection.start()

    assertThat(h.sources).hasSize(1)
    assertThat(h.requestBuilderCalls).isEqualTo(1)
    assertThat(h.latest.openedRequests.map { it.url.toString() })
      .containsExactly("https://example.com/stream?attempt=1")
    assertThat(h.latest.closed.get()).isFalse()
  }

  @Test
  fun `request builder runs again on every connect`() {
    val h = StreamConnectionHarness()
    h.connection.start()

    h.error(null)
    h.scheduler.advanceBy(1_000)

    assertThat(h.sources).hasSize(2)
    assertThat(h.requestBuilderCalls).isEqualTo(2)
    assertThat(h.sources[1].openedRequests.map { it.url.toString() })
      .containsExactly("https://example.com/stream?attempt=2")
  }

  @Test
  fun `open then events reach their handlers`() {
    val h = StreamConnectionHarness()
    h.connection.start()

    h.open()
    h.event("put", "p1")
    h.event("patch", "p2")
    h.message("m1")

    assertThat(h.openCount).isEqualTo(1)
    assertThat(h.delivered)
      .containsExactly("put" to "p1", "patch" to "p2", UNHANDLED to "m1")
      .inOrder()
    assertThat(h.errors).isEmpty()
  }

  @Test
  fun `message key in the events map is ignored`() {
    val h = StreamConnectionHarness(eventNames = listOf("put", "message"))
    h.connection.start()

    assertThat(h.latest.listeners.keys).containsExactly("put")

    h.open()
    h.message("m1")
    h.event("put", "p1")

    assertThat(h.delivered).containsExactly(UNHANDLED to "m1", "put" to "p1").inOrder()
  }

  @Test
  fun `terminal status gives up without retry`() {
    for (status in listOf(401, 403)) {
      val h = StreamConnectionHarness()
      h.connection.start()
      // Positive control: the silence timer is pending while connecting.
      assertThat(h.scheduler.pendingCount).isEqualTo(1)

      h.error(status)

      assertThat(h.errors).containsExactly(StreamConnectionErrorInfo(terminal = true))
      assertThat(h.latest.closed.get()).isTrue()
      assertThat(h.scheduler.pendingCount).isEqualTo(0)
      h.scheduler.advanceBy(600_000)
      assertThat(h.sources).hasSize(1)
    }
  }

  @Test
  fun `non-recoverable status gives up immediately without retry`() {
    for (status in listOf(400, 413, 422)) {
      val h = StreamConnectionHarness()
      h.connection.start()
      assertThat(h.scheduler.pendingCount).isEqualTo(1)

      h.error(status)

      assertThat(h.errors).containsExactly(StreamConnectionErrorInfo(terminal = false))
      assertThat(h.latest.closed.get()).isTrue()
      assertThat(h.scheduler.pendingCount).isEqualTo(0)
      h.scheduler.advanceBy(600_000)
      assertThat(h.sources).hasSize(1)
    }
  }

  @Test
  fun `recoverable error before open retries with backoff`() {
    for (status in listOf(null, 408, 429, 499, 500, 503)) {
      val h = StreamConnectionHarness()
      h.connection.start()

      h.error(status)

      assertThat(h.errors).isEmpty()
      assertThat(h.sources[0].closed.get()).isTrue()
      h.scheduler.advanceBy(999)
      assertThat(h.sources).hasSize(1)
      h.scheduler.advanceBy(1)
      assertThat(h.sources).hasSize(2)
      assertThat(h.latest.closed.get()).isFalse()
    }
  }

  @Test
  fun `transient error after open heals without calling onError`() {
    val h = StreamConnectionHarness()
    h.connection.start()
    h.open()

    h.error(503)
    h.scheduler.advanceBy(1_000)
    h.open()
    h.event("put", "p1")

    assertThat(h.sources).hasSize(2)
    assertThat(h.openCount).isEqualTo(2)
    assertThat(h.errors).isEmpty()
    assertThat(h.delivered).containsExactly("put" to "p1")
  }

  @Test
  fun `reconnect while a retry is pending opens exactly one connection`() {
    val h = StreamConnectionHarness()
    h.connection.start()
    h.error(null)
    assertThat(h.scheduler.pendingCount).isEqualTo(1)

    h.connection.reconnect()
    assertThat(h.sources).hasSize(2)

    h.scheduler.advanceBy(1_000)

    assertThat(h.sources).hasSize(2)
    assertThat(h.sources[0].closed.get()).isTrue()
    assertThat(h.sources[1].closed.get()).isFalse()
  }

  @Test
  fun `reconnect resets the backoff`() {
    val h = StreamConnectionHarness()
    h.connection.start()
    h.error(null) // next retry after 1000
    h.scheduler.advanceBy(1_000)
    h.error(null) // next retry after 2000
    h.scheduler.advanceBy(2_000)
    assertThat(h.sources).hasSize(3)

    // Without a reset, the next retry would wait 4000.
    h.connection.reconnect()
    assertThat(h.sources).hasSize(4)
    h.error(null)

    h.scheduler.advanceBy(999)
    assertThat(h.sources).hasSize(4)
    h.scheduler.advanceBy(1)
    assertThat(h.sources).hasSize(5)
  }

  @Test
  fun `reconnect is ignored before start and after stop`() {
    val h = StreamConnectionHarness()

    h.connection.reconnect()
    assertThat(h.sources).isEmpty()

    // Positive control: the same connection does open once started.
    h.connection.start()
    assertThat(h.sources).hasSize(1)

    h.connection.stop()
    h.connection.reconnect()
    h.scheduler.advanceBy(600_000)

    assertThat(h.sources).hasSize(1)
    assertThat(h.requestBuilderCalls).isEqualTo(1)
  }

  @Test
  fun `callbacks from a replaced event source are ignored`() {
    val h = StreamConnectionHarness()
    h.connection.start()
    val old = h.latest
    // The real source can post a block just before close() that runs after it.
    old.deliverEvenIfClosed = true

    h.connection.reconnect()
    assertThat(old.closed.get()).isTrue()
    val current = h.latest

    old.simulateOpen()
    old.simulateEvent("put", "stale")
    old.simulateMessage("stale")
    old.simulateError(401)
    h.scheduler.runCurrent()

    assertThat(h.openCount).isEqualTo(0)
    assertThat(h.delivered).isEmpty()
    assertThat(h.errors).isEmpty()
    assertThat(current.closed.get()).isFalse()
    assertThat(h.sources).hasSize(2)

    // Positive control: the current source's callbacks do get through.
    h.open()
    h.event("put", "fresh")
    assertThat(h.openCount).isEqualTo(1)
    assertThat(h.delivered).containsExactly("put" to "fresh")
  }

  @Test
  fun `stop cancels timers closes the event source and ignores late errors`() {
    // Open: silence timer and backoff reset pending.
    val open = StreamConnectionHarness()
    open.connection.start()
    open.open()
    assertThat(open.scheduler.pendingCount).isEqualTo(2)

    open.connection.stop()

    assertThat(open.scheduler.pendingCount).isEqualTo(0)
    assertThat(open.latest.closed.get()).isTrue()
    open.latest.deliverEvenIfClosed = true
    open.error(401)
    open.error(null)
    assertThat(open.errors).isEmpty()
    assertThat(open.scheduler.pendingCount).isEqualTo(0)

    // Retry pending.
    val retrying = StreamConnectionHarness()
    retrying.connection.start()
    retrying.error(null)
    assertThat(retrying.scheduler.pendingCount).isEqualTo(1)

    retrying.connection.stop()

    assertThat(retrying.scheduler.pendingCount).isEqualTo(0)
    retrying.scheduler.advanceBy(600_000)
    assertThat(retrying.sources).hasSize(1)
    assertThat(retrying.errors).isEmpty()
  }

  @Test
  fun `throwing request builder reports a non-terminal error without crashing`() {
    val h = StreamConnectionHarness()
    h.requestBuilderThrows = IllegalStateException("no user")

    h.connection.start()

    assertThat(h.requestBuilderCalls).isEqualTo(1)
    assertThat(h.errors).containsExactly(StreamConnectionErrorInfo(terminal = false))
    assertThat(h.sources).isEmpty()
    assertThat(h.scheduler.pendingCount).isEqualTo(0)
  }

  @Test
  fun `request builder Error is not swallowed`() {
    val h = StreamConnectionHarness()
    h.requestBuilderThrows = TestError()

    assertThrows(TestError::class.java) { h.connection.start() }

    // The builder really ran, so the Error came from it.
    assertThat(h.requestBuilderCalls).isEqualTo(1)
    assertThat(h.errors).isEmpty()
    assertThat(h.sources).isEmpty()
  }

  private class TestError : Error("broken")
}
