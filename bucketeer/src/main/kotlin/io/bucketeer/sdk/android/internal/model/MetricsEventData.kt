package io.bucketeer.sdk.android.internal.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

sealed class MetricsEventData {

  @JsonClass(generateAdapter = true)
  data class LatencyMetricsEvent(
    val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    // in seconds
    val latencySecond: Double,
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.LatencyMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class SizeMetricsEvent(
    val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    val sizeByte: Int,
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.SizeMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class TimeoutErrorMetricsEvent(
    val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.TimeoutErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class NetworkErrorMetricsEvent(
    val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.NetworkErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class BadRequestErrorMetricsEvent(
    val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.BadRequestErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class UnauthorizedErrorMetricsEvent(
    val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.UnauthorizedErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class ForbiddenErrorMetricsEvent(
    val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.ForbiddenErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class NotFoundErrorMetricsEvent(
    val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.NotFoundErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class ClientClosedRequestErrorMetricsEvent(
    val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.ClientClosedRequestErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class ServiceUnavailableErrorMetricsEvent(
    val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.ServiceUnavailableErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class InternalSdkErrorMetricsEvent(
    val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.InternalSdkErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class InternalServerErrorMetricsEvent(
    val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.InternalServerErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class UnknownErrorMetricsEvent(
    val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    val protobufType: String? = "type.googleapis.com/bucketeer.event.client.UnknownErrorMetricsEvent",
  ) : MetricsEventData()
}
