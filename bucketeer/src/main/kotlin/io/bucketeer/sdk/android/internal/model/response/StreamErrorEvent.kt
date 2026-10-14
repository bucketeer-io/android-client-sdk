package io.bucketeer.sdk.android.internal.model.response

import com.squareup.moshi.JsonClass

/** Sent by the server right before it closes the stream. Only logged, so every field is optional. */
@JsonClass(generateAdapter = true)
internal data class StreamErrorEvent(
  val code: String? = null,
  val message: String? = null,
)
