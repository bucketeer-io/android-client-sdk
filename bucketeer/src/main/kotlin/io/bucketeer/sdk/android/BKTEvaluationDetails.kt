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
  /**
   * Public enum representing the reason for a feature flag evaluation.
   *
   * Successful evaluation reasons:
   * - TARGET: User matched an individual targeting rule
   * - RULE: User matched a custom rule
   * - DEFAULT: Using the default strategy
   * - OFF_VARIATION: Feature flag is off
   * - PREREQUISITE: Evaluated via a prerequisite flag
   *
   * Error evaluation reasons:
   * - ERROR_NO_EVALUATIONS: No evaluations performed
   * - ERROR_FLAG_NOT_FOUND: Feature flag not found in cache
   * - ERROR_WRONG_TYPE: Type mismatch during value conversion
   * - ERROR_USER_ID_NOT_SPECIFIED: User ID validation failed
   * - ERROR_FEATURE_FLAG_ID_NOT_SPECIFIED: Feature flag ID validation failed
   * - ERROR_EXCEPTION: Unexpected error or unknown reason
   * - ERROR_CACHE_NOT_FOUND: Cache not ready after SDK initialization
   *
   * Deprecated:
   * - CLIENT: Legacy generic client error (use ERROR_* types instead)
   */
  enum class Reason {
    TARGET,
    RULE,
    DEFAULT,

    @Deprecated("CLIENT is deprecated. Use error-prefixed reason types instead.")
    CLIENT,
    OFF_VARIATION,
    PREREQUISITE,

    ERROR_NO_EVALUATIONS,
    ERROR_FLAG_NOT_FOUND,
    ERROR_WRONG_TYPE,
    ERROR_USER_ID_NOT_SPECIFIED,
    ERROR_FEATURE_FLAG_ID_NOT_SPECIFIED,
    ERROR_EXCEPTION,
    ERROR_CACHE_NOT_FOUND,

    ;

    companion object {
      fun from(value: String): Reason = entries.firstOrNull { it.name == value } ?: ERROR_EXCEPTION
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
      reason: Reason,
    ): BKTEvaluationDetails<T> =
      BKTEvaluationDetails(
        featureId = featureId,
        featureVersion = 0,
        userId = userId,
        variationId = "",
        variationName = "",
        variationValue = defaultValue,
        reason = reason,
      )
  }
}
