package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.model.EventData
import io.bucketeer.sdk.android.internal.model.MetricsEventData
import io.bucketeer.sdk.android.internal.model.MetricsEventType
import java.lang.reflect.Type

class MetricsEventAdapterFactory : JsonAdapter.Factory {
  override fun create(
    type: Type,
    annotations: MutableSet<out Annotation>,
    moshi: Moshi,
  ): JsonAdapter<*>? {
    if (!Types.getRawType(type).isAssignableFrom(EventData.MetricsEvent::class.java)) {
      return null
    }

    return object : JsonAdapter<EventData.MetricsEvent>() {
      private val metricsEventTypeAdapter = moshi.adapter(MetricsEventType::class.java)
      private val latencyMetricsEventAdapter =
        moshi.adapter(MetricsEventData.LatencyMetricsEvent::class.java)
      private val sizeMetricsEventJsonAdapter =
        moshi.adapter(MetricsEventData.SizeMetricsEvent::class.java)
      private val timeoutErrorAdapter =
        moshi.adapter(MetricsEventData.TimeoutErrorMetricsEvent::class.java)
      private val internalSDKErrorAdapter =
        moshi.adapter(MetricsEventData.InternalSdkErrorMetricsEvent::class.java)
      private val internalServerErrorAdapter =
        moshi.adapter(MetricsEventData.InternalServerErrorMetricsEvent::class.java)
      private val unknownErrorAdapter =
        moshi.adapter(MetricsEventData.UnknownErrorMetricsEvent::class.java)
      private val networkErrorAdapter =
        moshi.adapter(MetricsEventData.NetworkErrorMetricsEvent::class.java)
      private val badRequestErrorEventAdapter =
        moshi.adapter(MetricsEventData.BadRequestErrorMetricsEvent::class.java)
      private val unauthorizedErrorAdapter =
        moshi.adapter(MetricsEventData.UnauthorizedErrorMetricsEvent::class.java)
      private val forbiddenErrorAdapter =
        moshi.adapter(MetricsEventData.ForbiddenErrorMetricsEvent::class.java)
      private val notFoundErrorAdapter =
        moshi.adapter(MetricsEventData.NotFoundErrorMetricsEvent::class.java)
      private val clientClosedRequestErrorAdapter =
        moshi.adapter(MetricsEventData.ClientClosedRequestErrorMetricsEvent::class.java)
      private val serviceUnavailableErrorAdapter =
        moshi.adapter(MetricsEventData.ServiceUnavailableErrorMetricsEvent::class.java)
      private val redirectRequestErrorAdapter =
        moshi.adapter(MetricsEventData.RedirectionRequestErrorMetricsEvent::class.java)
      private val payloadTooLargeErrorAdapter =
        moshi.adapter(MetricsEventData.PayloadTooLargeErrorMetricsEvent::class.java)

      private val metadataAdapter: JsonAdapter<Map<String, String>?> =
        moshi.adapter(
          Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            String::class.java,
          ),
          emptySet(),
          "metadata",
        )

      @Suppress("UNCHECKED_CAST")
      override fun fromJson(reader: JsonReader): EventData.MetricsEvent? {
        val jsonObj = reader.readJsonValue() as? Map<String, Any> ?: return null

        val eventType = metricsEventTypeAdapter.fromJsonValue(jsonObj["type"])

        val adapter = when (eventType) {
          MetricsEventType.RESPONSE_LATENCY -> latencyMetricsEventAdapter
          MetricsEventType.RESPONSE_SIZE -> sizeMetricsEventJsonAdapter
          MetricsEventType.TIMEOUT_ERROR -> timeoutErrorAdapter
          MetricsEventType.INTERNAL_SDK_ERROR -> internalSDKErrorAdapter
          MetricsEventType.UNKNOWN -> unknownErrorAdapter
          MetricsEventType.NETWORK_ERROR -> networkErrorAdapter
          MetricsEventType.BAD_REQUEST_ERROR -> badRequestErrorEventAdapter
          MetricsEventType.UNAUTHORIZED_ERROR -> unauthorizedErrorAdapter
          MetricsEventType.FORBIDDEN_ERROR -> forbiddenErrorAdapter
          MetricsEventType.NOT_FOUND_ERROR -> notFoundErrorAdapter
          MetricsEventType.CLIENT_CLOSED_REQUEST_ERROR -> clientClosedRequestErrorAdapter
          MetricsEventType.SERVICE_UNAVAILABLE_ERROR -> serviceUnavailableErrorAdapter
          MetricsEventType.INTERNAL_SERVER_ERROR -> internalServerErrorAdapter
          MetricsEventType.REDIRECT_REQUEST -> redirectRequestErrorAdapter
          MetricsEventType.PAYLOAD_TOO_LARGE -> payloadTooLargeErrorAdapter
          null -> throw BKTException.IllegalStateException("unexpected type: $type")
        }

        return EventData.MetricsEvent(
          timestamp = (jsonObj["timestamp"] as Double).toLong(),
          event = adapter.fromJsonValue(jsonObj["event"]) as MetricsEventData,
          type = eventType,
          sdkVersion = jsonObj["sdkVersion"]?.toString(),
          metadata = metadataAdapter.fromJsonValue(jsonObj["metadata"]),
        )
      }

      override fun toJson(writer: JsonWriter, value: EventData.MetricsEvent?) {
        if (value == null) {
          writer.nullValue()
          return
        }

        writer.beginObject()

        writer.name("timestamp")
        writer.jsonValue(value.timestamp)

        writer.name("type")
        writer.jsonValue(value.type.value)

        writer.name("event")

        when (value.type) {
          MetricsEventType.RESPONSE_LATENCY -> {
            latencyMetricsEventAdapter.toJson(
              writer,
              value.event as MetricsEventData.LatencyMetricsEvent,
            )
          }
          MetricsEventType.RESPONSE_SIZE -> {
            sizeMetricsEventJsonAdapter.toJson(
              writer,
              value.event as MetricsEventData.SizeMetricsEvent,
            )
          }
          MetricsEventType.TIMEOUT_ERROR -> {
            timeoutErrorAdapter.toJson(
              writer,
              value.event as MetricsEventData.TimeoutErrorMetricsEvent,
            )
          }
          MetricsEventType.INTERNAL_SDK_ERROR -> {
            internalSDKErrorAdapter.toJson(
              writer,
              value.event as MetricsEventData.InternalSdkErrorMetricsEvent,
            )
          }

          MetricsEventType.UNKNOWN -> {
            unknownErrorAdapter.toJson(
              writer,
              value.event as MetricsEventData.UnknownErrorMetricsEvent,
            )
          }
          MetricsEventType.NETWORK_ERROR -> {
            networkErrorAdapter.toJson(
              writer,
              value.event as MetricsEventData.NetworkErrorMetricsEvent,
            )
          }
          MetricsEventType.BAD_REQUEST_ERROR -> {
            badRequestErrorEventAdapter.toJson(
              writer,
              value.event as MetricsEventData.BadRequestErrorMetricsEvent,
            )
          }
          MetricsEventType.UNAUTHORIZED_ERROR -> {
            unauthorizedErrorAdapter.toJson(
              writer,
              value.event as MetricsEventData.UnauthorizedErrorMetricsEvent,
            )
          }
          MetricsEventType.FORBIDDEN_ERROR -> {
            forbiddenErrorAdapter.toJson(
              writer,
              value.event as MetricsEventData.ForbiddenErrorMetricsEvent,
            )
          }
          MetricsEventType.NOT_FOUND_ERROR -> {
            notFoundErrorAdapter.toJson(
              writer,
              value.event as MetricsEventData.NotFoundErrorMetricsEvent,
            )
          }
          MetricsEventType.CLIENT_CLOSED_REQUEST_ERROR -> {
            clientClosedRequestErrorAdapter.toJson(
              writer,
              value.event as MetricsEventData.ClientClosedRequestErrorMetricsEvent,
            )
          }
          MetricsEventType.SERVICE_UNAVAILABLE_ERROR -> {
            serviceUnavailableErrorAdapter.toJson(
              writer,
              value.event as MetricsEventData.ServiceUnavailableErrorMetricsEvent,
            )
          }
          MetricsEventType.INTERNAL_SERVER_ERROR -> {
            internalServerErrorAdapter.toJson(
              writer,
              value.event as MetricsEventData.InternalServerErrorMetricsEvent,
            )
          }

          MetricsEventType.REDIRECT_REQUEST -> {
            redirectRequestErrorAdapter.toJson(
              writer,
              value.event as MetricsEventData.RedirectionRequestErrorMetricsEvent,
            )
          }
          MetricsEventType.PAYLOAD_TOO_LARGE -> {
            payloadTooLargeErrorAdapter.toJson(
              writer,
              value.event as MetricsEventData.PayloadTooLargeErrorMetricsEvent,
            )
          }
        }

        if (value.sdkVersion != null) {
          writer.name("sdkVersion")
          writer.jsonValue(value.sdkVersion)
        }

        if (value.metadata != null) {
          writer.name("metadata")
          metadataAdapter.toJson(writer, value.metadata)
        }

        if (value.protobufType != null) {
          writer.name("@type")
          writer.jsonValue(value.protobufType)
        }

        writer.endObject()
      }
    }
  }
}
