package io.bucketeer.sdk.android.internal.streaming

import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.internal.LoggerHolder
import io.bucketeer.sdk.android.internal.streaming.fakes.StreamConnectionHarness
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Silence timer (70 s), unhealthy window (gives up after more than 120 s), backoff reset (60 s
 * open). Jitter is pinned to 0, so retries wait 1000, 2000, 4000, 8000, 16000, then 30000 ms.
 */
class StreamConnectionTimingTest {
  @Before
  fun setUp() {
    LoggerHolder.clearLoggers()
  }

  @After
  fun tearDown() {
    LoggerHolder.clearLoggers()
  }

  @Test
  fun `watchdog reconnects after 70 seconds of silence`() {
    val h = StreamConnectionHarness()
    h.connection.start()
    h.open()

    h.scheduler.advanceBy(69_999)
    assertThat(h.sources).hasSize(1)
    assertThat(h.latest.closed.get()).isFalse()

    h.scheduler.advanceBy(1)
    assertThat(h.sources[0].closed.get()).isTrue()
    assertThat(h.errors).isEmpty()

    h.scheduler.advanceBy(1_000)
    assertThat(h.sources).hasSize(2)
  }

  @Test
  fun `received bytes reset the watchdog`() {
    val h = StreamConnectionHarness()
    h.connection.start()
    h.open()

    h.scheduler.advanceBy(60_000)
    h.bytes()

    h.scheduler.advanceBy(10_000) // 70_000
    assertThat(h.latest.closed.get()).isFalse()
    h.scheduler.advanceBy(59_999) // 129_999
    assertThat(h.latest.closed.get()).isFalse()
    h.scheduler.advanceBy(1) // 130_000
    assertThat(h.latest.closed.get()).isTrue()
  }

  @Test
  fun `named event resets the watchdog`() {
    val h = StreamConnectionHarness()
    h.connection.start()
    h.open()

    h.scheduler.advanceBy(60_000)
    h.event("put", "p1", withBytes = false)

    h.scheduler.advanceBy(10_000) // 70_000
    assertThat(h.latest.closed.get()).isFalse()
    h.scheduler.advanceBy(60_000) // 130_000
    assertThat(h.latest.closed.get()).isTrue()
    assertThat(h.delivered).containsExactly("put" to "p1")
  }

  @Test
  fun `unnamed message resets the watchdog even without onUnhandledMessage`() {
    val h = StreamConnectionHarness(withUnhandledMessage = false)
    h.connection.start()
    h.open()

    h.scheduler.advanceBy(60_000)
    h.message("m1", withBytes = false)

    h.scheduler.advanceBy(10_000) // 70_000
    assertThat(h.latest.closed.get()).isFalse()
    h.scheduler.advanceBy(60_000) // 130_000
    assertThat(h.latest.closed.get()).isTrue()
    assertThat(h.delivered).isEmpty()
  }

  @Test
  fun `heartbeat bytes alone keep the connection alive`() {
    val h = StreamConnectionHarness()
    h.connection.start()
    h.open()

    repeat(24) {
      h.scheduler.advanceBy(25_000)
      h.bytes()
    }

    assertThat(h.scheduler.nowMillis).isEqualTo(600_000)
    assertThat(h.sources).hasSize(1)
    assertThat(h.latest.closed.get()).isFalse()
    assertThat(h.errors).isEmpty()

    // Positive control: once the heartbeats stop, the watchdog does fire.
    h.scheduler.advanceBy(70_000)
    assertThat(h.latest.closed.get()).isTrue()
  }

  @Test
  fun `single drop after ten healthy minutes reconnects`() {
    val h = StreamConnectionHarness()
    h.connection.start()
    h.open()
    repeat(24) {
      h.scheduler.advanceBy(25_000)
      h.bytes()
    }

    h.error(null)

    assertThat(h.errors).isEmpty()
    h.scheduler.advanceBy(999)
    assertThat(h.sources).hasSize(1)
    h.scheduler.advanceBy(1)
    assertThat(h.sources).hasSize(2)
    h.open()
    assertThat(h.openCount).isEqualTo(2)
    assertThat(h.errors).isEmpty()
  }

  @Test
  fun `unhealthy for over 120 seconds gives up once`() {
    val h = StreamConnectionHarness()
    h.connection.start()
    h.open()
    h.error(null) // unhealthy from 0

    // Retries at 1000, 3000, 7000, 15000, 31000, 61000, 91000 all fail; the next is at 121000.
    h.failNewSourcesUntil(120_999)
    assertThat(h.errors).isEmpty()
    assertThat(h.sources).hasSize(8)

    h.failNewSourcesUntil(121_000)

    assertThat(h.errors).containsExactly(StreamConnectionErrorInfo(terminal = false))
    assertThat(h.scheduler.pendingCount).isEqualTo(0)
    h.scheduler.advanceBy(600_000)
    assertThat(h.sources).hasSize(9)
    assertThat(h.errors).hasSize(1)
  }

  @Test
  fun `repeated pre-open errors give up after 120 seconds`() {
    val h = StreamConnectionHarness()
    h.connection.start()
    h.error(null) // never opened; unhealthy from 0

    h.failNewSourcesUntil(120_999)
    assertThat(h.errors).isEmpty()

    h.failNewSourcesUntil(121_000)

    assertThat(h.errors).containsExactly(StreamConnectionErrorInfo(terminal = false))
    assertThat(h.openCount).isEqualTo(0)
    assertThat(h.scheduler.pendingCount).isEqualTo(0)
  }

