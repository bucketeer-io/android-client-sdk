package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.EventData
import io.bucketeer.sdk.android.internal.model.EventType
import java.lang.reflect.Type

class EventAdapterFactory : JsonAdapter.Factory {
  override fun create(
    type: Type,
    annotations: MutableSet<out Annotation>,
    moshi: Moshi,
  ): JsonAdapter<Event>? {
    if (!Types.getRawType(type).isAssignableFrom(Event::class.java)) {
      return null
    }

    return object : JsonAdapter<Event>() {
      private val eventTypeAdapter = moshi.adapter(EventType::class.java)
      private val goalEventAdapter = moshi.adapter(EventData.GoalEvent::class.java)
      private val evaluationAdapter = moshi.adapter(EventData.EvaluationEvent::class.java)
      private val metricsEventAdapter = moshi.adapter(EventData.MetricsEvent::class.java)

      @Suppress("UNCHECKED_CAST")
      override fun fromJson(reader: JsonReader): Event? {
        val jsonObj = reader.readJsonValue() as? Map<String, Any> ?: return null

        val eventType = eventTypeAdapter.fromJsonValue(jsonObj["type"])

        val adapter = when (eventType) {
          EventType.EVALUATION -> evaluationAdapter
          EventType.GOAL -> goalEventAdapter
          EventType.METRICS -> metricsEventAdapter
          else -> throw BKTException.IllegalStateException("unexpected type: $type")
        }

        return Event(
          id = jsonObj["id"] as String,
          event = adapter.fromJsonValue(jsonObj["event"]) as EventData,
          type = eventType,
        )
      }

      override fun toJson(writer: JsonWriter, value: Event?) {
        if (value == null) {
          writer.nullValue()
          return
        }

        writer.beginObject()

        writer.name("id")
        writer.jsonValue(value.id)

        writer.name("type")
        writer.jsonValue(value.type.value)

        writer.name("event")

        when (value.type) {
          EventType.EVALUATION -> {
            evaluationAdapter.toJson(writer, value.event as EventData.EvaluationEvent)
          }
          EventType.GOAL -> {
            goalEventAdapter.toJson(writer, value.event as EventData.GoalEvent)
          }
          EventType.METRICS -> {
            metricsEventAdapter.toJson(writer, value.event as EventData.MetricsEvent)
          }
          else -> throw BKTException.IllegalStateException("unexpected type: $type")
        }

        writer.endObject()
      }
    }
  }
}
