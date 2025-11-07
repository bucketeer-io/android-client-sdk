package io.bucketeer.sdk.android.internal.remote

/**
* Retries the given [block] of code up to [maxRetries] times if it throws an exception
* that satisfies [exceptionCheck]. Linear backoff delay is applied between retries.
*
* Note: This function blocks the current thread during delays. Use only on background threads.
*
* @param maxRetries Maximum retry attempts (default: 3).
* @param delayMillis Base delay in milliseconds between retries (default: 1000ms).
* @param exceptionCheck Predicate to determine if an exception is retriable.
* @param block Code block to execute.
* @return Result of [block] if successful.
* @throws Throwable Last exception if retries fail or exception is not retriable.
*/
internal fun <T> retryOnException(
  maxRetries: Int = 3,
  delayMillis: Long = 1000,
  exceptionCheck: (Throwable) -> Boolean,
  block: () -> T,
): T {
  var lastException: Throwable? = null
  val maxAttempts = if (maxRetries < 0) 1 else maxRetries + 1
  for (attempt in 0 until maxAttempts) {
    try {
      return block()
    } catch (e: Throwable) {
      lastException = e
      if (!exceptionCheck(e) || attempt >= maxAttempts - 1) {
        throw e
      }
      // Delay with linear backoff using Thread.sleep.
      // Ensure this runs on a background thread as it blocks the current thread.
      // Coroutine support is not used since the SDK does not rely on coroutines.
      Thread.sleep(delayMillis * (attempt + 1))
    }
  }
  // This line should never be reached, but Kotlin compiler requires a return statement here.
  throw lastException!!
}
