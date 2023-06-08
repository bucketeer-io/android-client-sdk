package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.bucketeer.sdk.android.internal.model.ApiId

class ApiIdAdapter {
  @FromJson
  fun fromJson(value: Int): ApiId = ApiId.from(value)

  @ToJson
  fun toJson(type: ApiId): Int = type.value
}
