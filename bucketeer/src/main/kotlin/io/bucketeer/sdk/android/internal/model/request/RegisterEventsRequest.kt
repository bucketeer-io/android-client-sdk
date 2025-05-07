package io.bucketeer.sdk.android.internal.model.request

import com.squareup.moshi.JsonClass
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.SourceID

@JsonClass(generateAdapter = true)
internal data class RegisterEventsRequest(
  val events: List<Event> = emptyList(),
  val sdkVersion: String,
  val sourceId: SourceID,
)
