package io.bucketeer.sdk.android.internal.model

import com.squareup.moshi.JsonClass

// we can't use codegen here
// see EventAdapterFactory
sealed class EventData {

  @JsonClass(generateAdapter = true)
  data class GoalEvent(
    val timestamp: Long,
    val goal_id: String,
    val user_id: String,
    val value: Double,
    val user: User,
    val tag: String,
    val source_id: SourceID,
  ) : EventData()

  @JsonClass(generateAdapter = true)
  data class EvaluationEvent(
    val timestamp: Long,
    val feature_id: String,
    val feature_version: Int = 0,
    val user_id: String,
    val variation_id: String = "",
    val user: User,
    val reason: Reason,
    val tag: String,
    val source_id: SourceID,
  ) : EventData()

  // we can't use codegen here
  // see MetricsEventAdapterFactory
  data class MetricsEvent(
    val timestamp: Long,
    val event: MetricsEventData,
    val type: MetricsEventType,
  ) : EventData()
}
