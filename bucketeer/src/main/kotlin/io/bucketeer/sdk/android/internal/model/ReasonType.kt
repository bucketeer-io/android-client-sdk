package io.bucketeer.sdk.android.internal.model

/**
 * Internal enum representing the reason type for feature flag evaluations.
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
enum class ReasonType {
  TARGET,
  RULE,
  DEFAULT,
  OFF_VARIATION,
  PREREQUISITE,

  @Deprecated("CLIENT is deprecated. Use error-prefixed reason types instead.")
  CLIENT,
  ERROR_NO_EVALUATIONS,
  ERROR_FLAG_NOT_FOUND,
  ERROR_WRONG_TYPE,
  ERROR_USER_ID_NOT_SPECIFIED,
  ERROR_FEATURE_FLAG_ID_NOT_SPECIFIED,
  ERROR_EXCEPTION,
  ERROR_CACHE_NOT_FOUND,

  ;

  companion object {
    fun from(value: String): ReasonType = values().firstOrNull { it.name == value } ?: ERROR_EXCEPTION
  }
}
