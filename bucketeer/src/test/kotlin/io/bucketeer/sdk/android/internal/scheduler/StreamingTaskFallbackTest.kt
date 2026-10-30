package io.bucketeer.sdk.android.internal.scheduler

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.internal.LoggerHolder
import io.bucketeer.sdk.android.internal.streaming.fakes.RecordingLogger
import io.bucketeer.sdk.android.internal.streaming.fakes.StreamingTaskHarness
import org.junit.After
import org.junit.Before
import org.junit.Test

class StreamingTaskFallbackTest {
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

  private fun StreamingTaskHarness.openSourceCount(): Int = sources.count { !it.closed.get() }

  /** Moves the clock 300_000 ms in 60_000 ms steps, sending bytes on the latest source after each. */
  private fun StreamingTaskHarness.keepLatestHealthyFor300Seconds() {
    repeat(5) {
      scheduler.advanceBy(60_000)
      latest.simulateBytes()
      scheduler.runCurrent()
    }
  }

  @Test
  fun `non-terminal error starts the fallback immediately and arms recovery`() {
    h.startStreaming()

    h.giveUpNonTerminal()

    assertThat(h.fallbacks).hasSize(1)
    assertThat(h.latestFallback.startCalls).containsExactly(true)
    h.scheduler.advanceBy(299_999)
    assertThat(h.sources).hasSize(1)
    h.scheduler.advanceBy(1)
    assertThat(h.sources).hasSize(2)
  }

  @Test
  fun `body-dependent 4xx starts the fallback and arms recovery like any non-terminal error`() {
    for (status in listOf(400, 413, 422)) {
      val harness = StreamingTaskHarness()
      harness.startStreaming()

      harness.error(status)

      assertThat(harness.fallbacks).hasSize(1)
      assertThat(harness.latestFallback.startCalls).containsExactly(true)
      harness.scheduler.advanceBy(300_000)
      assertThat(harness.sources).hasSize(2)
    }
  }

  @Test
  fun `terminal error starts the fallback but never schedules recovery`() {
    h.startStreaming()

    h.giveUpTerminal()

    assertThat(h.fallbacks).hasSize(1)
    assertThat(h.latestFallback.startCalls).containsExactly(true)
    assertThat(h.scheduler.pendingCount).isEqualTo(0)
    h.scheduler.advanceBy(600_000)
    assertThat(h.sources).hasSize(1)
  }

  @Test
  fun `unhealthy past 120 seconds with no status starts the fallback`() {
    h.startStreaming()
    h.error(null)

    while (h.fallbacks.isEmpty() && h.scheduler.nowMillis < 200_000) {
      val before = h.sources.size
      h.scheduler.advanceBy(1)
      if (h.sources.size > before) h.error(null)
    }

    assertThat(h.fallbacks).hasSize(1)
    assertThat(h.latestFallback.startCalls).containsExactly(true)
    assertThat(h.scheduler.nowMillis).isGreaterThan(120_000L)
  }

  @Test
  fun `terminal give-up warns once`() {
    h.startStreaming()

    h.giveUpTerminal()
    h.scheduler.advanceBy(600_000)

    assertThat(logger.messages(Log.WARN)).hasSize(1)
  }

  @Test
  fun `non-terminal give-up does not warn`() {
    h.startStreaming()

    h.giveUpNonTerminal()

    assertThat(logger.messages(Log.WARN)).isEmpty()

    // Control: a terminal give-up does warn.
    val terminal = StreamingTaskHarness()
    terminal.startStreaming()
    terminal.giveUpTerminal()
    assertThat(logger.messages(Log.WARN)).hasSize(1)
  }

  @Test
  fun `onOpen after recovery stops the fallback and leaves no recovery pending`() {
    // Pending timers of a plain open stream (watchdog and backoff reset): the baseline.
    val control = StreamingTaskHarness()
    control.startOpenStream()
    val openStreamTimers = control.scheduler.pendingCount

    h.startStreaming()
    h.giveUpNonTerminal()
    h.scheduler.advanceBy(300_000)
    assertThat(h.sources).hasSize(2)

    h.open()

    assertThat(h.latestFallback.stopCount).isEqualTo(1)
    assertThat(h.latestFallback.isRunning).isFalse()
    assertThat(h.scheduler.pendingCount).isEqualTo(openStreamTimers)
  }

