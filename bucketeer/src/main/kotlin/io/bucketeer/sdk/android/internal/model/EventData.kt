package io.bucketeer.sdk.android.internal.model

import androidx.annotation.VisibleForTesting
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.bucketeer.sdk.android.BKTException

// we can't use codegen here
// see EventAdapterFactory
sealed class EventData {
  @JsonClass(generateAdapter = true)
  data class GoalEvent(
    val timestamp: Long,
    val goalId: String,
    val userId: String,
    val value: Double,
    val user: User,
    val tag: String,
    val sourceId: SourceID,
    val sdkVersion: String? = null,
    val metadata: Map<String, String>? = null,
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.GoalEvent",
  ) : EventData()

  @JsonClass(generateAdapter = true)
  data class EvaluationEvent(
    val timestamp: Long,
    val featureId: String,
    val featureVersion: Int = 0,
    val userId: String,
    val variationId: String = "",
    val user: User,
    val reason: Reason,
    val tag: String,
    val sourceId: SourceID,
    val sdkVersion: String? = null,
    val metadata: Map<String, String>? = null,
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.EvaluationEvent",
  ) : EventData()

  // we can't use codegen here
  // see MetricsEventAdapterFactory
  data class MetricsEvent(
    val timestamp: Long,
    val event: MetricsEventData,
    val type: MetricsEventType,
    val sdkVersion: String? = null,
    val metadata: Map<String, String>? = null,
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.MetricsEvent",
  ) : EventData() {
    // https://github.com/bucketeer-io/android-client-sdk/pull/68#discussion_r1222401661
    fun uniqueKey() : String {
      return "${event.apiId}::${event.protobufType}"
    }
  }
}


@VisibleForTesting
internal fun Event.metricsEventUniqueKey() : String {
  if (type == EventType.METRICS && event is EventData.MetricsEvent) {
    return event.uniqueKey()
  }
  throw BKTException.IllegalStateException("expected `MetricsEvent` but the input is not")
}
