package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.bucketeer.sdk.android.internal.model.SourceId

internal class SourceIDAdapter {
  @ToJson
  fun toJson(type: SourceId): Int = type.value

  @FromJson
  fun fromJson(value: Int): SourceId = SourceId.from(value)
}
