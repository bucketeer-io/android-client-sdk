package io.bucketeer.sdk.android.internal.scheduler

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.internal.LoggerHolder
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.evaluation.CachedEvaluationsState
import io.bucketeer.sdk.android.internal.evaluation.storage.UserAttributesState
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.streaming.ExecutorStreamScheduler
import io.bucketeer.sdk.android.internal.streaming.fakes.FakeStreamingEvaluationInteractor
import io.bucketeer.sdk.android.internal.streaming.fakes.FakeStreamingTaskDependencies
import io.bucketeer.sdk.android.internal.streaming.fakes.RecordingLogger
import io.bucketeer.sdk.android.internal.streaming.fakes.StreamingTaskHarness
import io.bucketeer.sdk.android.internal.streaming.fakes.StreamingTaskHarness.Companion.body
import io.bucketeer.sdk.android.internal.streaming.fakes.StreamingTaskHarness.Companion.payload
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class StreamingTaskLifecycleTest {
  private lateinit var h: StreamingTaskHarness
  private lateinit var logger: RecordingLogger
  private var realExecutor: ScheduledExecutorService? = null

  @Before
  fun setup() {
    LoggerHolder.clearLoggers()
    logger = RecordingLogger()
    LoggerHolder.addLogger(logger)
    h = StreamingTaskHarness()
  }

  @After
  fun tearDown() {
    realExecutor?.shutdownNow()
    LoggerHolder.clearLoggers()
  }

  private val initialState = UserAttributesState(userAttributesUpdated = true, updateSequence = 1)

  /** Fails every new source with [status] until the task falls back to polling. */
  private fun StreamingTaskHarness.failUntilFallback(status: Int?) {
    error(status)
    while (fallbacks.isEmpty() && scheduler.nowMillis < 200_000) {
      val before = sources.size
      scheduler.advanceBy(1)
      if (sources.size > before) error(status)
    }
    assertThat(fallbacks).hasSize(1)
  }

  // A StreamingTask on a real single-thread executor, for the cross-thread tests.
  private inner class RealExecutorTask {
    val executor = Executors.newSingleThreadScheduledExecutor().also { realExecutor = it }
    val dependencies = FakeStreamingTaskDependencies(executor, ExecutorStreamScheduler(executor))
    val interactor = FakeStreamingEvaluationInteractor()
    val task =
      StreamingTask(
        config = StreamingTaskHarness.defaultConfig(),
        userProvider = { User(id = "user-1", data = mapOf("age" to "28")) },
        interactor = interactor,
        moshi = DataModule.createMoshi(),
        dependencies = dependencies,
      )

    /** Waits for every block queued on the executor so far. */
    fun drain() {
      executor.submit {}.get(5, TimeUnit.SECONDS)
    }
  }

  @Test
  fun `onOpen clears the flag with the state captured when the request was built`() {
    h.startStreaming()

    h.open()

    assertThat(h.interactor.cleared).containsExactly(initialState)
  }

  @Test
  fun `a flag set after the request was built is cleared only with the older state`() {
    h.startStreaming()
    h.interactor.attributesState = UserAttributesState(userAttributesUpdated = true, updateSequence = 2)

    h.open()

    assertThat(h.interactor.cleared).containsExactly(initialState)
  }

  @Test
  fun `a failed connect never clears the flag`() {
    h.startStreaming()

    h.failUntilFallback(503)

    assertThat(h.interactor.cleared).isEmpty()

    // Control: a later successful open clears it.
    h.reconnect()
    h.open()
    assertThat(h.interactor.cleared).containsExactly(initialState)
  }

  @Test
  fun `attributes state is captured before the user is read`() {
    h.startStreaming()

    val log = h.interactor.accessLog
    assertThat(log).contains("attributesState")
    assertThat(log).contains("user")
    assertThat(log.indexOf("attributesState")).isLessThan(log.indexOf("user"))
  }

  @Test
  fun `reconnect while streaming opens exactly one fresh connection with fresh attributes`() {
    h.startOpenStream()
    h.user = User(id = "user-1", data = mapOf("age" to "29"))

    h.reconnect()

    assertThat(h.sources).hasSize(2)
    assertThat(h.sources[0].closed.get()).isTrue()
    assertThat(h.sources[1].closed.get()).isFalse()
    val user = body(h.latest.openedRequests.single()).getJSONObject("user")
    assertThat(user.getJSONObject("data").getString("age")).isEqualTo("29")
  }

  @Test
  fun `reconnect rebuilds the body with the latest cached state`() {
    h.startOpenStream()
    h.interactor.cachedState = CachedEvaluationsState(userEvaluationsId = "new-id", evaluatedAt = "1700000999")

    h.reconnect()

    val json = body(h.latest.openedRequests.single())
    assertThat(json.getString("userEvaluationsId")).isEqualTo("new-id")
    assertThat(json.getString("evaluatedAt")).isEqualTo("1700000999")
  }

  @Test
  fun `reconnect from the fallback goes straight back to streaming`() {
    h.startStreaming()
    h.giveUpNonTerminal()

    h.reconnect()

    assertThat(h.scheduler.nowMillis).isEqualTo(0L)
    assertThat(h.sources).hasSize(2)
    assertThat(h.latestFallback.isRunning).isTrue()
    h.open()
    assertThat(h.latestFallback.isRunning).isFalse()
  }

  @Test
  fun `reconnect after a 413 reopens with the corrected attributes`() {
    h.startStreaming()
    h.error(413)
    h.user = User(id = "user-1", data = mapOf("age" to "30"))

    h.reconnect()

    assertThat(h.sources).hasSize(2)
    val user = body(h.latest.openedRequests.single()).getJSONObject("user")
    assertThat(user.getJSONObject("data").getString("age")).isEqualTo("30")
  }

  @Test
  fun `reconnect after a terminal error is a no-op`() {
    h.startStreaming()
    h.giveUpTerminal()

    h.reconnect()

    assertThat(h.sources).hasSize(1)
    assertThat(h.latestFallback.startCalls).containsExactly(true)
    assertThat(h.latestFallback.stopCount).isEqualTo(0)
  }

  @Test
  fun `start alone does not open until enable`() {
    h.task.start()
    h.scheduler.runCurrent()
    assertThat(h.sources).isEmpty()

    // Control.
    h.task.enable()
    h.scheduler.runCurrent()
    assertThat(h.sources).hasSize(1)
  }

  @Test
  fun `enable before start opens on start`() {
    h.task.enable()
    h.scheduler.runCurrent()
    assertThat(h.sources).isEmpty()

    h.task.start()
    h.scheduler.runCurrent()

    assertThat(h.sources).hasSize(1)
  }

  @Test
  fun `reconnect before enable is a no-op`() {
    h.task.start()
    h.scheduler.runCurrent()

    h.reconnect()

    assertThat(h.sources).isEmpty()

    // Control: once enabled, a stream opens.
    h.task.enable()
    h.scheduler.runCurrent()
    assertThat(h.sources).hasSize(1)
  }

  @Test
  fun `start twice opens one stream`() {
    h.startStreaming()
    h.task.start()
    h.scheduler.runCurrent()
    assertThat(h.sources).hasSize(1)

    // Also on the fallback.
    h.giveUpNonTerminal()
    h.task.start()
    h.scheduler.runCurrent()
    assertThat(h.sources).hasSize(1)
    assertThat(h.fallbacks).hasSize(1)
    assertThat(h.latestFallback.startCalls).containsExactly(true)
  }

  @Test
  fun `terminal failure survives stop and start`() {
    h.startStreaming()
    h.giveUpTerminal()

    h.task.stop()
    h.scheduler.runCurrent()
    h.task.start()
    h.scheduler.runCurrent()

    assertThat(h.sources).hasSize(1)
    assertThat(h.fallbacks).hasSize(2)
    assertThat(h.fallbacks[1].startCalls).containsExactly(false)
  }

  @Test
  fun `background then foreground creates a new connection`() {
    h.startOpenStream()
    // Move the first backoff on, so a reused one would not start from 1000.
    h.dependencies.backoffs[0].nextDelayMillis()

    h.task.stop()
    h.scheduler.runCurrent()
    h.task.start()
    h.scheduler.runCurrent()

    assertThat(h.sources).hasSize(2)
    assertThat(h.sources[0].closed.get()).isTrue()
    assertThat(h.dependencies.backoffs).hasSize(2)
    assertThat(h.dependencies.backoffs[1].nextDelayMillis()).isEqualTo(1000L)
  }

  @Test
  fun `entry points only post to the executor`() {
    h.task.enable()
    h.task.start()
    assertThat(h.sources).isEmpty()
    h.scheduler.runCurrent()
    assertThat(h.sources).hasSize(1)

    h.open()
    h.task.onUserAttributesUpdated()
    assertThat(h.sources).hasSize(1)
    h.scheduler.advanceBy(200)
    assertThat(h.sources).hasSize(2)

    h.giveUpNonTerminal()
    h.task.stop()
    assertThat(h.latestFallback.stopCount).isEqualTo(0)
    h.scheduler.runCurrent()
    assertThat(h.latestFallback.stopCount).isEqualTo(1)
  }

  @Test
  fun `stop called from another thread takes effect when it returns`() {
    h.startOpenStream()
    h.event("put", payload())
    val shouldNotify =
      h.interactor.applied
        .single()
        .second
    h.latest.simulateEvent("put", payload(userEvaluationsId = "queued"))

    val thread = Thread { h.task.stop() }
    thread.start()
    thread.join(5_000)

    assertThat(shouldNotify()).isFalse()
    h.scheduler.runCurrent()
    assertThat(h.interactor.applied).hasSize(1)
  }

  @Test
  fun `attribute updates merge into one reconnect 200 ms after the last call`() {
    h.startOpenStream()

    h.task.onUserAttributesUpdated()
    h.scheduler.advanceBy(100)
    h.task.onUserAttributesUpdated()
    h.scheduler.advanceBy(50)
    h.task.onUserAttributesUpdated()

    h.scheduler.advanceBy(199)
    assertThat(h.scheduler.nowMillis).isEqualTo(349L)
    assertThat(h.sources).hasSize(1)
    h.scheduler.advanceBy(1)
    assertThat(h.sources).hasSize(2)
    h.scheduler.advanceBy(1_000)
    assertThat(h.sources).hasSize(2)
  }

  @Test
  fun `attribute update while stopped is ignored`() {
    h.startOpenStream()
    h.task.stop()
    h.scheduler.runCurrent()

    h.task.onUserAttributesUpdated()
    h.scheduler.advanceBy(200)

    assertThat(h.sources).hasSize(1)

    // Control: while running, it reconnects.
    val running = StreamingTaskHarness()
    running.startOpenStream()
    running.task.onUserAttributesUpdated()
    running.scheduler.advanceBy(200)
    assertThat(running.sources).hasSize(2)
  }

  @Test
  fun `stop cancels a pending merged reconnect`() {
    h.startOpenStream()
    h.task.onUserAttributesUpdated()
    h.scheduler.advanceBy(100)

    h.task.stop()
    h.scheduler.advanceBy(200)

    assertThat(h.sources).hasSize(1)
    assertThat(h.scheduler.pendingCount).isEqualTo(0)
  }

  @Test
  fun `onOpen queued before stop neither clears the flag nor stops the fallback`() {
    h.startStreaming()
    h.giveUpNonTerminal()
    h.scheduler.advanceBy(300_000)
    assertThat(h.sources).hasSize(2)

    h.latest.simulateOpen()
    h.task.stop()
    h.scheduler.runCurrent()

    assertThat(h.interactor.cleared).isEmpty()

    // Control: the same open without the stop clears the flag.
    val control = StreamingTaskHarness()
    control.startStreaming()
    control.giveUpNonTerminal()
    control.scheduler.advanceBy(300_000)
    control.open()
    assertThat(control.interactor.cleared).containsExactly(initialState)
  }

  @Test
  fun `onOpen queued before a quick stop and start still runs`() {
    h.startStreaming()

    h.latest.simulateOpen()
    h.task.stop()
    h.task.start()
    h.scheduler.runCurrent()

    // Same as iOS: the old open passes the running check and clears the flag with the old
    // request's state. Pins today's behavior: change this test if that gap is ever closed.
    assertThat(h.interactor.cleared).containsExactly(initialState)
    assertThat(h.sources).hasSize(2)
  }

  @Test
  fun `data queued before a quick stop and start is applied`() {
    h.startOpenStream()

    h.latest.simulateEvent("put", payload(userEvaluationsId = "queued"))
    h.task.stop()
    h.task.start()
    h.scheduler.runCurrent()

    // Same as iOS: valid data for the same user.
    assertThat(h.interactor.applied.map { it.first.userEvaluationsId }).containsExactly("queued")
  }

  @Test
  fun `error queued before stop starts no fallback`() {
    h.startOpenStream()

    h.latest.simulateError(400)
    h.task.stop()
    h.scheduler.runCurrent()

    assertThat(h.fallbacks).isEmpty()
  }

  @Test
  fun `timers due between stop and its cleanup do nothing while stopped`() {
    // Recovery: stop() runs inside a task due at the same time as the recovery, but queued before
    // it, so the recovery runs after stop() and before its cleanup.
    h.startStreaming()
    h.scheduler.schedule(300_000) { h.task.stop() }
    h.giveUpNonTerminal()
    h.scheduler.advanceBy(300_000)
    assertThat(h.sources).hasSize(1)

    // The merged reconnect, the same way.
    val merge = StreamingTaskHarness()
    merge.startOpenStream()
    merge.scheduler.schedule(200) { merge.task.stop() }
    merge.task.onUserAttributesUpdated()
    merge.scheduler.advanceBy(200)
    assertThat(merge.sources).hasSize(1)
  }

  @Test
  fun `entry points from two threads with a real executor keep at most one stream`() {
    val real = RealExecutorTask()
    real.task.enable()

    val a =
      Thread {
        repeat(200) {
          real.task.start()
          real.task.stop()
        }
      }
    val b = Thread { repeat(200) { real.task.onUserAttributesUpdated() } }
    a.start()
    b.start()
    a.join(10_000)
    b.join(10_000)
    real.task.start()
    real.drain()
    // Let any merged reconnect (200 ms) fire, then wait for what it posted.
    Thread.sleep(400)
    real.drain()

    assertThat(real.dependencies.sources).isNotEmpty()
    assertThat(real.dependencies.sources.count { !it.closed.get() }).isEqualTo(1)
    assertThat(logger.throwables(Log.ERROR)).isEmpty()
  }

  @Test
  fun `stop returns at once and closes the live source while the executor is busy`() {
    val real = RealExecutorTask()
    real.task.enable()
    real.task.start()
    real.drain()
    val source = real.dependencies.sources.single()
    source.simulateOpen()
    real.drain()

    val release = CountDownLatch(1)
    real.executor.execute { release.await(5, TimeUnit.SECONDS) }
    val startedAt = System.nanoTime()
    real.task.stop()
    val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

    assertThat(elapsedMillis).isLessThan(100L)
    // Closed on the calling thread, before the busy block ends.
    assertThat(source.closed.get()).isTrue()

    release.countDown()
    real.drain()
    // The cleanup ran after the busy block, and a new start opens a new stream.
    real.task.start()
    real.drain()
    assertThat(real.dependencies.sources).hasSize(2)
  }

  @Test
  fun `start then stop then enable opens nothing until the next start`() {
    h.task.start()
    h.task.stop()
    h.task.enable()
    h.scheduler.runCurrent()
    assertThat(h.sources).isEmpty()

    h.task.start()
    h.scheduler.runCurrent()

    assertThat(h.sources).hasSize(1)
  }

  @Test
  fun `nothing starts after destroy`() {
    h.task.destroy()
    h.task.start()
    h.task.enable()
    h.task.onUserAttributesUpdated()
    h.scheduler.runCurrent()
    h.scheduler.advanceBy(600_000)

    assertThat(h.sources).isEmpty()
    assertThat(h.fallbacks).isEmpty()

    // Positive control: a stopped (not destroyed) task opens a stream again.
    val stopped = StreamingTaskHarness()
    stopped.task.stop()
    stopped.task.start()
    stopped.task.enable()
    stopped.task.onUserAttributesUpdated()
    stopped.scheduler.runCurrent()
    assertThat(stopped.sources).hasSize(1)
  }
}
