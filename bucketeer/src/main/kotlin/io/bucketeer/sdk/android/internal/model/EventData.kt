package io.bucketeer.sdk.android.internal.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// we can't use codegen here
// see EventAdapterFactory
internal sealed class EventData {
  @JsonClass(generateAdapter = true)
  data class GoalEvent(
    val timestamp: Long,
    val goalId: String,
    val userId: String,
    val value: Double,
    val user: User,
    val tag: String,
    val sourceId: SourceId,
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
    val sourceId: SourceId,
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
    val sourceId: SourceId,
    val sdkVersion: String? = null,
    val metadata: Map<String, String>? = null,
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.MetricsEvent",
  ) : EventData() {
    // https://github.com/bucketeer-io/android-client-sdk/pull/68#discussion_r1222401661
    fun uniqueKey(): String = "${event.apiId}::${event.protobufType}"
  }
}
