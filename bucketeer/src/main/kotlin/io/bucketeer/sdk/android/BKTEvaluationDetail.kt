package io.bucketeer.sdk.android

import io.bucketeer.sdk.android.internal.util.contains
import org.json.JSONObject

data class BKTEvaluationDetail<T>(
  var id: String,
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
    if (other !is BKTEvaluationDetail<*>) return false

    if (id != other.id) return false
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
    var result = id.hashCode()
    result += featureId.hashCode()
    result += featureVersion
    result += userId.hashCode()
    result += variationId.hashCode()
    result += variationName.hashCode()
    result += reason.hashCode()
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
    ): BKTEvaluationDetail<T> {
      return BKTEvaluationDetail(
        id = "",
        featureId = featureId,
        featureVersion = 0,
        userId = userId,
        variationId = "",
        variationName = "",
        variationValue = defaultValue,
        reason = BKTEvaluationDetail.Reason.CLIENT,
      )
    }
  }
}
