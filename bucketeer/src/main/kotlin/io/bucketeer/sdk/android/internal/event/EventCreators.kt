package io.bucketeer.sdk.android.internal.event

import android.os.Build
import io.bucketeer.sdk.android.BuildConfig
import io.bucketeer.sdk.android.internal.Clock
import io.bucketeer.sdk.android.internal.IdGenerator
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
      feature_version = evaluation.featureVersion,
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

internal fun newGetEvaluationLatencyMetricsEvent(
  clock: Clock,
  idGenerator: IdGenerator,
  seconds: Long,
  featureTag: String,
  appVersion: String,
): Event {
  return Event(
    id = idGenerator.newId(),
    type = EventType.METRICS,
    event = EventData.MetricsEvent(
      timestamp = clock.currentTimeSeconds(),
      type = MetricsEventType.GET_EVALUATION_LATENCY,
      event = MetricsEventData.GetEvaluationLatencyMetricsEvent(
        labels = mapOf(
          "tag" to featureTag,
        ),
        duration = seconds,
      ),
      sdkVersion = BuildConfig.SDK_VERSION,
      metadata = newMetadata(appVersion),
    ),
  )
}

internal fun newGetEvaluationSizeMetricsEvent(
  clock: Clock,
  idGenerator: IdGenerator,
  sizeByte: Int,
  featureTag: String,
  appVersion: String,
): Event {
  return Event(
    id = idGenerator.newId(),
    type = EventType.METRICS,
    event = EventData.MetricsEvent(
      timestamp = clock.currentTimeSeconds(),
      type = MetricsEventType.GET_EVALUATION_SIZE,
      event = MetricsEventData.GetEvaluationSizeMetricsEvent(
        labels = mapOf(
          "tag" to featureTag,
        ),
        size_byte = sizeByte,
      ),
      sdkVersion = BuildConfig.SDK_VERSION,
      metadata = newMetadata(appVersion),
    ),
  )
}

internal fun newTimeoutErrorCountMetricsEvent(
  clock: Clock,
  idGenerator: IdGenerator,
  featureTag: String,
  appVersion: String,
): Event {
  return Event(
    id = idGenerator.newId(),
    type = EventType.METRICS,
    event = EventData.MetricsEvent(
      timestamp = clock.currentTimeSeconds(),
      type = MetricsEventType.TIMEOUT_ERROR_COUNT,
      event = MetricsEventData.TimeoutErrorCountMetricsEvent(
        tag = featureTag,
      ),
      sdkVersion = BuildConfig.SDK_VERSION,
      metadata = newMetadata(appVersion),
    ),
  )
}

internal fun newInternalErrorCountMetricsEvent(
  clock: Clock,
  idGenerator: IdGenerator,
  featureTag: String,
  appVersion: String,
): Event {
  return Event(
    id = idGenerator.newId(),
    type = EventType.METRICS,
    event = EventData.MetricsEvent(
      timestamp = clock.currentTimeSeconds(),
      type = MetricsEventType.INTERNAL_ERROR_COUNT,
      event = MetricsEventData.InternalErrorCountMetricsEvent(
        tag = featureTag,
      ),
      sdkVersion = BuildConfig.SDK_VERSION,
      metadata = newMetadata(appVersion),
    ),
  )
}
