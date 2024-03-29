package io.bucketeer.sdk.android.internal.model.response

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RegisterEventsResponse(
  val errors: Map<String, RegisterEventsErrorResponse> = emptyMap(),
)
