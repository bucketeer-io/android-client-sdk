package io.bucketeer.sdk.android.internal.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Evaluation(
  val id: String,
  val featureId: String,
  val featureVersion: Int,
  val userId: String,
  val variationId: String,
  val variation: Variation,
  val reason: Reason,
  val variationValue: String,
)
