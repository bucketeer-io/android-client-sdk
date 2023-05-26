package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.bucketeer.sdk.android.internal.model.MetricsEventData
import java.lang.reflect.Type
import com.squareup.moshi.internal.Util

class GetEvaluationLatencyMetricsEventAdapterFactory : JsonAdapter.Factory {

  override fun create(
    type: Type,
    annotations: MutableSet<out Annotation>,
    moshi: Moshi,
  ): JsonAdapter<MetricsEventData.GetEvaluationLatencyMetricsEvent>? {
    if (!Types.getRawType(type).isAssignableFrom(MetricsEventData.GetEvaluationLatencyMetricsEvent::class.java)) {
      return null
    }

     return object : JsonAdapter<MetricsEventData.GetEvaluationLatencyMetricsEvent>() {

      private val options: JsonReader.Options = JsonReader.Options.of("labels", "duration")

      private val mapOfStringStringAdapter: JsonAdapter<Map<String, String>> =
        moshi.adapter(Types.newParameterizedType(Map::class.java, String::class.java,
          String::class.java), emptySet(), "labels")

      private val durationAdapter: JsonAdapter<String?> = moshi.adapter(String::class.java, emptySet(),
        "duration")

      override fun fromJson(reader: JsonReader): MetricsEventData.GetEvaluationLatencyMetricsEvent? {
        var labels: Map<String, String>? = null
        var duration: Long? = null
        reader.beginObject()
        while (reader.hasNext()) {
          when (reader.selectName(options)) {
            0 -> {
              labels = mapOfStringStringAdapter.fromJson(reader) ?: throw Util.unexpectedNull("labels",
                "labels", reader)
            }
            1 -> {
              duration = durationAdapter.fromJson(reader)?.protobufDurationToLong() ?: throw Util.unexpectedNull("duration",
                "duration", reader)
            }
            -1 -> {
              // Unknown name, skip it.
              reader.skipName()
              reader.skipValue()
            }
          }
        }
        reader.endObject()
        return  MetricsEventData.GetEvaluationLatencyMetricsEvent(
          labels = labels as Map<String, String>,
          duration = duration ?: throw Util.missingProperty("duration", "duration", reader),
        )
      }

      override fun toJson(writer: JsonWriter, value: MetricsEventData.GetEvaluationLatencyMetricsEvent?) {
        if (value == null) {
          writer.nullValue()
          return
        }
        writer.beginObject()
        writer.name("labels")
        writer.jsonValue(value.labels)
        writer.name("duration")
        // Kenji : because duration is in `second`, we are safe to do that
        // `GetEvaluationLatencyMetricsEvent` were deprecated in backend
        // Convert Long to the protobuf `Duration` format
        writer.jsonValue( "${value.duration}s")
        writer.name("@type")
        writer.jsonValue( value.protobufType)
        writer.endObject()
      }
    }
  }
}

fun String.protobufDurationToLong() : Long {
  // Kenji : because duration is in `second`, we are safe to do that
  // `GetEvaluationLatencyMetricsEvent` were deprecated in backend
  // We could use google.protobuf lib but I don't want add more dependency to our lib.
  val durationProto = this.removeSuffix("s")
  return try {
    durationProto.toLong()
  } catch (ex : NumberFormatException) {
    0L
  }
}
