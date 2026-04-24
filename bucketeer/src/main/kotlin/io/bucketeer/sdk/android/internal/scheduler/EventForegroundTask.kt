package io.bucketeer.sdk.android.internal.scheduler

import io.bucketeer.sdk.android.BKTClientImpl
import io.bucketeer.sdk.android.internal.di.Component
import io.bucketeer.sdk.android.internal.event.EventInteractor
import io.bucketeer.sdk.android.internal.event.SendEventsResult
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Events are sent to the server in the following two cases:
 *
 * 1. New event is added to the cache, and the cache exceeded the limit.
 * 2. Periodic flushing job.
 *
 * In the first case, we need to [reschedule] periodic flushing job to reduce unnecessary execution.
 */
internal class EventForegroundTask(
  private val component: Component,
  private val executor: ScheduledExecutorService,
) : ScheduledTask {
  private var scheduledFuture: ScheduledFuture<*>? = null

  // This listener is called whenever the event cache is updated
  private val eventUpdateListener =
    EventInteractor.EventUpdateListener { _ ->
      executor.execute {
        // send events if the cache exceeded the limit
        val result = component.eventInteractor.sendEvents(force = false)
        if (result is SendEventsResult.Success && result.sent) {
          // reschedule the background task if event is actually sent.
          reschedule(component.config.eventsFlushInterval)
        }
      }
    }



  private fun reschedule(interval: Long) {
    scheduledFuture?.cancel(false)
    scheduledFuture =
      executor.scheduleWithFixedDelay(
        { sendEvents() },
        interval,
        interval,
        TimeUnit.MILLISECONDS,
      )
  }

  private fun sendEvents() {
    component.eventCancellationRunner.scheduleTask(
      block = { component.eventInteractor.sendEvents(force = true) },
      retryPredicate = { result ->
        result is SendEventsResult.Failure &&
          result.error is io.bucketeer.sdk.android.BKTException.ClientClosedRequestException
      },
    )
  }

  override fun start() {
    component.eventInteractor.setEventUpdateListener(this.eventUpdateListener)
    reschedule(component.config.eventsFlushInterval)
  }

  override fun stop() {
    component.eventInteractor.setEventUpdateListener(null)
    scheduledFuture?.cancel(false)
  }
}
