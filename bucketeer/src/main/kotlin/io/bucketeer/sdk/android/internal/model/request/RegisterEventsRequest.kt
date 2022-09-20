package io.bucketeer.sdk.android.internal.model.request

import com.squareup.moshi.JsonClass
import io.bucketeer.sdk.android.internal.model.Event

@JsonClass(generateAdapter = true)
data class RegisterEventsRequest(
  val events: List<Event> = emptyList(),
)
