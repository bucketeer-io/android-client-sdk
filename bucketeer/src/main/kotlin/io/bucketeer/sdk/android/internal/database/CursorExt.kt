package io.bucketeer.sdk.android.internal.database

import android.database.Cursor

internal fun Cursor.asSequence(): Sequence<Cursor> {
  return generateSequence(seed = takeIf { it.moveToFirst() }) { takeIf { it.moveToNext() } }
}

// TODO add Cursor.get(Int, Long, Double, ...)
internal fun Cursor.getString(name: String): String {
  return getString(getColumnIndexOrThrow(name))
}

internal fun Cursor.getBlob(name: String): ByteArray {
  return getBlob(getColumnIndexOrThrow(name))
}

internal fun Cursor.getInt(name: String): Int {
  return getInt(getColumnIndexOrThrow(name))
}
