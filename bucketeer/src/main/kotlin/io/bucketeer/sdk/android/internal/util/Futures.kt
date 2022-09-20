package io.bucketeer.sdk.android.internal.util

import io.bucketeer.sdk.android.BKTException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

internal object Futures {
  fun <V> success(value: V): Future<V> = SuccessFuture(value)
  fun <V> failure(error: BKTException): Future<V> = FailureFuture(error)
}

internal class SuccessFuture<V>(
  private val value: V,
) : Future<V> {
  override fun cancel(p0: Boolean): Boolean = false

  override fun isCancelled(): Boolean = false

  override fun isDone(): Boolean = true

  override fun get(): V = value

  override fun get(p0: Long, p1: TimeUnit?): V = value
}

internal class FailureFuture<V>(
  private val error: BKTException,
) : Future<V> {
  override fun cancel(p0: Boolean): Boolean = false

  override fun isCancelled(): Boolean = false

  override fun isDone(): Boolean = true

  override fun get(): V = throw ExecutionException(error)

  override fun get(p0: Long, p1: TimeUnit?): V = throw ExecutionException(error)
}
