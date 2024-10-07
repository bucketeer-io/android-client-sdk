package io.bucketeer.sdk.android.internal.event

import android.os.Build
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.BuildConfig
import io.bucketeer.sdk.android.internal.Clock
import io.bucketeer.sdk.android.internal.IdGenerator
import io.bucketeer.sdk.android.internal.model.ApiId
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.EventData
import io.bucketeer.sdk.android.internal.model.EventType
import io.bucketeer.sdk.android.internal.model.MetricsEventData
import io.bucketeer.sdk.android.internal.model.MetricsEventType
import io.bucketeer.sdk.android.internal.model.Reason
import io.bucketeer.sdk.android.internal.model.ReasonType
import io.bucketeer.sdk.android.internal.model.SourceID
import io.bucketeer.sdk.android.internal.model.User

internal fun newMetadata(appVersion: String): Map<String, String> =
  mapOf(
    "app_version" to appVersion,
    "os_version" to Build.VERSION.SDK_INT.toString(),
    "device_model" to Build.DEVICE,
  )

internal fun newEvaluationEvent(
  clock: Clock,
  idGenerator: IdGenerator,
  featureTag: String,
  user: User,
  evaluation: Evaluation,
  appVersion: String,
): Event =
  Event(
    id = idGenerator.newId(),
    type = EventType.EVALUATION,
    event =
      EventData.EvaluationEvent(
        timestamp = clock.currentTimeSeconds(),
        featureId = evaluation.featureId,
        featureVersion = evaluation.featureVersion,
        userId = user.id,
        variationId = evaluation.variationId,
        user = user,
        reason = evaluation.reason,
        tag = featureTag,
        sourceId = SourceID.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
        metadata = newMetadata(appVersion),
      ),
  )

internal fun newDefaultEvaluationEvent(
  clock: Clock,
  idGenerator: IdGenerator,
  featureTag: String,
  user: User,
  featureId: String,
  appVersion: String,
): Event =
  Event(
    id = idGenerator.newId(),
    type = EventType.EVALUATION,
    event =
      EventData.EvaluationEvent(
        timestamp = clock.currentTimeSeconds(),
        featureId = featureId,
        userId = user.id,
        user = user,
        reason =
          Reason(
            type = ReasonType.CLIENT,
          ),
        tag = featureTag,
        sourceId = SourceID.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
        metadata = newMetadata(appVersion),
      ),
  )

internal fun newGoalEvent(
  clock: Clock,
  idGenerator: IdGenerator,
  goalId: String,
  value: Double,
  featureTag: String,
  user: User,
  appVersion: String,
): Event =
  Event(
    id = idGenerator.newId(),
    type = EventType.GOAL,
    event =
      EventData.GoalEvent(
        timestamp = clock.currentTimeSeconds(),
        goalId = goalId,
        userId = user.id,
        value = value,
        user = user,
        tag = featureTag,
        sourceId = SourceID.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
        metadata = newMetadata(appVersion),
      ),
  )

