package io.bucketeer.sdk.android.internal.streaming

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.BKTLogger
import io.bucketeer.sdk.android.internal.LoggerHolder
import io.bucketeer.sdk.android.internal.streaming.fakes.FakeStreamScheduler
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

class StreamSchedulerTest {
  private lateinit var executor: ScheduledExecutorService
  private lateinit var logger: RecordingLogger

  @Before
  fun setUp() {
    LoggerHolder.clearLoggers()
    logger = RecordingLogger()
    LoggerHolder.addLogger(logger)
    executor = Executors.newSingleThreadScheduledExecutor { Thread(it, EXECUTOR_THREAD) }
  }

  @After
  fun tearDown() {
    executor.shutdownNow()
    LoggerHolder.clearLoggers()
  }

  @Test
  fun `executor scheduler runs work on the executor thread after the delay`() {
    val scheduler = ExecutorStreamScheduler(executor)
    val ran = CountDownLatch(1)
    val threadName = AtomicReference<String>()
    val elapsedNanos = AtomicReference<Long>()
    val start = System.nanoTime()

    scheduler.schedule(100) {
      threadName.set(Thread.currentThread().name)
      elapsedNanos.set(System.nanoTime() - start)
      ran.countDown()
    }

    assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue()
    assertThat(threadName.get()).isEqualTo(EXECUTOR_THREAD)
    assertThat(TimeUnit.NANOSECONDS.toMillis(elapsedNanos.get())).isAtLeast(100)
  }

  @Test
  fun `executor scheduler never runs cancelled work`() {
    val scheduler = ExecutorStreamScheduler(executor)
    val cancelledRan = AtomicBoolean(false)
    val otherRan = CountDownLatch(1)

    val handle = scheduler.schedule(100) { cancelledRan.set(true) }
    scheduler.schedule(150) { otherRan.countDown() }
    // Cancelled on the executor thread, as StreamConnection does.
    executor.submit { handle.cancel() }.get(1, TimeUnit.SECONDS)

    // Positive control: the other task, due later, did run.
    assertThat(otherRan.await(2, TimeUnit.SECONDS)).isTrue()
    assertThat(cancelledRan.get()).isFalse()
  }

  @Test
  fun `fake scheduler runs due work in time order`() {
    val scheduler = FakeStreamScheduler()
    val order = mutableListOf<String>()

    scheduler.schedule(300) { order.add("300") }
    scheduler.schedule(100) { order.add("100a") }
    scheduler.schedule(200) { order.add("200") }
    scheduler.schedule(100) { order.add("100b") }
    scheduler.advanceBy(300)

    assertThat(order).containsExactly("100a", "100b", "200", "300").inOrder()
  }

  @Test
  fun `fake scheduler does not run work before it is due`() {
    val scheduler = FakeStreamScheduler()
    var ran = false
    scheduler.schedule(1_000) { ran = true }

    scheduler.advanceBy(999)
    assertThat(ran).isFalse()
    assertThat(scheduler.nowMillis).isEqualTo(999)

    scheduler.advanceBy(1)
    assertThat(ran).isTrue()
    assertThat(scheduler.nowMillis).isEqualTo(1_000)
  }

  @Test
  fun `fake scheduler skips cancelled work and runs work scheduled while advancing`() {
    val scheduler = FakeStreamScheduler()
    val order = mutableListOf<String>()
    val nestedAt = mutableListOf<Long>()

    val cancelled = scheduler.schedule(50) { order.add("cancelled") }
    scheduler.schedule(100) {
      order.add("outer")
      scheduler.schedule(200) {
        order.add("nested")
        nestedAt.add(scheduler.nowMillis)
      }
      scheduler.schedule(1_000) { order.add("too late") }
    }
    assertThat(scheduler.pendingCount).isEqualTo(2)
    cancelled.cancel()
    assertThat(scheduler.pendingCount).isEqualTo(1)

    scheduler.advanceBy(300)

    assertThat(order).containsExactly("outer", "nested").inOrder()
    assertThat(nestedAt).containsExactly(300L)
    assertThat(scheduler.pendingCount).isEqualTo(1)
  }

  @Test
  fun `fake scheduler execute runs on runCurrent`() {
    val scheduler = FakeStreamScheduler()
    var ran = false

    scheduler.execute { ran = true }
    assertThat(ran).isFalse()
    assertThat(scheduler.pendingCount).isEqualTo(1)

    scheduler.runCurrent()
    assertThat(ran).isTrue()
    assertThat(scheduler.nowMillis).isEqualTo(0)
  }

  @Test
  fun `executor scheduler logs a throwing task and keeps running later ones`() {
    val scheduler = ExecutorStreamScheduler(executor)
    val laterRan = CountDownLatch(1)

    scheduler.schedule(0) { throw IllegalStateException("timer failed") }
    scheduler.schedule(50) { laterRan.countDown() }

    assertThat(laterRan.await(2, TimeUnit.SECONDS)).isTrue()
    val errors = logger.throwables(Log.ERROR)
    assertThat(errors).hasSize(1)
    assertThat(errors[0]).hasMessageThat().isEqualTo("timer failed")
  }

  @Test
  fun `executor scheduler clock follows the monotonic clock not wall time`() {
    val monotonicMillis = System.nanoTime() / 1_000_000
    val wallMillis = System.currentTimeMillis()
    // Positive control: the two clocks really differ here, so the check below can tell them apart.
    assertThat(abs(wallMillis - monotonicMillis)).isGreaterThan(1_000L)

    val now = ExecutorStreamScheduler(executor).nowMillis

    assertThat(abs(now - monotonicMillis)).isLessThan(1_000L)
  }

  private companion object {
    const val EXECUTOR_THREAD = "stream-test-executor"
  }

  private class RecordingLogger : BKTLogger {
    private val entries = mutableListOf<Triple<Int, String?, Throwable?>>()

    override fun log(
      priority: Int,
      messageCreator: (() -> String?)?,
      throwable: Throwable?,
    ) {
      synchronized(entries) {
        entries.add(Triple(priority, messageCreator?.invoke(), throwable))
      }
    }

    fun throwables(priority: Int): List<Throwable> =
      synchronized(entries) { entries.filter { it.first == priority }.mapNotNull { it.third } }
  }
}
