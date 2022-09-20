package io.bucketeer.sdk.android.internal.model

import com.squareup.moshi.JsonClass

sealed class MetricsEventData {

  @JsonClass(generateAdapter = true)
  data class GetEvaluationLatencyMetricsEvent(
    val labels: Map<String, String> = emptyMap(),
    // in seconds
    val duration: Long,
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class GetEvaluationSizeMetricsEvent(
    val labels: Map<String, String> = emptyMap(),
    val size_byte: Int,
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class TimeoutErrorCountMetricsEvent(
    val tag: String,
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class InternalErrorCountMetricsEvent(
    val tag: String,
  ) : MetricsEventData()
}
