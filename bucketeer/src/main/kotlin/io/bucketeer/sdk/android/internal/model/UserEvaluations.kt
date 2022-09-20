package io.bucketeer.sdk.android.internal.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserEvaluations(
  val id: String,
  val evaluations: List<Evaluation> = emptyList(),
)
