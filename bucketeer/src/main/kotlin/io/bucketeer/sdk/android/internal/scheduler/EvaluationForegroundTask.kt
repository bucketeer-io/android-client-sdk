package io.bucketeer.sdk.android.internal.scheduler

import io.bucketeer.sdk.android.BKTClientImpl
import io.bucketeer.sdk.android.internal.di.Component
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private const val RETRY_POLLING_INTERVAL: Long = 1_000 * 60 // 1 minute
private const val MAX_RETRY_COUNT = 5

internal class EvaluationForegroundTask(
  private val component: Component,
  private val executor: ScheduledExecutorService,
  private val retryPollingInterval: Long = RETRY_POLLING_INTERVAL,
  private val maxRetryCount: Int = MAX_RETRY_COUNT,
) : ScheduledTask {

  private var scheduledFuture: ScheduledFuture<*>? = null

  private var retryCount: Int = 0

  private fun reschedule(interval: Long) {
    scheduledFuture?.cancel(false)
    scheduledFuture = executor.scheduleWithFixedDelay(
      { fetchEvaluations() },
      interval,
      interval,
      TimeUnit.MILLISECONDS,
    )
  }

  private fun fetchEvaluations() {
    val result = BKTClientImpl.fetchEvaluationsSync(component, executor, null)
    if (result == null) {
      // success
      if (retryCount > 0) {
        // retried already, so reschedule with proper interval
        retryCount = 0
        reschedule(component.config.pollingInterval)
      }
    } else {
      // error
      if (component.config.pollingInterval <= retryPollingInterval) {
        // pollingInterval is short enough, do nothing
        return
      }
      val retried = retryCount > 0
      val canRetry = retryCount < maxRetryCount
      if (canRetry) {
        // we can retry more
        retryCount++
        if (!retried) {
          reschedule(retryPollingInterval)
        }
      } else {
        // we already retried enough, let's get back to daily job
        retryCount = 0
        reschedule(component.config.pollingInterval)
      }
    }
  }

  override fun start() {
    reschedule(component.config.pollingInterval)
  }

  override fun stop() {
    scheduledFuture?.cancel(false)
  }
}
