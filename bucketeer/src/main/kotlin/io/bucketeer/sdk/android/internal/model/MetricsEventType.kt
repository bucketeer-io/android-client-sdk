package io.bucketeer.sdk.android.internal.model

enum class MetricsEventType(val value: Int) {
  //TODO: kenji will change it later
  //https://github.com/jinSasaki/bucketeer-ios-sdk/blob/ee4a38f0011c43c999c5964adf099404ed52b943/Bucketeer/Sources/Internal/Model/MetricsEventType.swift
  GET_EVALUATION_LATENCY(1),
  GET_EVALUATION_SIZE(2),
  TIMEOUT_ERROR_COUNT(3),
  INTERNAL_ERROR_COUNT(4),

  ;

  companion object {
    fun from(value: Int): MetricsEventType = values().first { it.value == value }
  }
}
