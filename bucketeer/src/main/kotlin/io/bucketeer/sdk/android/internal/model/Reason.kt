package io.bucketeer.sdk.android.internal.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Reason(
  val type: ReasonType,
  val rule_id: String = "",
)
