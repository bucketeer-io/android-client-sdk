package io.bucketeer.sdk.android.internal.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class User(
  val id: String,
  val data: Map<String, String> = emptyMap(),
  // note: tagged_data is not used in client SDK
)
