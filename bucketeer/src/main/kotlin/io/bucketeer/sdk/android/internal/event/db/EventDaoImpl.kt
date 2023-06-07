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

internal class EventDaoImpl(
  private val sqLiteOpenHelper: SupportSQLiteOpenHelper,
  moshi: Moshi,
) : EventDao {

  private val eventAdapter = moshi.adapter(Event::class.java)

  override fun addEvent(event: Event) {
    addEventInternal(sqLiteOpenHelper.writableDatabase, event)
  }

  override fun addEvents(events: List<Event>) {
    // This approach below is a reference from iOS SDK.
    // It could be better but This approach will create a minimum impact,
    // And because the number of pending events in the database is small
    // So I think we are safe to do this without changing too much
    // 1. Get all current events and collect hash
    // https://kotlinlang.org/docs/data-classes.html
    val storedEvents = getEvents()
    val storedEventHashList = storedEvents.map {
      it.event.hashCode()
    }
    sqLiteOpenHelper.writableDatabase.transaction {
      events.forEach { event ->
        // 2. Push to the database when the event data do not exist in the database
        if (!storedEventHashList.contains(event.event.hashCode())) {
          addEventInternal(this, event)
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
