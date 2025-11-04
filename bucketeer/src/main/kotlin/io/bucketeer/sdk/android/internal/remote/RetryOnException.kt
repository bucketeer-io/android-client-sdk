package io.bucketeer.sdk.android.internal.remote

import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService

internal fun <T> retryOnException(
  executor: ScheduledExecutorService,
  maxRetries: Int = 3,
  delayMillis: Long = 1000,
  exceptionCheck: (Throwable) -> Boolean,
  block: () -> T,
): T {
  return executor.submit<T> {
    var lastException: Throwable? = null

    for (attempt in 0..maxRetries) {
      try {
        return@submit block()
      } catch (e: Throwable) {
        lastException = e
        if (!exceptionCheck(e) || attempt >= maxRetries) {
          throw e
        }
        // Sleep directly since we're already inside the executor task
        Thread.sleep(delayMillis * (attempt + 1))
      }
    }
    throw lastException!!
  }.getOrThrow()
}

internal fun <T> retryOnExceptionSync(
  maxRetries: Int = 3,
  delayMillis: Long = 1000,
  exceptionCheck: (Throwable) -> Boolean,
  block: () -> T,
): T {
  var lastException: Throwable? = null

  for (attempt in 0..maxRetries) {
    try {
      return block()
    } catch (e: Throwable) {
      lastException = e
      if (!exceptionCheck(e) || attempt >= maxRetries) {
        throw e
      }
      Thread.sleep(delayMillis * (attempt + 1))
    }
  }
  throw lastException!!
}

// Unwrap ExecutionException and throw the cause directly
fun <T> Future<T>.getOrThrow(): T =
  try {
    get()
  } catch (e: ExecutionException) {
    throw e.cause ?: e
  }
