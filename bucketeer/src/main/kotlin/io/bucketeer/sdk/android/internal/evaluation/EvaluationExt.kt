package io.bucketeer.sdk.android.internal.evaluation

import io.bucketeer.sdk.android.BKTValue
import io.bucketeer.sdk.android.BKTValueAdapter
import io.bucketeer.sdk.android.internal.logd
import io.bucketeer.sdk.android.internal.model.Evaluation
import org.json.JSONObject
import java.util.Locale

internal inline fun <reified T : Any> Evaluation?.getVariationValue(): T? {
  val value = this?.variationValue
  val typedValue: T? =
    if (value != null) {
      val anyValue: Any? = value.getVariationValue<T>()
      anyValue as? T
    } else {
      logd {
        "getVariation returns null reason: " +
          when {
            this == null -> {
              "Evaluation is null"
            }

            (this.variationValue as CharSequence).isEmpty() -> {
              "Variation value is null or empty"
            }

            else -> {
              "Unknown"
            }
          }
      }
      null
    }
  return typedValue
}

internal fun String.getBKTValue(): BKTValue {
  val value = this
  try {
    val bktValue = BKTValueAdapter().fromJson(value)
    if (bktValue != null && bktValue.isNull().not()) {
      return bktValue
    }
  } catch (_: Exception) {
  }
  // For the method `getBKTValue`, if `BKTValue.Null` or exception is returned when decoding JSON using the raw string,
  // return the raw value using `BKTValue.String` instead.
  return BKTValue.String(value)
}

internal inline fun <reified T : Any> String.getVariationValue(): T? {
  val value = this
  val anyValue =
    @Suppress("IMPLICIT_CAST_TO_ANY")
    when (T::class) {
      String::class ->
        value
      Int::class -> value.toIntOrNull()
      Long::class -> value.toLongOrNull()
      Float::class -> value.toFloatOrNull()
      Double::class -> value.toDoubleOrNull()
      Boolean::class ->
        when (value.lowercase(Locale.ENGLISH)) {
          "true" -> true
          "false" -> false
          else -> null
        }
      JSONObject::class ->
        try {
          JSONObject(value)
        } catch (e: Exception) {
          null
        }
      BKTValue::class -> {
        value.getBKTValue()
      }
      else -> null
    }
  logd {
    if (anyValue == null) {
      "getVariation returns null reason: failed to cast"
    } else {
      null
    }
  }
  val typedValue: T? = anyValue as? T
  return typedValue
}

internal inline fun <reified T : Any> Evaluation?.getVariationValue(defaultValue: T): T {
  val typedValue: T = getVariationValue() ?: defaultValue
  return typedValue
}
