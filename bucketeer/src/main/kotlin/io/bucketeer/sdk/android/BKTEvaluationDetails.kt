package io.bucketeer.sdk.android

import io.bucketeer.sdk.android.internal.util.contains
import org.json.JSONObject

data class BKTEvaluationDetails<T>(
  val featureId: String,
  val featureVersion: Int,
  val userId: String,
  val variationId: String,
  val variationName: String,
  val variationValue: T,
  val reason: Reason,
) {
  enum class Reason {
    TARGET,
    RULE,
    DEFAULT,
    CLIENT,
    OFF_VARIATION,
    PREREQUISITE,

    ;

    companion object {
      fun from(value: String): Reason = entries.firstOrNull { it.name == value } ?: CLIENT
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is BKTEvaluationDetails<*>) return false
    if (featureId != other.featureId) return false
    if (featureVersion != other.featureVersion) return false
    if (userId != other.userId) return false
    if (variationId != other.variationId) return false
    if (variationName != other.variationName) return false
    if (reason != other.reason) return false

    return when {
      variationValue is JSONObject && other.variationValue is JSONObject ->
        (variationValue as JSONObject).contains(other.variationValue) || other.variationValue.contains(variationValue)
      else -> variationValue == other.variationValue
    }
  }

  override fun hashCode(): Int {
    var result = 31 * featureId.hashCode()
    result += 31 * featureVersion
    result += 31 * userId.hashCode()
    result += 31 * variationId.hashCode()
    result += 31 * variationName.hashCode()
    result += 31 * reason.hashCode()
    result +=
      when (variationValue) {
        // Ignore JSONObject
        is JSONObject -> 1
        else -> variationValue?.hashCode() ?: 0
      }
    return result
  }

  companion object {
    fun <T> newDefaultInstance(
      featureId: String,
      userId: String,
      defaultValue: T,
    ): BKTEvaluationDetails<T> =
      BKTEvaluationDetails(
        featureId = featureId,
        featureVersion = 0,
        userId = userId,
        variationId = "",
        variationName = "",
        variationValue = defaultValue,
        reason = BKTEvaluationDetails.Reason.CLIENT,
      )
  }
}
