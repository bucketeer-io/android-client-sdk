package io.bucketeer.sdk.android.internal.streaming.fakes

import io.bucketeer.sdk.android.internal.streaming.Cancellable
import io.bucketeer.sdk.android.internal.streaming.StreamScheduler
import java.util.concurrent.Executor

/**
 * Virtual clock and task queue for streaming tests. Also an [Executor], so one fake drives both
 * the timers and the work posted to the SDK executor.
 *
 * Safe to call from several threads (OkHttp's thread posts into it), but tasks only run on the
 * thread that calls [advanceBy] or [runCurrent], outside the lock.
 */
internal class FakeStreamScheduler :
  StreamScheduler,
  Executor {
  private class Task(
    val dueAt: Long,
    val sequence: Long,
    val block: () -> Unit,
  ) {
    @Volatile var cancelled = false
  }

  private val lock = Any()
  private val tasks = mutableListOf<Task>()
  private var nextSequence = 0L

  @Volatile private var now = 0L

  override val nowMillis: Long get() = now

  /** Scheduled tasks that are not cancelled and have not run yet. */
  val pendingCount: Int
    get() = synchronized(lock) { tasks.count { !it.cancelled } }

  override fun schedule(
    delayMillis: Long,
    task: () -> Unit,
  ): Cancellable {
    val scheduled =
      synchronized(lock) {
        Task(dueAt = now + delayMillis, sequence = nextSequence++, block = task).also { tasks.add(it) }
      }
    return Cancellable { scheduled.cancelled = true }
  }

  override fun execute(command: Runnable) {
    schedule(0) { command.run() }
  }

  /**
   * Moves the clock forward by [millis], running every due task in (due time, scheduling order)
   * order, including tasks scheduled while running that become due within the window.
   */
  fun advanceBy(millis: Long) {
    require(millis >= 0) { "millis must not be negative: $millis" }
    val target = now + millis
    while (true) {
      val next =
        synchronized(lock) {
          tasks.removeAll { it.cancelled }
          tasks
            .filter { it.dueAt <= target }
            .minWithOrNull(compareBy<Task>({ it.dueAt }, { it.sequence }))
            ?.also { tasks.remove(it) }
        } ?: break
      if (next.dueAt > now) now = next.dueAt
      next.block()
    }
    now = target
  }

  /** Runs everything due now, without moving the clock. */
  fun runCurrent() = advanceBy(0)
}
