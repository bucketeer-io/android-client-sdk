package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.bucketeer.sdk.android.internal.model.EventType

class EventTypeAdapter {
  @ToJson
  fun toJson(type: EventType): Int = type.value

  @FromJson
  fun fromJson(value: Int): EventType = EventType.from(value)
}
