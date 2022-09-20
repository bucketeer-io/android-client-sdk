package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.bucketeer.sdk.android.internal.model.ReasonType

class ReasonTypeAdapter {
  @ToJson
  fun toJson(type: ReasonType): Int = type.value

  @FromJson
  fun fromJson(value: Int): ReasonType = ReasonType.from(value)
}
