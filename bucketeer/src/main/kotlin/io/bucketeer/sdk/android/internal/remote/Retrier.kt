package io.bucketeer.sdk.android.internal.remote

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.pow

/**
 * Retrier handles retry logic with exponential backoff for API requests.
 *
 * This class is designed to work with Android's ScheduledExecutorService,
 * similar to iOS's dispatch queue async after mechanism.
 *
 * The exponential backoff formula is: delay = multiplier^(attemptsMade) * baseDelayMillis
 * With default values (multiplier=2.0, baseDelayMillis=1000ms), this produces:
 * - 1st retry after 2 seconds (2^1 * 1s)
 * - 2nd retry after 4 seconds (2^2 * 1s)
 * - 3rd retry after 8 seconds (2^3 * 1s)
 *
 * @param executor ScheduledExecutorService for scheduling delayed retries
 */
internal class Retrier(
  private val executor: ScheduledExecutorService,
) {
  companion object {
    private const val DEFAULT_BASE_DELAY_MILLIS = 1000L
    private const val DEFAULT_MULTIPLIER = 2.0
    private const val DEFAULT_MAX_ATTEMPTS = 4 // 1 initial + 3 retries
  }

  /**
   * Attempts to execute a task with retry logic.
   *
   * @param task The task to execute. Must call the completion handler exactly once.
   * @param condition Predicate to determine if an exception is retriable.
   * @param maxAttempts Maximum number of attempts (default: 4 = 1 initial + 3 retries).
   * @param baseDelayMillis Base delay in milliseconds for exponential backoff (default: 1000ms).
   * @param multiplier Exponential multiplier for delay calculation (default: 2.0).
   * @param completion Callback invoked with the result or final exception.
   */
  fun <T> attempt(
    task: (completion: (Result<T>) -> Unit) -> Unit,
    condition: (Throwable) -> Boolean,
    maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    baseDelayMillis: Long = DEFAULT_BASE_DELAY_MILLIS,
    multiplier: Double = DEFAULT_MULTIPLIER,
    completion: (Result<T>) -> Unit,
  ) {
    attemptInternal(
      task = task,
      condition = condition,
      remaining = maxAttempts,
      maxAttempts = maxAttempts,
      baseDelayMillis = baseDelayMillis,
      multiplier = multiplier,
      completion = completion,
    )
  }

  private fun <T> attemptInternal(
    task: (completion: (Result<T>) -> Unit) -> Unit,
    condition: (Throwable) -> Boolean,
    remaining: Int,
    maxAttempts: Int,
    baseDelayMillis: Long,
    multiplier: Double,
    completion: (Result<T>) -> Unit,
  ) {
    if (remaining <= 0) {
      completion(Result.failure(IllegalStateException("Max retry attempts reached")))
      return
    }

    // Execute the task
    task { result ->
      result.fold(
        onSuccess = { value ->
          // Success - call completion
          completion(Result.success(value))
        },
        onFailure = { error ->
          // Check if we should retry
          val shouldRetry = condition(error) && remaining > 1

          if (!shouldRetry) {
            // No more retries or non-retriable error
            completion(Result.failure(error))
            return@fold
          }

          // Calculate exponential backoff delay
          val attemptsMade = maxAttempts - remaining + 1
          val nextDelay = (multiplier.pow(attemptsMade.toDouble()) * baseDelayMillis).toLong()

          // Schedule next retry after delay (non-blocking)
          executor.schedule(
            {
              attemptInternal(
                task = task,
                condition = condition,
                remaining = remaining - 1,
                maxAttempts = maxAttempts,
                baseDelayMillis = baseDelayMillis,
                multiplier = multiplier,
                completion = completion,
              )
            },
            nextDelay,
            TimeUnit.MILLISECONDS,
          )
        },
      )
    }
  }
}
