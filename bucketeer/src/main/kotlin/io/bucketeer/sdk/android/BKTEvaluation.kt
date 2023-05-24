package io.bucketeer.sdk.android

import io.bucketeer.sdk.android.internal.model.ReasonType

data class BKTEvaluation(
  var id: String,
  val featureId: String,
  val featureVersion: Int,
  val userId: String,
  val variationId: String,
  val variationValue: String,
  val reason: ReasonType,
)
