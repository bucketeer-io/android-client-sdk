package io.bucketeer.sdk.android.internal.event

import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.Clock
import io.bucketeer.sdk.android.internal.IdGenerator
import io.bucketeer.sdk.android.internal.event.db.EventDao
import io.bucketeer.sdk.android.internal.logd
import io.bucketeer.sdk.android.internal.model.ApiID
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.MetricsEventType
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
      listOf(
        newMetricsEvent(
          clock = clock,
          idGenerator = idGenerator,
          featureTag = featureTag,
          appVersion = appVersion,
          metricsEventType = MetricsEventType.RESPONSE_LATENCY,
          apiID = ApiID.GET_EVALUATIONS,
          latencySecond = seconds,
        ),
        newMetricsEvent(
          clock = clock,
          idGenerator = idGenerator,
          featureTag = featureTag,
          appVersion = appVersion,
          metricsEventType = MetricsEventType.RESPONSE_SIZE,
          apiID = ApiID.GET_EVALUATIONS,
          sizeByte = sizeByte,
        ),
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
    ApiID.GET_EVALUATIONS,
  )

  private fun trackMetricsEventWhenRequestAPIFailure(
    featureTag: String?,
    error: BKTException,
    apiID: ApiID
  ) {

    val metricEventType = when (error) {
      is BKTException.BadRequestException -> MetricsEventType.BAD_REQUEST_ERROR
      is BKTException.ClientClosedRequestException -> MetricsEventType.CLIENT_CLOSED_REQUEST_ERROR
      is BKTException.FeatureNotFoundException -> MetricsEventType.NOT_FOUND_ERROR
      is BKTException.ForbiddenException -> MetricsEventType.FORBIDDEN_ERROR
      is BKTException.IllegalArgumentException -> MetricsEventType.INTERNAL_ERROR
      is BKTException.IllegalStateException -> MetricsEventType.INTERNAL_ERROR
      is BKTException.InternalServerErrorException -> MetricsEventType.INTERNAL_SERVER_ERROR
      is BKTException.InvalidHttpMethodException -> MetricsEventType.INTERNAL_ERROR
      is BKTException.NetworkException -> MetricsEventType.NETWORK_ERROR
      is BKTException.ServiceUnavailableException -> MetricsEventType.SERVICE_UNAVAILABLE_ERROR
      is BKTException.TimeoutException -> MetricsEventType.TIMEOUT_ERROR
      is BKTException.UnauthorizedException -> MetricsEventType.UNAUTHORIZED_ERROR
      is BKTException.UnknownException -> MetricsEventType.UNKNOWN
    }

    val event = newMetricsEvent(
      clock = clock,
      idGenerator = idGenerator,
      featureTag = featureTag,
      appVersion = appVersion,
      metricsEventType = metricEventType,
      apiID = apiID,
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
    featureTag = null,
    error,
    ApiID.REGISTER_EVENTS,
  )

  private fun updateEventsAndNotify() {
    eventUpdateListener?.onUpdate(eventDao.getEvents())
  }

  fun interface EventUpdateListener {
    fun onUpdate(events: List<Event>)
  }
}
