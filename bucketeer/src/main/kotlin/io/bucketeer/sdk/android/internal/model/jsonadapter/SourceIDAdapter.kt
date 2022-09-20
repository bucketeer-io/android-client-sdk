package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.bucketeer.sdk.android.internal.model.SourceID

class SourceIDAdapter {
  @ToJson
  fun toJson(type: SourceID): Int = type.value

  @FromJson
  fun fromJson(value: Int): SourceID = SourceID.from(value)
}
