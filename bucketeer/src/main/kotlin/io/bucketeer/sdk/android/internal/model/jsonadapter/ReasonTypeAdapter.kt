package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.bucketeer.sdk.android.internal.model.ReasonType

class ReasonTypeAdapter {
  @ToJson
  fun toJson(type: ReasonType): String = type.value

  @FromJson
  fun fromJson(value: String): ReasonType = ReasonType.from(value)
}
