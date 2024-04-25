package io.bucketeer.sdk.android.internal.database

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import io.bucketeer.sdk.android.internal.database.migration.Migration1to2
import io.bucketeer.sdk.android.internal.database.migration.Migration2to3
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationEntity
import io.bucketeer.sdk.android.internal.event.EventEntity

class OpenHelperCallback(
  private val sharedPreferences: SharedPreferences,
) : SupportSQLiteOpenHelper.Callback(VERSION) {
  companion object {
    const val FILE_NAME = "bucketeer.db"
    const val VERSION = 3

    @VisibleForTesting
    fun v2Schema(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        |CREATE TABLE ${EvaluationEntity.TABLE_NAME} (
        |   ${EvaluationEntity.COLUMN_USER_ID} TEXT,
        |   ${EvaluationEntity.COLUMN_FEATURE_ID} TEXT,
        |   ${EvaluationEntity.COLUMN_EVALUATION} TEXT,
        |   PRIMARY KEY(
        |     ${EvaluationEntity.COLUMN_USER_ID},
        |     ${EvaluationEntity.COLUMN_FEATURE_ID}
        |   )
        |)
        """.trimMargin(),
      )

      db.execSQL(
        """
        |CREATE TABLE ${EventEntity.TABLE_NAME} (
        |   ${EventEntity.COLUMN_ID} TEXT PRIMARY KEY,
        |   ${EventEntity.COLUMN_EVENT} TEXT
        |)
        """.trimMargin(),
      )
    }
  }

  override fun onCreate(db: SupportSQLiteDatabase) {
    v2Schema(db)
  }

  override fun onUpgrade(
    db: SupportSQLiteDatabase,
    oldVersion: Int,
    newVersion: Int,
  ) {
    if (oldVersion < 2) {
      Migration1to2().migrate(db, sharedPreferences)
    }
    if (oldVersion < 3) {
      Migration2to3().migrate(db, sharedPreferences)
    }
  }
}

fun createDatabase(
  context: Context,
  fileName: String? = OpenHelperCallback.FILE_NAME,
  sharedPreferences: SharedPreferences,
): SupportSQLiteOpenHelper {
  val config =
    SupportSQLiteOpenHelper.Configuration.builder(context)
      .name(fileName)
      .callback(OpenHelperCallback(sharedPreferences))
      .build()

  return FrameworkSQLiteOpenHelperFactory().create(config)
}
