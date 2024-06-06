package io.bucketeer.sdk.android

@Deprecated(
  message = "BKTEvaluation is deprecated in favor of BKTEvaluationDetails<T>",
  replaceWith = ReplaceWith("BKTEvaluationDetails<T>"),
)
data class BKTEvaluation(
  var id: String,
  val featureId: String,
  val featureVersion: Int,
  val userId: String,
  val variationId: String,
  val variationName: String,
  val variationValue: String,
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
