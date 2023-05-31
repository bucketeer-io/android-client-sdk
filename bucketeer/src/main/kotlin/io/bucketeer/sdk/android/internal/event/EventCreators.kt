package io.bucketeer.sdk.android.internal.event

import android.os.Build
import io.bucketeer.sdk.android.BuildConfig
import io.bucketeer.sdk.android.internal.Clock
import io.bucketeer.sdk.android.internal.IdGenerator
import io.bucketeer.sdk.android.internal.model.ApiID
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

internal fun newMetadata(appVersion: String): Map<String, String> {
  return mapOf(
    "app_version" to appVersion,
    "os_version" to Build.VERSION.SDK_INT.toString(),
    "device_model" to Build.DEVICE,
  )
}

internal fun newEvaluationEvent(
  clock: Clock,
  idGenerator: IdGenerator,
  featureTag: String,
  user: User,
  evaluation: Evaluation,
  appVersion: String,
): Event {
  return Event(
    id = idGenerator.newId(),
    type = EventType.EVALUATION,
    event = EventData.EvaluationEvent(
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
}

internal fun newDefaultEvaluationEvent(
  clock: Clock,
  idGenerator: IdGenerator,
  featureTag: String,
  user: User,
  featureId: String,
  appVersion: String,
): Event {
  return Event(
    id = idGenerator.newId(),
    type = EventType.EVALUATION,
    event = EventData.EvaluationEvent(
      timestamp = clock.currentTimeSeconds(),
      featureId = featureId,
      userId = user.id,
      user = user,
      reason = Reason(
        type = ReasonType.CLIENT,
      ),
      tag = featureTag,
      sourceId = SourceID.ANDROID,
      sdkVersion = BuildConfig.SDK_VERSION,
      metadata = newMetadata(appVersion),
    ),
  )
}

internal fun newGoalEvent(
  clock: Clock,
  idGenerator: IdGenerator,
  goalId: String,
  value: Double,
  featureTag: String,
  user: User,
  appVersion: String,
): Event {
  return Event(
    id = idGenerator.newId(),
    type = EventType.GOAL,
    event = EventData.GoalEvent(
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
}

internal fun newMetricsEvent(
  clock: Clock,
  idGenerator: IdGenerator,
  featureTag: String?,
  appVersion: String,
  metricsEventType: MetricsEventType,
  apiID: ApiID,
  // note: only available on success request
  latencySecond: Long? = null,
  // note: only available on success request
  sizeByte: Int? = null,
): Event {
  return Event(
    id = idGenerator.newId(),
    type = EventType.METRICS,
    event = EventData.MetricsEvent(
      timestamp = clock.currentTimeSeconds(),
      type = metricsEventType,
      event = newMetricsEventData(featureTag, apiID, metricsEventType, latencySecond, sizeByte),
      sdkVersion = BuildConfig.SDK_VERSION,
      metadata = newMetadata(appVersion),
    ),
  )
}
internal fun newMetricsEventData(
  featureTag: String?,
  apiID: ApiID,
  type: MetricsEventType,
  // note: only available on success request
  latencySecond: Long? = null,
  // note: only available on success request
  sizeByte: Int? = null,
): MetricsEventData {
  // note: featureTag only available from `GET_EVALUATIONS`
  val labels = if (featureTag != null) mapOf("tag" to featureTag) else mapOf()

  return when (type) {
    MetricsEventType.UNKNOWN -> MetricsEventData.UnknownErrorMetricsEvent(
      apiID = apiID,
      labels = labels,
    )

    MetricsEventType.RESPONSE_LATENCY -> {
      if (latencySecond == null) {
        // note: this exception for SDK developer only, must not throw to the user of the SDK.
        throw Exception("Create LatencyMetricsEvent fail because missing `latency`")
      }
      MetricsEventData.LatencyMetricsEvent(
        apiID = apiID,
        labels = labels,
        latencySecond = latencySecond.toDouble(),
      )
    }

    MetricsEventType.RESPONSE_SIZE -> {
      if (sizeByte == null) {
        // note: this exception for SDK developer only, must not throw to the user of the SDK.
        throw Exception("Create SizeMetricsEvent fail because missing `sizeByte`")
      }
      MetricsEventData.SizeMetricsEvent(
        apiID = apiID,
        labels = labels,
        sizeByte = sizeByte,
      )
    }

    MetricsEventType.TIMEOUT_ERROR -> MetricsEventData.TimeoutErrorMetricsEvent(
      apiID = apiID,
      labels = labels,
    )

    MetricsEventType.NETWORK_ERROR -> MetricsEventData.NetworkErrorMetricsEvent(
      apiID = apiID,
      labels = labels,
    )

    MetricsEventType.INTERNAL_ERROR -> MetricsEventData.InternalSdkErrorMetricsEvent(
      apiID = apiID,
      labels = labels,
    )

    MetricsEventType.BAD_REQUEST_ERROR -> MetricsEventData.BadRequestErrorMetricsEvent(
      apiID = apiID,
      labels = labels,
    )

    MetricsEventType.UNAUTHORIZED_ERROR -> MetricsEventData.UnauthorizedErrorMetricsEvent(
      apiID = apiID,
      labels = labels,
    )

    MetricsEventType.FORBIDDEN_ERROR -> MetricsEventData.ForbiddenErrorMetricsEvent(
      apiID = apiID,
      labels = labels,
    )

    MetricsEventType.NOT_FOUND_ERROR -> MetricsEventData.NotFoundErrorMetricsEvent(
      apiID = apiID,
      labels = labels,
    )

    MetricsEventType.CLIENT_CLOSED_REQUEST_ERROR -> MetricsEventData.ClientClosedRequestErrorMetricsEvent(
      apiID = apiID,
      labels = labels,
    )

    MetricsEventType.SERVICE_UNAVAILABLE_ERROR -> MetricsEventData.ServiceUnavailableErrorMetricsEvent(
      apiID = apiID,
      labels = labels,
    )

    MetricsEventType.INTERNAL_SERVER_ERROR -> MetricsEventData.InternalServerErrorMetricsEvent(
      apiID = apiID,
      labels = labels,
    )
  }
}
