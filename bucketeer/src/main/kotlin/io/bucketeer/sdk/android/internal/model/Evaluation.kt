package io.bucketeer.sdk.android.internal.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Evaluation(
  val id: String,
  val feature_id: String,
  val feature_version: Int,
  val user_id: String,
  val variation_id: String,
  val variation: Variation,
  val reason: Reason,
  val variation_value: String,
)
