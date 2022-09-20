package io.bucketeer.sdk.android.internal.model.response

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ErrorResponse(
  val error: ErrorDetail,
) {
  @JsonClass(generateAdapter = true)
  data class ErrorDetail(
    val code: Int,
    val message: String,
  )
}
