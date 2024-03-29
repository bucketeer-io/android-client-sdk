package io.bucketeer.sdk.android.internal.model.response

import com.squareup.moshi.JsonClass
import io.bucketeer.sdk.android.internal.model.UserEvaluations

@JsonClass(generateAdapter = true)
data class GetEvaluationsResponse(
  val evaluations: UserEvaluations,
  val userEvaluationsId: String,
)