  @Test
  fun `recovery reopening keeps the fallback until onOpen`() {
    h.startStreaming()
    h.giveUpNonTerminal()

    h.scheduler.advanceBy(300_000)

    assertThat(h.sources).hasSize(2)
    assertThat(h.latestFallback.isRunning).isTrue()
    assertThat(h.latestFallback.stopCount).isEqualTo(0)
  }

  @Test
  fun `reconnect from the fallback keeps the fallback until onOpen`() {
    h.startStreaming()
    h.giveUpNonTerminal()

    h.reconnect()

    assertThat(h.sources).hasSize(2)
    assertThat(h.latestFallback.isRunning).isTrue()

    h.open()

    assertThat(h.latestFallback.isRunning).isFalse()
    assertThat(h.latestFallback.stopCount).isEqualTo(1)
  }

  @Test
  fun `reconnect-opened stream plus an armed recovery gives one live connection`() {
    h.startStreaming()
    h.giveUpNonTerminal()
    h.reconnect()
    assertThat(h.dependencies.backoffs).hasSize(2)

    // Not opened, so the fallback (and a recovery it failed to cancel) stays; bytes keep it alive.
    h.keepLatestHealthyFor300Seconds()
    h.scheduler.advanceBy(1)

    assertThat(h.dependencies.backoffs).hasSize(2)
    assertThat(h.openSourceCount()).isEqualTo(1)
  }

  @Test
  fun `terminal error on a reconnect-opened stream leaves no recovery`() {
    h.startStreaming()
    h.giveUpNonTerminal()
    h.reconnect()
    assertThat(h.sources).hasSize(2)

    h.giveUpTerminal()
    h.scheduler.advanceBy(600_000)

    assertThat(h.sources).hasSize(2)
  }

  @Test
  fun `stop stops the connection the fallback and recovery`() {
    // An open connection.
    h.startOpenStream()
    h.task.stop()
    h.scheduler.runCurrent()
    assertThat(h.latest.closed.get()).isTrue()
    assertThat(h.scheduler.pendingCount).isEqualTo(0)

    // The fallback with a recovery armed.
    val onFallback = StreamingTaskHarness()
    onFallback.startStreaming()
    onFallback.giveUpNonTerminal()
    assertThat(onFallback.scheduler.pendingCount).isEqualTo(1)
    onFallback.task.stop()
    onFallback.scheduler.runCurrent()
    assertThat(onFallback.latestFallback.stopCount).isEqualTo(1)
    assertThat(onFallback.scheduler.pendingCount).isEqualTo(0)
  }

  @Test
  fun `stop after a give-up does not close the given-up source again`() {
    h.startOpenStream()
    h.giveUpNonTerminal()
    assertThat(h.latest.closeCount.get()).isEqualTo(1)

    h.task.stop()
    h.scheduler.runCurrent()

    assertThat(h.latest.closeCount.get()).isEqualTo(1)
  }

  @Test
  fun `connection error after stop starts no fallback`() {
    // Error queued before stop().
    h.startOpenStream()
    h.latest.simulateError(400)
    h.task.stop()
    h.scheduler.runCurrent()
    assertThat(h.fallbacks).isEmpty()

    // Error reported after stop() and its cleanup.
    val after = StreamingTaskHarness()
    after.startOpenStream()
    after.task.stop()
    after.scheduler.runCurrent()
    after.latest.deliverEvenIfClosed = true
    after.error(400)
    assertThat(after.fallbacks).isEmpty()
  }

  @Test
  fun `request builder throw inside start clears the connection`() {
    // Positive control: without the throw, the first connect makes a source.
    val control = StreamingTaskHarness()
    control.startStreaming()
    assertThat(control.sources).hasSize(1)

    h.interactor.attributesStateThrowsOnce = IllegalStateException("request build failed")

    h.startStreaming()

    assertThat(h.sources).isEmpty()
    assertThat(h.dependencies.backoffs).hasSize(1)
    assertThat(h.fallbacks).hasSize(1)
    assertThat(h.latestFallback.startCalls).containsExactly(true)

    h.task.onUserAttributesUpdated()
    h.scheduler.advanceBy(200)

    // A new StreamConnection, not the given-up one reused.
    assertThat(h.dependencies.backoffs).hasSize(2)
    assertThat(h.sources).hasSize(1)

    // The recovery armed by the give-up was cancelled.
    h.keepLatestHealthyFor300Seconds()
    assertThat(h.sources).hasSize(1)
    assertThat(h.dependencies.backoffs).hasSize(2)
  }
}
