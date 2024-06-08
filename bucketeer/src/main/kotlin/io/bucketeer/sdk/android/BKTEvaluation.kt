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
  @Suppress("DEPRECATION")
  val reason: Reason,
) {
  @Deprecated(
    "BKTEvaluation.Reason is deprecated in favor of BKTEvaluationDetails.Reason",
    replaceWith = ReplaceWith("BKTEvaluationDetails.Reason"),
  )
  enum class Reason {
    TARGET,
    RULE,
    DEFAULT,
    CLIENT,
    OFF_VARIATION,
    PREREQUISITE,

    ;

    companion object {
      @Suppress("DEPRECATION")
      fun from(value: String): Reason = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
  }
}
