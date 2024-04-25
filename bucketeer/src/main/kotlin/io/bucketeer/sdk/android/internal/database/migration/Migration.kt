package io.bucketeer.sdk.android.internal.database.migration

import android.content.SharedPreferences
import androidx.sqlite.db.SupportSQLiteDatabase

interface Migration {
  fun migrate(
    db: SupportSQLiteDatabase,
    sharedPreferences: SharedPreferences,
  )
}
