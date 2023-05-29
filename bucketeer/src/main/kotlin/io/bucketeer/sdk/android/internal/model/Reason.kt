package io.bucketeer.sdk.android.internal.model

import com.squareup.moshi.JsonClass
import io.bucketeer.sdk.android.ReasonType

@JsonClass(generateAdapter = true)
data class Reason(
  val type: ReasonType,
  val ruleId: String = "",
)
