package io.bucketeer.sdk.android.internal.remote

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.ScheduledExecutorService

internal fun <T> retryOnException(
  executor: ScheduledExecutorService,
  maxRetries: Int = 3,
  delayMillis: Long = 1000,
  exceptionCheck: (Throwable) -> Boolean,
  block: () -> T,
): T {
  val futureTask = object : FutureTask<T>(
    Callable {
      var lastException: Throwable? = null
      repeat(maxRetries + 1) { attempt ->
        try {
          return@Callable block()
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
  ) {}

  executor.execute(futureTask)
  return futureTask.getOrThrow()
}

// Unwrap ExecutionException and throw the cause directly
fun <T> Future<T>.getOrThrow(): T {
  return try {
    get()
  } catch (e: ExecutionException) {
    throw e.cause ?: e
  }
}
