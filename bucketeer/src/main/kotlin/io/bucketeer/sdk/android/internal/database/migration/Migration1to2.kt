package io.bucketeer.sdk.android.internal.database.migration

import androidx.sqlite.db.SupportSQLiteDatabase

class Migration1to2 : Migration {

  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("DROP TABLE current_evaluation")
    db.execSQL("DROP TABLE latest_evaluation")
    db.execSQL("DROP TABLE event")

    db.execSQL(
      """
      |CREATE TABLE evaluation (
      |   user_id TEXT,
      |   feature_id TEXT,
      |   evaluation TEXT,
      |   PRIMARY KEY(
      |     user_id,
      |     feature_id
      |   )
      |)
      """.trimMargin(),
    )

    db.execSQL(
      """
      |CREATE TABLE event (
      |   id TEXT PRIMARY KEY,
      |   event TEXT
      |)
      """.trimMargin(),
    )
  }
}
