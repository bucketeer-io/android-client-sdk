package io.bucketeer.sdk.android.internal.database

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQueryBuilder

inline fun <T> SupportSQLiteDatabase.transaction(
  exclusive: Boolean = true,
  block: SupportSQLiteDatabase.() -> T,
): T {
  if (exclusive) {
    beginTransaction()
  } else {
    beginTransactionNonExclusive()
  }
  try {
    val result = block()
    setTransactionSuccessful()
    return result
  } finally {
    endTransaction()
  }
}

fun SupportSQLiteDatabase.select(
  table: String,
  columns: Array<String>? = null,
  selection: String? = null,
  selectionArgs: Array<String>? = null,
  groupBy: String? = null,
  having: String? = null,
  orderBy: String? = null,
  limit: String? = null,
): Cursor {
  val builder = SupportSQLiteQueryBuilder.builder(table)
    .columns(columns)
    .selection(selection, selectionArgs)
    .groupBy(groupBy)
    .having(having)
    .orderBy(orderBy)
    .limit(limit)

  return query(builder.create())
}
