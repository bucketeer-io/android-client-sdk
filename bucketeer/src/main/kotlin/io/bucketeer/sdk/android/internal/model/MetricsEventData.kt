package io.bucketeer.sdk.android.internal.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

sealed class MetricsEventData {
  @JsonClass(generateAdapter = true)
  data class LatencyMetricsEvent(
    override val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    // in seconds
    val latencySecond: Double,
    @Json(name = "@type")
    override val protobufType: String? = "type.googleapis.com/bucketeer.event.client.LatencyMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class SizeMetricsEvent(
    override val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    val sizeByte: Int,
    @Json(name = "@type")
    override val protobufType: String? = "type.googleapis.com/bucketeer.event.client.SizeMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class TimeoutErrorMetricsEvent(
    override val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    override val protobufType: String? = "type.googleapis.com/bucketeer.event.client.TimeoutErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class NetworkErrorMetricsEvent(
    override val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    override val protobufType: String? = "type.googleapis.com/bucketeer.event.client.NetworkErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class BadRequestErrorMetricsEvent(
    override val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    override val protobufType: String? = "type.googleapis.com/bucketeer.event.client.BadRequestErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class UnauthorizedErrorMetricsEvent(
    override val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    override val protobufType: String? = "type.googleapis.com/bucketeer.event.client.UnauthorizedErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class ForbiddenErrorMetricsEvent(
    override val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    override val protobufType: String? = "type.googleapis.com/bucketeer.event.client.ForbiddenErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class NotFoundErrorMetricsEvent(
    override val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    override val protobufType: String? = "type.googleapis.com/bucketeer.event.client.NotFoundErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class ClientClosedRequestErrorMetricsEvent(
    override val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    override val protobufType: String? = "type.googleapis.com/bucketeer.event.client.ClientClosedRequestErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class ServiceUnavailableErrorMetricsEvent(
    override val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    override val protobufType: String? = "type.googleapis.com/bucketeer.event.client.ServiceUnavailableErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class InternalSdkErrorMetricsEvent(
    override val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    override val protobufType: String? = "type.googleapis.com/bucketeer.event.client.InternalSdkErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class InternalServerErrorMetricsEvent(
    override val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    override val protobufType: String? = "type.googleapis.com/bucketeer.event.client.InternalServerErrorMetricsEvent",
  ) : MetricsEventData()

  @JsonClass(generateAdapter = true)
  data class UnknownErrorMetricsEvent(
    override val apiId: ApiId,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "@type")
    override val protobufType: String? = "type.googleapis.com/bucketeer.event.client.UnknownErrorMetricsEvent",
  ) : MetricsEventData()

  abstract val protobufType: String?
  abstract val apiId: ApiId
}
