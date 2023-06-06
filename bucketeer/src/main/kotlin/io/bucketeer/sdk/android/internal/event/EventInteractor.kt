package io.bucketeer.sdk.android.internal.event

import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.Clock
import io.bucketeer.sdk.android.internal.IdGenerator
import io.bucketeer.sdk.android.internal.event.db.EventDao
import io.bucketeer.sdk.android.internal.logd
import io.bucketeer.sdk.android.internal.model.ApiId
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
  private val appVersion: String,
  private val featureTag: String,
) {

  private var eventUpdateListener: EventUpdateListener? = null

  fun setEventUpdateListener(listener: EventUpdateListener?) {
    this.eventUpdateListener = listener
  }

  fun trackEvaluationEvent(featureTag: String, user: User, evaluation: Evaluation) {
    eventDao.addEvent(
      newEvaluationEvent(clock, idGenerator, featureTag, user, evaluation, appVersion),
    )

    updateEventsAndNotify()
  }

  fun trackDefaultEvaluationEvent(featureTag: String, user: User, featureId: String) {
    eventDao.addEvent(
      newDefaultEvaluationEvent(clock, idGenerator, featureTag, user, featureId, appVersion),
    )

    updateEventsAndNotify()
  }

  fun trackGoalEvent(featureTag: String, user: User, goalId: String, value: Double) {
    eventDao.addEvent(
      newGoalEvent(clock, idGenerator, goalId, value, featureTag, user, appVersion),
    )

    updateEventsAndNotify()
  }

  fun trackFetchEvaluationsSuccess(
    featureTag: String,
    seconds: Long,
    sizeByte: Int,
  ) {
    // For get_evaluations, we will report all metrics events, Including the latency and size metrics events.
    // https://github.com/bucketeer-io/android-client-sdk/issues/56
    eventDao.addEvents(
      newSuccessMetricsEvents(
        clock = clock,
        idGenerator = idGenerator,
        featureTag = featureTag,
        appVersion = appVersion,
        apiId = ApiId.GET_EVALUATIONS,
        latencySecond = seconds,
        sizeByte = sizeByte,
      ),
    )

    updateEventsAndNotify()
  }

  fun trackFetchEvaluationsFailure(
    featureTag: String,
    error: BKTException,
  ) = trackMetricsEventWhenRequestAPIFailure(
    featureTag,
    error,
    ApiId.GET_EVALUATIONS,
  )

  private fun trackApiFailureMetricsEvent(
    featureTag: String,
    error: BKTException,
    apiId: ApiId,
  ) {
    val event = newErrorMetricsEvent(
      clock = clock,
      idGenerator = idGenerator,
      featureTag = featureTag,
      appVersion = appVersion,
      error = error,
      apiId = apiId,
    )
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
        val errors = result.value.errors
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
        val error = result.error
        logd(throwable = error) { "Failed to register events" }
        // For register_events, we will only report error metrics, such as timeout, network, server errors, etc.
        // https://github.com/bucketeer-io/android-client-sdk/issues/56
        trackSendEventsFailure(error)

        SendEventsResult.Failure(error)
      }
    }
  }

  private fun trackSendEventsFailure(
    error: BKTException,
  ) = trackMetricsEventWhenRequestAPIFailure(
    // discussed: here https://github.com/bucketeer-io/android-client-sdk/pull/64/files#r1214187627
    featureTag = featureTag,
    error,
    ApiId.REGISTER_EVENTS,
  )

  private fun updateEventsAndNotify() {
    eventUpdateListener?.onUpdate(eventDao.getEvents())
  }

  fun interface EventUpdateListener {
    fun onUpdate(events: List<Event>)
  }
}
