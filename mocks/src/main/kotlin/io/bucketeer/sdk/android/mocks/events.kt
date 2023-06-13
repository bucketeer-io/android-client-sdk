@file:Suppress("ktlint:filename")

package io.bucketeer.sdk.android.mocks

import io.bucketeer.sdk.android.internal.model.ApiId
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.EventData
import io.bucketeer.sdk.android.internal.model.EventType
import io.bucketeer.sdk.android.internal.model.MetricsEventData
import io.bucketeer.sdk.android.internal.model.MetricsEventType
import io.bucketeer.sdk.android.internal.model.Reason
import io.bucketeer.sdk.android.internal.model.ReasonType
import io.bucketeer.sdk.android.internal.model.SourceID

val evaluationEvent1: Event by lazy {
  Event(
    id = "5ce0ae1a-8568-44d3-961b-89d7735f2a93",
    type = EventType.EVALUATION,
    event = EventData.EvaluationEvent(
      timestamp = 1661780821,
      featureId = "evaluation1",
      userId = user1.id,
      user = user1,
      reason = Reason(type = ReasonType.DEFAULT),
      tag = "",
      sourceId = SourceID.ANDROID,
      sdkVersion = io.bucketeer.sdk.android.BuildConfig.SDK_VERSION,
    ),
  )
}

val evaluationEvent2: Event by lazy {
  Event(
    id = "62d76a53-3396-4dfb-8dce-dd1b794a984d",
    type = EventType.EVALUATION,
    event = EventData.EvaluationEvent(
      timestamp = 1661780821,
      featureId = "evaluation2",
      userId = user1.id,
      user = user1,
      reason = Reason(type = ReasonType.DEFAULT),
      tag = "",
      sourceId = SourceID.ANDROID,
      sdkVersion = io.bucketeer.sdk.android.BuildConfig.SDK_VERSION,
      metadata = mapOf(
        "app_version" to "1.2.3",
        "os_version" to "os_version_value",
        "device_model" to "device_model_value",
      ),
    ),
  )
}

val goalEvent1: Event by lazy {
  Event(
    id = "408741bd-ae4c-45e9-888d-a85e88817fec",
    type = EventType.GOAL,
    event = EventData.GoalEvent(
      timestamp = 1661780821,
      goalId = "goal1",
      userId = user1.id,
      user = user1,
      value = 0.0,
      tag = "",
      sourceId = SourceID.ANDROID,
      sdkVersion = io.bucketeer.sdk.android.BuildConfig.SDK_VERSION,
      metadata = mapOf(
        "app_version" to "1.2.3",
        "os_version" to "os_version_value",
        "device_model" to "device_model_value",
      ),
    ),
  )
}

val duplicateGoalEvent1: Event by lazy {
  Event(
    id = "408741bd-ae4c-45e9-888d-a85e88817fdd",
    type = EventType.GOAL,
    event = EventData.GoalEvent(
      timestamp = 1661780821,
      goalId = "goal1",
      userId = user1.id,
      user = user1,
      value = 0.0,
      tag = "",
      sourceId = SourceID.ANDROID,
      sdkVersion = io.bucketeer.sdk.android.BuildConfig.SDK_VERSION,
      metadata = mapOf(
        "app_version" to "1.2.3",
        "os_version" to "os_version_value",
        "device_model" to "device_model_value",
      ),
    ),
  )
}

val goalEvent2: Event by lazy {
  Event(
    id = "5ea231b4-c3c7-4b9f-97a2-ee50337f51f0",
    type = EventType.GOAL,
    event = EventData.GoalEvent(
      timestamp = 1661780821,
      goalId = "goal2",
      userId = user1.id,
      user = user1,
      value = 0.0,
      tag = "",
      sourceId = SourceID.ANDROID,
      sdkVersion = io.bucketeer.sdk.android.BuildConfig.SDK_VERSION,
      metadata = mapOf(
        "app_version" to "1.2.3",
        "os_version" to "os_version_value",
        "device_model" to "device_model_value",
      ),
    ),
  )
}

val latencyMetricsEvent1: Event by lazy {
  Event(
    id = "e1c03cae-367d-4be4-a613-759441a37801",
    type = EventType.METRICS,
    event = EventData.MetricsEvent(
      timestamp = 1661823274, // 2022-08-30 01:34:34
      event = getEvaluationLatencyMetricsEvent1,
      type = MetricsEventType.RESPONSE_LATENCY,
      sdkVersion = io.bucketeer.sdk.android.BuildConfig.SDK_VERSION,
      metadata = mapOf(
        "app_version" to "1.2.3",
        "os_version" to "os_version_value",
        "device_model" to "device_model_value",
      ),
    ),
  )
}

val getEvaluationLatencyMetricsEvent1 = MetricsEventData.LatencyMetricsEvent(
  ApiId.GET_EVALUATIONS,
  labels = mapOf("tag" to "android", "state" to "FULL"),
  latencySecond = 2000.0,
)

// Will duplicate with `metricsEvent1`
val duplicateLatencyMetricsEvent1: Event by lazy {
  Event(
    id = "bbc03cae-367d-4be4-a613-759441a378aa",
    type = EventType.METRICS,
    event = EventData.MetricsEvent(
      timestamp = 1661823274, // 2022-08-30 01:34:34
      event = getEvaluationLatencyMetricsEvent1,
      type = MetricsEventType.RESPONSE_LATENCY,
      sdkVersion = io.bucketeer.sdk.android.BuildConfig.SDK_VERSION,
      metadata = mapOf(
        "app_version" to "1.2.3",
        "os_version" to "os_version_value",
        "device_model" to "device_model_value",
      ),
    ),
  )
}

val sizeMetricsEvent1: Event by lazy {
  Event(
    id = "aac03cae-367d-4be4-a613-759441a37820",
    type = EventType.METRICS,
    event = EventData.MetricsEvent(
      timestamp = 1661823275,
      event = getEvaluationSizeMetricsEvent1,
      type = MetricsEventType.RESPONSE_SIZE,
      sdkVersion = io.bucketeer.sdk.android.BuildConfig.SDK_VERSION,
      metadata = mapOf(
        "app_version" to "1.2.3",
        "os_version" to "os_version_value",
        "device_model" to "device_model_value",
      ),
    ),
  )
}

val getEvaluationSizeMetricsEvent1 = MetricsEventData.SizeMetricsEvent(
  ApiId.GET_EVALUATIONS,
  labels = mapOf("tag" to "android", "state" to "FULL"),
  sizeByte = 400,
)

val internalErrorMetricsEvent1: Event by lazy {
  Event(
    id = "aac03cae-367d-4be4-a613-759441a37820",
    type = EventType.METRICS,
    event = EventData.MetricsEvent(
      timestamp = 1661823275,
      event = MetricsEventData.InternalServerErrorMetricsEvent(
        ApiId.GET_EVALUATIONS,
        labels = mapOf("tag" to "android", "state" to "FULL"),
      ),
      type = MetricsEventType.INTERNAL_SERVER_ERROR,
      sdkVersion = io.bucketeer.sdk.android.BuildConfig.SDK_VERSION,
      metadata = mapOf(
        "app_version" to "1.2.3",
        "os_version" to "os_version_value",
        "device_model" to "device_model_value",
      ),
    ),
  )
}
