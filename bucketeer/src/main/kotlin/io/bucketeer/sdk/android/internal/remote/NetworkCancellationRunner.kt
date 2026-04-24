package io.bucketeer.sdk.android.internal.remote

import io.bucketeer.sdk.android.internal.IdGenerator
import io.bucketeer.sdk.android.internal.util.SettableFuture
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class NetworkCancellationRunner(
  private val executor: ScheduledExecutorService,
  private val idGenerator: IdGenerator,
) {
  // Holds the ID of the "current" active request (manual or background).
  // Any retry attempt with an older ID will abort.
  private val currentRequestId = AtomicReference<String?>(null)

  /**
   * Called by Background Tasks (e.g. ForegroundTask) before they start running.
   * This effectively cancels any pending Manual retries because they will see a new ID.
   */
  fun updateRequestID(newId: String) {
    currentRequestId.set(newId)
  }

  fun cancelExistingTasks() {
    updateRequestID(idGenerator.newId())
  }

  fun <T> scheduleTask(
    block: () -> T,
    retryPredicate: (T) -> Boolean,
  ): Future<T> {
    return scheduleInternal(block, retryPredicate, null)
  }

  fun <T> scheduleTaskWithCallback(
    block: () -> T,
    retryPredicate: (T) -> Boolean,
    completionCallback: (T) -> Unit,
  ): Future<T> {
    return scheduleInternal(block, retryPredicate, completionCallback)
  }

  private fun <T> scheduleInternal(
    block: () -> T,
    retryPredicate: (T) -> Boolean,
    completionCallback: ((T) -> Unit)? = null,
  ): Future<T> {
    val myId = idGenerator.newId()
    currentRequestId.set(myId)

    val future = SettableFuture<T>()

    fun attempt(retryCount: Int) {
      if (executor.isShutdown) {
        // If executor is down, we can't run.
        return
      }
      executor.execute {
        // 1. CANCELLATION CHECK
        // If the global ID has changed since we started, we are outdated.
        if (currentRequestId.get() != myId) {
          // We are outdated. Stop.
          future.setException(IllegalStateException("Outdated request"))
          return@execute
        }

        // 2. RUN
        // Run the blocking network call on this background thread
        val result = block()

        // 3. RETRY CHECK
        // If we failed (499) and have retries left...
        if (retryPredicate(result) && retryCount < 3) {
          // Schedule next attempt (Non-blocking wait)
          try {
            executor.schedule(
              { attempt(retryCount + 1) },
              1000,
              TimeUnit.MILLISECONDS
            )
          } catch (e: Throwable) {
            // If scheduling fails (executor shutdown), set the result as is
            future.set(result)
            completionCallback?.invoke(result)
          }
        } else {
          // Success or Max Retries reached
          future.set(result)
          completionCallback?.invoke(result)
        }
      }
    }

    // Start first attempt
    attempt(0)
    return future
  }
}
