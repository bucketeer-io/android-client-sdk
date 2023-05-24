package io.bucketeer.sdk.android.internal.model

enum class MetricsEventType(val value: Int) {
  //https://github.com/jinSasaki/bucketeer-ios-sdk/blob/ee4a38f0011c43c999c5964adf099404ed52b943/Bucketeer/Sources/Internal/Model/MetricsEventType.swift
  GET_EVALUATION_LATENCY(1),
  GET_EVALUATION_SIZE(2),
//@deprecated  TIMEOUT_ERROR_COUNT(3),
//@deprecated  INTERNAL_ERROR_COUNT(4),
  TIMEOUT_ERROR(5),
  NETWORK_ERROR(6),
  INTERNAL_ERROR(7),

  ;

  companion object {
    fun from(value: Int): MetricsEventType = values().first { it.value == value }
  }
}
