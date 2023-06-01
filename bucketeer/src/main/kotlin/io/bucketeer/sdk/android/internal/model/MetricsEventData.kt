package io.bucketeer.sdk.android.internal.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

sealed class MetricsEventData {

  @JsonClass(generateAdapter = true)
  data class LatencyMetricsEvent(
    val apiID: ApiID,
    val labels: Map<String, String> = emptyMap(),
    // in seconds
    val latencySecond: Double,
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.LatencyMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class SizeMetricsEvent(
    val apiID: ApiID,
    val labels: Map<String, String> = emptyMap(),
    val sizeByte: Int,
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.SizeMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class TimeoutErrorMetricsEvent(
    val apiID: ApiID,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.TimeoutErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class NetworkErrorMetricsEvent(
    val apiID: ApiID,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.NetworkErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class BadRequestErrorMetricsEvent(
    val apiID: ApiID,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.BadRequestErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class UnauthorizedErrorMetricsEvent(
    val apiID: ApiID,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.UnauthorizedErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class ForbiddenErrorMetricsEvent(
    val apiID: ApiID,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.ForbiddenErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class NotFoundErrorMetricsEvent(
    val apiID: ApiID,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.NotFoundErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class ClientClosedRequestErrorMetricsEvent(
    val apiID: ApiID,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.ClientClosedRequestErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class ServiceUnavailableErrorMetricsEvent(
    val apiID: ApiID,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.ServiceUnavailableErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class InternalSdkErrorMetricsEvent(
    val apiID: ApiID,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.InternalSdkErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class InternalServerErrorMetricsEvent(
    val apiID: ApiID,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.InternalServerErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class UnknownErrorMetricsEvent(
    val apiID: ApiID,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.UnknownErrorMetricsEvent",
  ) : MetricsEventData()
}
