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
      private val getEvaluationLatencyAdapter =
        moshi.adapter(MetricsEventData.GetEvaluationLatencyMetricsEvent::class.java)
      private val getEvaluationSizeAdapter =
        moshi.adapter(MetricsEventData.GetEvaluationSizeMetricsEvent::class.java)
      private val timeoutErrorCountAdapter =
        moshi.adapter(MetricsEventData.TimeoutErrorCountMetricsEvent::class.java)
      private val internalErrorCountAdapter =
        moshi.adapter(MetricsEventData.InternalErrorCountMetricsEvent::class.java)

      @Suppress("UNCHECKED_CAST")
      override fun fromJson(reader: JsonReader): EventData.MetricsEvent? {
        val jsonObj = reader.readJsonValue() as? Map<String, Any> ?: return null

        val eventType = metricsEventTypeAdapter.fromJsonValue(jsonObj["type"])

        val adapter = when (eventType) {
          MetricsEventType.GET_EVALUATION_LATENCY -> getEvaluationLatencyAdapter
          MetricsEventType.GET_EVALUATION_SIZE -> getEvaluationSizeAdapter
          MetricsEventType.TIMEOUT_ERROR_COUNT -> timeoutErrorCountAdapter
          MetricsEventType.INTERNAL_ERROR_COUNT -> internalErrorCountAdapter
          null -> throw BKTException.IllegalStateException("unexpected type: $type")
        }

        return EventData.MetricsEvent(
          timestamp = (jsonObj["timestamp"] as Double).toLong(),
          event = adapter.fromJsonValue(jsonObj["event"]) as MetricsEventData,
          type = eventType,
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
          MetricsEventType.GET_EVALUATION_LATENCY -> {
            getEvaluationLatencyAdapter.toJson(
              writer,
              value.event as MetricsEventData.GetEvaluationLatencyMetricsEvent,
            )
          }
          MetricsEventType.GET_EVALUATION_SIZE -> {
            getEvaluationSizeAdapter.toJson(
              writer,
              value.event as MetricsEventData.GetEvaluationSizeMetricsEvent,
            )
          }
          MetricsEventType.TIMEOUT_ERROR_COUNT -> {
            timeoutErrorCountAdapter.toJson(
              writer,
              value.event as MetricsEventData.TimeoutErrorCountMetricsEvent,
            )
          }
          MetricsEventType.INTERNAL_ERROR_COUNT -> {
            internalErrorCountAdapter.toJson(
              writer,
              value.event as MetricsEventData.InternalErrorCountMetricsEvent,
            )
          }
        }

        writer.endObject()
      }
    }
  }
}