internal fun newSuccessMetricsEvents(
  clock: Clock,
  idGenerator: IdGenerator,
  featureTag: String,
  appVersion: String,
  apiId: ApiId,
  latencySecond: Double,
  sizeByte: Int,
): List<Event> {
  val labels = mapOf("tag" to featureTag)
  return listOf(
    Event(
      id = idGenerator.newId(),
      type = EventType.METRICS,
      event =
        EventData.MetricsEvent(
          timestamp = clock.currentTimeSeconds(),
          type = MetricsEventType.RESPONSE_LATENCY,
          event =
            MetricsEventData.LatencyMetricsEvent(
              apiId = apiId,
              labels = labels,
              latencySecond = latencySecond,
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = BuildConfig.SDK_VERSION,
          metadata = newMetadata(appVersion),
        ),
    ),
    Event(
      id = idGenerator.newId(),
      type = EventType.METRICS,
      event =
        EventData.MetricsEvent(
          timestamp = clock.currentTimeSeconds(),
          type = MetricsEventType.RESPONSE_SIZE,
          event =
            MetricsEventData.SizeMetricsEvent(
              apiId = apiId,
              labels = labels,
              sizeByte = sizeByte,
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = BuildConfig.SDK_VERSION,
          metadata = newMetadata(appVersion),
        ),
    ),
  )
}

internal fun newErrorMetricsEvent(
  clock: Clock,
  idGenerator: IdGenerator,
  featureTag: String,
  appVersion: String,
  error: BKTException,
  apiId: ApiId,
): Event =
  Event(
    id = idGenerator.newId(),
    type = EventType.METRICS,
    event =
      newEventDataMetricEvent(
        error,
        clock.currentTimeSeconds(),
        featureTag,
        appVersion,
        apiId,
      ),
  )

internal fun newEventDataMetricEvent(
  error: BKTException,
  timestamp: Long,
  featureTag: String,
  appVersion: String,
  apiId: ApiId,
): EventData.MetricsEvent {
  val labels = mutableMapOf("tag" to featureTag)
  val (type, event) =
    when (error) {
      is BKTException.BadRequestException ->
        Pair(
          MetricsEventType.BAD_REQUEST_ERROR,
          MetricsEventData.BadRequestErrorMetricsEvent(
            apiId = apiId,
            labels = labels,
          ),
        )

      is BKTException.ClientClosedRequestException ->
        Pair(
          MetricsEventType.CLIENT_CLOSED_REQUEST_ERROR,
          MetricsEventData.ClientClosedRequestErrorMetricsEvent(
            apiId = apiId,
            labels = labels,
          ),
        )

      is BKTException.FeatureNotFoundException ->
        Pair(
          MetricsEventType.NOT_FOUND_ERROR,
          MetricsEventData.NotFoundErrorMetricsEvent(
            apiId = apiId,
            labels = labels,
          ),
        )

      is BKTException.ForbiddenException ->
        Pair(
          MetricsEventType.FORBIDDEN_ERROR,
          MetricsEventData.ForbiddenErrorMetricsEvent(
            apiId = apiId,
            labels = labels,
          ),
        )

      is BKTException.IllegalArgumentException,
      is BKTException.IllegalStateException,
      is BKTException.InvalidHttpMethodException,
      ->
        Pair(
          MetricsEventType.INTERNAL_SDK_ERROR,
          MetricsEventData.InternalSdkErrorMetricsEvent(
            apiId = apiId,
            labels = labels,
          ),
        )

      is BKTException.InternalServerErrorException ->
        Pair(
          MetricsEventType.INTERNAL_SERVER_ERROR,
          MetricsEventData.InternalServerErrorMetricsEvent(
            apiId = apiId,
            labels = labels,
          ),
        )

      is BKTException.NetworkException ->
        Pair(
          MetricsEventType.NETWORK_ERROR,
          MetricsEventData.NetworkErrorMetricsEvent(
            apiId = apiId,
            labels = labels,
          ),
        )

      is BKTException.ServiceUnavailableException ->
        Pair(
          MetricsEventType.SERVICE_UNAVAILABLE_ERROR,
          MetricsEventData.ServiceUnavailableErrorMetricsEvent(
            apiId = apiId,
            labels = labels,
          ),
        )

      is BKTException.TimeoutException ->
        Pair(
          MetricsEventType.TIMEOUT_ERROR,
          MetricsEventData.TimeoutErrorMetricsEvent(
            apiId = apiId,
            labels =
              labels.apply {
                // https://github.com/bucketeer-io/android-client-sdk/issues/81
                set("timeout", error.timeoutMillis.toStringInDoubleFormat())
              },
          ),
        )

      is BKTException.UnauthorizedException ->
        Pair(
          MetricsEventType.UNAUTHORIZED_ERROR,
          MetricsEventData.UnauthorizedErrorMetricsEvent(
            apiId = apiId,
            labels = labels,
          ),
        )

      is BKTException.UnknownException ->
        Pair(
          MetricsEventType.UNKNOWN,
          MetricsEventData.UnknownErrorMetricsEvent(
            apiId = apiId,
            labels =
              labels.apply {
                set("error_message", error.message ?: "UnknownException")
              },
          ),
        )

      is BKTException.UnknownServerException ->
        Pair(
          MetricsEventType.UNKNOWN,
          MetricsEventData.UnknownErrorMetricsEvent(
            apiId = apiId,
            labels =
              labels.apply {
                set("response_code", error.statusCode.toString())
                set("error_message", error.message ?: "UnknownServerException")
              },
          ),
        )

      is BKTException.PayloadTooLargeException ->
        Pair(
          MetricsEventType.PAYLOAD_TOO_LARGE,
          MetricsEventData.PayloadTooLargeErrorMetricsEvent(
            apiId = apiId,
            labels = labels,
          ),
        )

      is BKTException.RedirectRequestException ->
        Pair(
          MetricsEventType.REDIRECT_REQUEST,
          MetricsEventData.RedirectionRequestErrorMetricsEvent(
            apiId = apiId,
            labels =
              labels.apply {
                set("response_code", error.statusCode.toString())
              },
          ),
        )
    }
  return EventData.MetricsEvent(
    timestamp = timestamp,
    type = type,
    event = event,
    sourceId = SourceID.ANDROID,
    sdkVersion = BuildConfig.SDK_VERSION,
    metadata = newMetadata(appVersion),
  )
}

internal fun Long.toStringInDoubleFormat(): String = (this / 1000.0).toString()
