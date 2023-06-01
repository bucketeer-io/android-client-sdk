package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.bucketeer.sdk.android.internal.model.ApiID

class ApiIDAdapter {
  @FromJson
  fun fromJson(value: Int): ApiID = ApiID.from(value)

  @ToJson
  fun toJson(type: ApiID): Int = type.value
}
