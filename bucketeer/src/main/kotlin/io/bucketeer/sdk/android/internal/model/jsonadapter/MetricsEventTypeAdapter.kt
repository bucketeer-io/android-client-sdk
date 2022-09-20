package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.bucketeer.sdk.android.internal.model.MetricsEventType

class MetricsEventTypeAdapter {
  @FromJson
  fun fromJson(value: Int): MetricsEventType = MetricsEventType.from(value)

  @ToJson
  fun toJson(type: MetricsEventType): Int = type.value
}
