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

internal class EventSQLDaoImpl(
  private val sqLiteOpenHelper: SupportSQLiteOpenHelper,
  moshi: Moshi,
) : EventSQLDao {
  override var isClosed = false
  private val eventAdapter = moshi.adapter(Event::class.java)

  override fun addEvent(event: Event) {
    addEvents(listOf(event))
  }

  override fun addEvents(events: List<Event>) {
    if (isClosed) {
      return
    }
    sqLiteOpenHelper.writableDatabase.transaction {
      events.forEach { event ->
        addEventInternal(this, event)
      }
    }
  }

  override fun getEvents(): List<Event> {
    if (isClosed) {
      return listOf()
    }
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
    if (isClosed) {
      return
    }
    @Suppress("MoveLambdaOutsideParentheses")
    val valuesIn = List(ids.count(), { "?" }).joinToString(separator = ",")
    val whereArgs = ids.toTypedArray()

    sqLiteOpenHelper.writableDatabase.delete(
      TABLE_NAME,
      "$COLUMN_ID IN ($valuesIn)",
      whereArgs,
    )
  }

  override fun close() {
    synchronized(this) {
      isClosed = true;
    }
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
