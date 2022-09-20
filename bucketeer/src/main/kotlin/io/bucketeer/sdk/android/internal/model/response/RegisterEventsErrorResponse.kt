package io.bucketeer.sdk.android.internal.model.response

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RegisterEventsErrorResponse(
  val retriable: Boolean = false,
  val message: String,
)
