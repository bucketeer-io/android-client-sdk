package io.bucketeer.sdk.android.internal.scheduler

import io.bucketeer.sdk.android.internal.di.Component
import io.bucketeer.sdk.android.internal.event.EventInteractor
import io.bucketeer.sdk.android.internal.event.SendEventsResult
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class EventForegroundTask(
  private val component: Component,
  private val executor: ScheduledExecutorService,
) : ScheduledTask {

  private var scheduledFuture: ScheduledFuture<*>? = null

  private val eventUpdateListener = EventInteractor.EventUpdateListener { _ ->
    executor.execute {
      val result = component.eventInteractor.sendEvents(force = false)
      if (result is SendEventsResult.Success && result.sent) {
        reschedule()
      }
    }
  }

  internal fun reschedule() {
    scheduledFuture?.cancel(false)
    scheduledFuture = executor.scheduleWithFixedDelay(
      { component.eventInteractor.sendEvents(force = true) },
      component.config.eventsFlushInterval,
      component.config.eventsFlushInterval,
      TimeUnit.MILLISECONDS,
    )
  }

  override fun start() {
    component.eventInteractor.setEventUpdateListener(this.eventUpdateListener)
    reschedule()
  }

  override fun stop() {
    component.eventInteractor.setEventUpdateListener(null)
    scheduledFuture?.cancel(false)
  }
}
