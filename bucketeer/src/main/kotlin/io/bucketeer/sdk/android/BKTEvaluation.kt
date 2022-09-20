package io.bucketeer.sdk.android

data class BKTEvaluation(
  var id: String,
  val featureId: String,
  val featureVersion: Int,
  val userId: String,
  val variationId: String,
  val variationValue: String,
  val reason: Reason,
) {
  enum class Reason(val value: Int) {
    TARGET(0),
    RULE(1),
    DEFAULT(3),
    CLIENT(4),
    OFF_VARIATION(5),
    PREREQUISITE(6),

    ;

    companion object {
      fun from(value: Int): Reason = values().first { it.value == value }
    }
  }
}
