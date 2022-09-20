package io.bucketeer.sdk.android.internal.database.migration

import androidx.sqlite.db.SupportSQLiteDatabase

interface Migration {
  fun migrate(db: SupportSQLiteDatabase)
}
