package io.bucketeer.sdk.android

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

sealed class BKTValue {
  fun asString(): kotlin.String? = if (this is String) string else null

  fun asBoolean(): kotlin.Boolean? = if (this is Boolean) boolean else null

  fun asInteger(): Int? = if (this is Number) number.toInt() else null

  fun asDouble(): Double? = if (this is Number) number else null

  fun asList(): kotlin.collections.List<BKTValue>? = if (this is List) list else null

  fun asStructure(): Map<kotlin.String, BKTValue>? = if (this is Structure) structure else null

  fun isNull(): kotlin.Boolean = this is Null

  data class String(
    val string: kotlin.String,
  ) : BKTValue()

  data class Boolean(
    val boolean: kotlin.Boolean,
  ) : BKTValue()

  data class Number(
    val number: Double,
  ) : BKTValue()

  data class Structure(
    val structure: Map<kotlin.String, BKTValue>,
  ) : BKTValue()

  data class List(
    val list: kotlin.collections.List<BKTValue>,
  ) : BKTValue()

  data object Null : BKTValue()
}

internal class BKTValueAdapter : JsonAdapter<BKTValue>() {
  @ToJson
  override fun toJson(
    writer: JsonWriter,
    value: BKTValue?,
  ) {
    when (value) {
      is BKTValue.String -> writer.value(value.string)
      is BKTValue.Boolean -> writer.value(value.boolean)
      is BKTValue.Number -> writer.value(value.number)
      is BKTValue.Structure -> {
        writer.beginObject()
        value.structure.forEach { (k, v) ->
          writer.name(k)
          toJson(writer, v)
        }
        writer.endObject()
      }

      is BKTValue.List -> {
        writer.beginArray()
        value.list.forEach { v -> toJson(writer, v) }
        writer.endArray()
      }

      is BKTValue.Null, null -> writer.nullValue()
    }
  }

  @FromJson
  override fun fromJson(reader: JsonReader): BKTValue =
    when (reader.peek()) {
      JsonReader.Token.STRING -> BKTValue.String(reader.nextString())
      JsonReader.Token.BOOLEAN -> BKTValue.Boolean(reader.nextBoolean())
      JsonReader.Token.NUMBER -> {
        BKTValue.Number(reader.nextDouble())
      }
      JsonReader.Token.BEGIN_OBJECT -> {
        val structure = mutableMapOf<String, BKTValue>()
        reader.beginObject()
        while (reader.hasNext()) {
          structure[reader.nextName()] = fromJson(reader)
        }
        reader.endObject()
        BKTValue.Structure(structure)
      }

      JsonReader.Token.BEGIN_ARRAY -> {
        val list = mutableListOf<BKTValue>()
        reader.beginArray()
        while (reader.hasNext()) {
          list.add(fromJson(reader))
        }
        reader.endArray()
        BKTValue.List(list)
      }

      JsonReader.Token.NULL -> {
        reader.nextNull<Unit>()
        BKTValue.Null
      }

      else -> throw JsonDataException("Unknown token: ${reader.peek()}")
    }
}

fun BKTValue.toJSONString(): String {
  val result = BKTValueAdapter().toJson(this)
  return result
}
