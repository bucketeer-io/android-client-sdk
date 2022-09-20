package io.bucketeer.sdk.android.internal.model

enum class MetricsEventType(val value: Int) {
  GET_EVALUATION_LATENCY(1),
  GET_EVALUATION_SIZE(2),
  TIMEOUT_ERROR_COUNT(3),
  INTERNAL_ERROR_COUNT(4),

  ;

  companion object {
    fun from(value: Int): MetricsEventType = values().first { it.value == value }
  }
}
