package io.bucketeer.sdk.android.internal.remote

internal fun <T> retryOnException(
  maxRetries: Int = 3,
  delayMillis: Long = 1000,
  exceptionCheck: (Throwable) -> Boolean,
  block: () -> T,
): T {
  var lastException: Throwable? = null
  val maxAttempts = if (maxRetries < 0) 1 else maxRetries + 1
  for (attempt in 0..maxAttempts) {
    try {
      return block()
    } catch (e: Throwable) {
      lastException = e
      if (!exceptionCheck(e) || attempt >= maxAttempts - 1) {
        throw e
      }
      Thread.sleep(delayMillis * (attempt + 1))
    }
  }
  throw lastException!!
}
