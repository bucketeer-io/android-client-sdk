package io.bucketeer.sdk.android.internal.event.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.internal.database.asSequence
import io.bucketeer.sdk.android.internal.database.getString
import io.bucketeer.sdk.android.internal.database.select
import io.bucketeer.sdk.android.internal.database.transaction
import io.bucketeer.sdk.android.internal.event.EventEntity.Companion.COLUMN_EVENT
import io.bucketeer.sdk.android.internal.event.EventEntity.Companion.COLUMN_ID
import io.bucketeer.sdk.android.internal.event.EventEntity.Companion.TABLE_NAME
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.EventData

internal class EventDaoImpl(
  private val sqLiteOpenHelper: SupportSQLiteOpenHelper,
  moshi: Moshi,
) : EventDao {

  private val eventAdapter = moshi.adapter(Event::class.java)

  override fun addEvent(event: Event) {
    addEvents(listOf(event))
  }

  override fun addEvents(events: List<Event>) {
    // This approach below is a reference from iOS SDK.
    // It could be better but This approach will create a minimum impact,
    // And because the number of pending events in the database is small
    // So I think we are safe to do this without changing too much
    val storedEvents = getEvents()
    val metricsEventUniqueKeys: List<String> = storedEvents.filter {
      it.event is EventData.MetricsEvent
    }.map {
      val metricsEvent = it.event as EventData.MetricsEvent
      return@map metricsEvent.uniqueKey()
    }

    sqLiteOpenHelper.writableDatabase.transaction {
      events.forEach { item ->
        when (item.event) {
          is EventData.MetricsEvent -> {
            // 2. Push to the database when the event data do not exist in the database
            if (!metricsEventUniqueKeys.contains(item.event.uniqueKey())) {
              addEventInternal(this, item)
            }
          }
          else -> addEventInternal(this, item)
        }
      }
    }
  }

  override fun getEvents(): List<Event> {
    val c = sqLiteOpenHelper.readableDatabase.select(
      table = TABLE_NAME,
    )

    return c.use {
      c.asSequence()
        .mapNotNull { eventAdapter.fromJson(it.getString(COLUMN_EVENT)) }
        .toList()
    }
  }

  override fun delete(ids: List<String>) {
    @Suppress("MoveLambdaOutsideParentheses")
    val valuesIn = List(ids.count(), { "?" }).joinToString(separator = ",")
    val whereArgs = ids.toTypedArray()

    sqLiteOpenHelper.writableDatabase.delete(
      TABLE_NAME,
      "$COLUMN_ID IN ($valuesIn)",
      whereArgs,
    )
  }

  private fun addEventInternal(writableDatabase: SupportSQLiteDatabase, event: Event) {
    val contentValues = ContentValues().apply {
      put(COLUMN_ID, event.id)
      put(COLUMN_EVENT, eventAdapter.toJson(event))
    }

    writableDatabase.insert(
      TABLE_NAME,
      SQLiteDatabase.CONFLICT_REPLACE,
      contentValues,
    )
  }
}
