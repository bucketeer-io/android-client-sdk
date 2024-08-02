package io.bucketeer.sdk.android

sealed interface BKTValue {

  fun asString(): kotlin.String? = if (this is String) string else null
  fun asBoolean(): kotlin.Boolean? = if (this is Boolean) boolean else null
  fun asInteger(): Int? = if (this is Integer) integer else null
  fun asDouble(): kotlin.Double? = if (this is Double) double else null
  fun asDate(): java.util.Date? = if (this is Date) date else null
  fun asList(): kotlin.collections.List<BKTValue>? = if (this is List) list else null
  fun asStructure(): Map<kotlin.String, BKTValue>? = if (this is Structure) structure else null
  fun isNull(): kotlin.Boolean = this is Null

  data class String(val string: kotlin.String) : BKTValue

  data class Boolean(val boolean: kotlin.Boolean) : BKTValue

  data class Integer(val integer: Int) : BKTValue

  data class Double(val double: kotlin.Double) : BKTValue

  data class Date(val date: java.util.Date) : BKTValue

  data class Structure(val structure: Map<kotlin.String, BKTValue>) : BKTValue

  data class List(val list: kotlin.collections.List<BKTValue>) : BKTValue

  object Null : BKTValue {
    override fun equals(other: Any?): kotlin.Boolean {
      return other is Null
    }

    override fun hashCode(): Int {
      return javaClass.hashCode()
    }
  }
}
