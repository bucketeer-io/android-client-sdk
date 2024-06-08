package io.bucketeer.sdk.android

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
      fun from(value: String): Reason = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
  }
}
