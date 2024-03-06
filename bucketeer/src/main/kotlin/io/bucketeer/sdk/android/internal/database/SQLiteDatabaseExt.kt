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

/**
 * Be aware that `having` clauses are only permitted when using a `groupBy` clause.
 */
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

  if (!columns.isNullOrEmpty()) {
    builder.columns(columns)
  }
  if (!selection.isNullOrEmpty() && !selectionArgs.isNullOrEmpty()) {
    builder.selection(selection, selectionArgs)
  }
  if (!groupBy.isNullOrEmpty()) {
    builder.groupBy(groupBy)
  }
  if (!having.isNullOrEmpty()) {
    builder.having(having)
  }
  if (!orderBy.isNullOrEmpty()) {
    builder.orderBy(orderBy)
  }
  if (!limit.isNullOrEmpty()) {
    builder.limit(limit)
  }
  return query(builder.create())
}
