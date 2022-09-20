package io.bucketeer.sdk.android.internal.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Variation(
  val id: String,
  val value: String,
  val name: String? = null,
  val description: String? = null,
)
