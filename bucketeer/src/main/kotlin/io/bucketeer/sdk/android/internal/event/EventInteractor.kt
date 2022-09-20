package io.bucketeer.sdk.android.internal.event

import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.Clock
import io.bucketeer.sdk.android.internal.IdGenerator
import io.bucketeer.sdk.android.internal.event.db.EventDao
import io.bucketeer.sdk.android.internal.logd
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.remote.ApiClient
import io.bucketeer.sdk.android.internal.remote.RegisterEventsResult

internal class EventInteractor(
  private val eventsMaxBatchQueueCount: Int,
  private val apiClient: ApiClient,
  private val eventDao: EventDao,
  private val clock: Clock,
  private val idGenerator: IdGenerator,
) {

  private var eventUpdateListener: EventUpdateListener? = null

  fun setEventUpdateListener(listener: EventUpdateListener?) {
    this.eventUpdateListener = listener
  }

  fun trackEvaluationEvent(featureTag: String, user: User, evaluation: Evaluation) {
    eventDao.addEvent(
      newEvaluationEvent(clock, idGenerator, featureTag, user, evaluation),
    )

    updateEventsAndNotify()
  }

  fun trackDefaultEvaluationEvent(featureTag: String, user: User, featureId: String) {
    eventDao.addEvent(
      newDefaultEvaluationEvent(clock, idGenerator, featureTag, user, featureId),
    )

    updateEventsAndNotify()
  }

  fun trackGoalEvent(featureTag: String, user: User, goalId: String, value: Double) {
    eventDao.addEvent(
      newGoalEvent(clock, idGenerator, goalId, value, featureTag, user),
    )

    updateEventsAndNotify()
  }

  fun trackFetchEvaluationsSuccess(
    featureTag: String,
    seconds: Long,
    sizeByte: Int,
  ) {
    eventDao.addEvents(
      listOf(
        newGetEvaluationLatencyMetricsEvent(clock, idGenerator, seconds, featureTag),
        newGetEvaluationSizeMetricsEvent(clock, idGenerator, sizeByte, featureTag),
      ),
    )

    updateEventsAndNotify()
  }

  fun trackFetchEvaluationsFailure(
    featureTag: String,
    error: BKTException,
  ) {
    val event = when (error) {
      is BKTException.NetworkException,
      is BKTException.TimeoutException,
      -> newTimeoutErrorCountMetricsEvent(
        clock,
        idGenerator,
        featureTag,
      )
      else -> newInternalErrorCountMetricsEvent(clock, idGenerator, featureTag)
    }

    eventDao.addEvent(event)

    updateEventsAndNotify()
  }

  fun sendEvents(force: Boolean = false): SendEventsResult {
    val current = eventDao.getEvents()

    if (current.isEmpty()) {
      logd { "no events to register" }
      return SendEventsResult.Success(sent = false)
    }
    if (!force && current.size < eventsMaxBatchQueueCount) {
      logd { "event count is less than threshold - current: ${current.size}, threshold: $eventsMaxBatchQueueCount" }
      return SendEventsResult.Success(sent = false)
    }

    val sendingEvents = current.take(eventsMaxBatchQueueCount)

    @Suppress("MoveVariableDeclarationIntoWhen")
    val result = apiClient.registerEvents(sendingEvents)

    return when (result) {
      is RegisterEventsResult.Success -> {
        val errors = result.value.data.errors
        val deleteIds = sendingEvents.map { it.id }
          .filter { eventId ->
            // if the event does not contain in error, delete it
            val error = errors[eventId] ?: return@filter true
            // if the error is not retriable, delete it
            !error.retriable
          }

        eventDao.delete(deleteIds)

        updateEventsAndNotify()

        SendEventsResult.Success(sent = true)
      }
      is RegisterEventsResult.Failure -> {
        logd(throwable = result.error) { "Failed to register events" }
        SendEventsResult.Failure(result.error)
      }
    }
  }

  private fun updateEventsAndNotify() {
    eventUpdateListener?.onUpdate(eventDao.getEvents())
  }

  fun interface EventUpdateListener {
    fun onUpdate(events: List<Event>)
  }
}
