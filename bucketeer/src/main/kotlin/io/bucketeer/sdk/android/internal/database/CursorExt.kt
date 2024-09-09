package io.bucketeer.sdk.android.internal.database

import android.database.Cursor

internal fun Cursor.asSequence(): Sequence<Cursor> = generateSequence(seed = takeIf { it.moveToFirst() }) { takeIf { it.moveToNext() } }

// TODO add Cursor.get(Int, Long, Double, ...)
internal fun Cursor.getString(name: String): String = getString(getColumnIndexOrThrow(name))

internal fun Cursor.getBlob(name: String): ByteArray = getBlob(getColumnIndexOrThrow(name))

internal fun Cursor.getInt(name: String): Int = getInt(getColumnIndexOrThrow(name))
