package io.bucketeer.sdk.android

data class BKTEvaluation(
  var id: String,
  val featureId: String,
  val featureVersion: Int,
  val userId: String,
  val variationId: String,
  val variationValue: String,
  val reason: ReasonType,
)
