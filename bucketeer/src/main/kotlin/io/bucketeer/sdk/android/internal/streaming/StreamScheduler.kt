package io.bucketeer.sdk.android.internal.streaming

import io.bucketeer.sdk.android.internal.loge
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** A handle to scheduled work. */
internal fun interface Cancellable {
  fun cancel()
}

/**
 * Timers and "now" for the stream. Work runs on the SDK executor.
 *
 * Cancel only from the SDK executor: a task cancelled there can never run afterwards. Cancelled
 * from another thread, a task that already started keeps running.
 */
internal interface StreamScheduler {
  /** Milliseconds from a clock that never jumps (not wall time). */
  val nowMillis: Long

  fun schedule(
    delayMillis: Long,
    task: () -> Unit,
  ): Cancellable
}

internal class ExecutorStreamScheduler(
  private val executor: ScheduledExecutorService,
  // The clock ScheduledThreadPoolExecutor itself uses for its delays. It stops in deep sleep, like
  // the timers, so "how long has the stream been unhealthy" and the timers can't disagree after
  // the CPU sleeps. Wall time would jump when the user changes the device time.
  private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : StreamScheduler {
  override val nowMillis: Long get() = clock()

  override fun schedule(
    delayMillis: Long,
    task: () -> Unit,
  ): Cancellable {
    val future =
      executor.schedule(
        {
          // ScheduledThreadPoolExecutor would keep the exception in the future, where nobody
          // reads it. Log it instead, so a failing timer is visible.
          runCatching(task).onFailure { loge(it) { "Stream timer failed" } }
        },
        delayMillis,
        TimeUnit.MILLISECONDS,
      )
    return Cancellable { future.cancel(false) }
  }
}
