package io.bucketeer.sdk.android.internal.model

enum class MetricsEventType(val value: Int) {
  UNKNOWN(0),
  RESPONSE_LATENCY(1),
  RESPONSE_SIZE(2),
  TIMEOUT_ERROR(3),
  NETWORK_ERROR(4),
  INTERNAL_SDK_ERROR(5),
  BAD_REQUEST_ERROR(6),
  UNAUTHORIZED_ERROR(7),
  FORBIDDEN_ERROR(8),
  NOT_FOUND_ERROR(9),
  CLIENT_CLOSED_REQUEST_ERROR(10),
  SERVICE_UNAVAILABLE_ERROR(11),
  INTERNAL_SERVER_ERROR(12),

  ;

  companion object {
    fun from(value: Int): MetricsEventType {
      return values().firstOrNull { it.value == value } ?: UNKNOWN
    }
  }
}
