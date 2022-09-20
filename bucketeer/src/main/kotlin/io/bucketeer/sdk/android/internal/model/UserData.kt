package io.bucketeer.sdk.android.internal.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserData(
  val value: Map<String, String> = emptyMap(),
)
