package io.bucketeer.sdk.android.internal.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserEvaluationCondition(
  val evaluatedAt: String,
  val userAttributesUpdated: String,
)
