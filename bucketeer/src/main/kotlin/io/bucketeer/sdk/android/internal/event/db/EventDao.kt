package io.bucketeer.sdk.android.internal.event.db

import io.bucketeer.sdk.android.internal.model.Event

internal interface EventDao {
  fun addEvent(event: Event)
  fun addEvents(events: List<Event>)
  fun getEvents(): List<Event>

  /** delete rows by ID */
  fun delete(ids: List<String>)
}