  @Test
  fun `data between drops restarts the unhealthy window`() {
    val h = StreamConnectionHarness()
    h.connection.start()
    h.error(null) // unhealthy from 0

    // Retries at 1000 to 61000 fail. The one at 91000 opens and delivers data at 100_000.
    h.failNewSourcesUntil(90_999)
    h.scheduler.advanceBy(1) // 91_000: new source
    assertThat(h.sources).hasSize(8)
    h.open()
    h.scheduler.advanceBy(9_000)
    h.event("put", "p1") // healthy again at 100_000
    h.error(null) // unhealthy from 100_000; next retry at 130_000

    h.failNewSourcesUntil(130_000)
    assertThat(h.errors).isEmpty()

    // Positive control: the new window still ends. Retries at 160000, 190000, 220000 (exactly
    // 120 s: not over) fail; the one at 250000 gives up.
    h.failNewSourcesUntil(249_999)
    assertThat(h.errors).isEmpty()
    h.failNewSourcesUntil(250_000)
    assertThat(h.errors).containsExactly(StreamConnectionErrorInfo(terminal = false))
  }

  @Test
  fun `connect that never opens gives up after the window`() {
    val h = StreamConnectionHarness()
    h.connection.start() // the source never calls back

    // Watchdog at 70000 starts the window; retry at 71000 hangs; watchdog at 141000 retries
    // after 2000; the source at 143000 hangs; its watchdog at 213000 is past 120 s.
    h.scheduler.advanceBy(70_000)
    assertThat(h.sources).hasSize(1)
    assertThat(h.sources[0].closed.get()).isTrue()
    h.scheduler.advanceBy(142_999) // 212_999
    assertThat(h.errors).isEmpty()
    assertThat(h.sources).hasSize(3)

    h.scheduler.advanceBy(1)

    assertThat(h.errors).containsExactly(StreamConnectionErrorInfo(terminal = false))
    assertThat(h.latest.closed.get()).isTrue()
    assertThat(h.scheduler.pendingCount).isEqualTo(0)
  }

  @Test
  fun `external reconnect does not reset the unhealthy window`() {
    val h = StreamConnectionHarness()
    h.connection.start()
    h.error(null) // unhealthy from 0; retry at 1000

    h.scheduler.advanceBy(500)
    h.connection.reconnect()
    h.error(null) // backoff reset: retry at 1500

    // Retries at 1500, 3500, 7500, 15500, 31500, 61500, 91500 fail; the next would be at 121500.
    h.failNewSourcesUntil(119_000)
    h.connection.reconnect()
    h.error(null) // backoff reset: retry at 120_000

    h.failNewSourcesUntil(120_000)
    // Exactly 120 s unhealthy: not over the limit yet.
    assertThat(h.errors).isEmpty()

    h.failNewSourcesUntil(122_000)
    assertThat(h.errors).containsExactly(StreamConnectionErrorInfo(terminal = false))
  }

  @Test
  fun `recovered reconnect clears the unhealthy window`() {
    val h = StreamConnectionHarness()
    h.connection.start()
    h.error(null) // unhealthy from 0
    h.failNewSourcesUntil(100_000)

    h.connection.reconnect()
    h.open()
    h.event("put", "p1") // healthy at 100_000
    repeat(10) {
      h.scheduler.advanceBy(25_000)
      h.bytes()
    }
    h.scheduler.advanceBy(10_000)
    assertThat(h.scheduler.nowMillis).isEqualTo(360_000)

    h.error(null) // a new drop at 360 s

    assertThat(h.errors).isEmpty()
    h.scheduler.advanceBy(1_000)
    h.open()
    assertThat(h.errors).isEmpty()
    assertThat(h.latest.closed.get()).isFalse()
  }

  @Test
  fun `stable connection resets the backoff before the next drop`() {
    val h = StreamConnectionHarness()
    h.connection.start()
    h.error(null) // retry after 1000
    h.scheduler.advanceBy(1_000)
    h.error(null) // retry after 2000
    h.scheduler.advanceBy(2_000) // 3000
    h.open()
    h.scheduler.advanceBy(60_000)
    h.bytes()

    h.error(null)

    // Without the reset the next retry would wait 4000.
    h.scheduler.advanceBy(999)
    assertThat(h.sources).hasSize(3)
    h.scheduler.advanceBy(1)
    assertThat(h.sources).hasSize(4)
  }

  @Test
  fun `drop before 60 seconds keeps escalating`() {
    val h = StreamConnectionHarness()
    h.connection.start()

    for ((index, delay) in listOf(1_000L, 2_000L, 4_000L).withIndex()) {
      h.open()
      h.scheduler.advanceBy(500)
      h.error(null)
      h.scheduler.advanceBy(delay - 1)
      assertThat(h.sources).hasSize(index + 1)
      h.scheduler.advanceBy(1)
      assertThat(h.sources).hasSize(index + 2)
    }
  }

  @Test
  fun `drop just before 60 seconds does not reset the backoff later`() {
    val h = StreamConnectionHarness()
    h.connection.start()
    h.error(null) // retry after 1000
    h.scheduler.advanceBy(1_000)
    h.open() // backoff reset armed for 61_000

    h.scheduler.advanceBy(59_999) // 60_999
    h.bytes()
    h.error(null) // retry after 2000, at 62_999

    h.scheduler.advanceBy(1) // 61_000: the cancelled reset must not run
    h.scheduler.advanceBy(1_999) // 62_999
    assertThat(h.sources).hasSize(3)
    h.error(null)

    // Still escalating: 4000, not 1000.
    h.scheduler.advanceBy(3_999)
    assertThat(h.sources).hasSize(3)
    h.scheduler.advanceBy(1)
    assertThat(h.sources).hasSize(4)
  }
}
