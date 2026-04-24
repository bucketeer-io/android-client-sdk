package io.bucketeer.sdk.android.internal.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class SettableFuture<V> : Future<V> {
  private val latch = CountDownLatch(1)
  private var value: V? = null
  private var exception: Throwable? = null
  private var isCancelled = false

  fun set(value: V?) {
    this.value = value
    latch.countDown()
  }

  fun setException(throwable: Throwable?) {
    this.exception = throwable
    latch.countDown()
  }

  override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
    // We don't support true interruption of the running task via this Future,
    // but we can mark it as cancelled.
    isCancelled = true
    latch.countDown()
    return true
  }

  override fun isCancelled(): Boolean {
    return isCancelled
  }

  override fun isDone(): Boolean {
    return latch.count == 0L
  }

  @Throws(InterruptedException::class, ExecutionException::class)
  override fun get(): V? {
    latch.await()
    return getResult()
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  override fun get(timeout: Long, unit: TimeUnit): V? {
    if (!latch.await(timeout, unit)) {
      throw TimeoutException()
    }
    return getResult()
  }

  private fun getResult(): V? {
    if (isCancelled) {
      throw java.util.concurrent.CancellationException()
    }
    if (exception != null) {
      throw ExecutionException(exception)
    }
    return value
  }
}
