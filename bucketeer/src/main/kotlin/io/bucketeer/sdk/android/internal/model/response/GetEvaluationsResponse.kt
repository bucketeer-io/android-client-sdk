package io.bucketeer.sdk.android.internal.model.response

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GetEvaluationsResponse(
  val data: GetEvaluationsDataResponse,
)
