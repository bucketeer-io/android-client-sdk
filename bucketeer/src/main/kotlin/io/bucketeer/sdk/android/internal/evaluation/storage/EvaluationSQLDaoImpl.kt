package io.bucketeer.sdk.android.internal.evaluation.storage

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.internal.database.asSequence
import io.bucketeer.sdk.android.internal.database.getString
import io.bucketeer.sdk.android.internal.database.select
import io.bucketeer.sdk.android.internal.database.transaction
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationEntity.COLUMN_EVALUATION
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationEntity.COLUMN_FEATURE_ID
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationEntity.COLUMN_USER_ID
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationEntity.TABLE_NAME
import io.bucketeer.sdk.android.internal.model.Evaluation

internal class EvaluationSQLDaoImpl(
  private val sqLiteOpenHelper: SupportSQLiteOpenHelper,
  moshi: Moshi,
) : EvaluationSQLDao {

  private val adapter = moshi.adapter(Evaluation::class.java)

  override fun put(userId: String, list: List<Evaluation>) {
    sqLiteOpenHelper.writableDatabase.apply {
      list.forEach { evaluation ->
        val affectedRow = update(this, userId, evaluation)
        if (affectedRow == 0) {
          insert(this, userId, evaluation)
        }
      }
    }
  }

  private fun insert(
    database: SupportSQLiteDatabase,
    userId: String,
    evaluation: Evaluation,
  ): Long {
    val contentValue = ContentValues().apply {
      put(COLUMN_USER_ID, userId)
      put(COLUMN_FEATURE_ID, evaluation.featureId)
      put(COLUMN_EVALUATION, adapter.toJson(evaluation))
    }
    return database.insert(TABLE_NAME, SQLiteDatabase.CONFLICT_REPLACE, contentValue)
  }

  private fun update(
    database: SupportSQLiteDatabase,
    userId: String,
    evaluation: Evaluation,
  ): Int {
    val contentValues = ContentValues().apply {
      put(COLUMN_EVALUATION, adapter.toJson(evaluation))
    }
    return database.update(
      TABLE_NAME,
      SQLiteDatabase.CONFLICT_REPLACE,
      contentValues,
      "$COLUMN_USER_ID=? AND $COLUMN_FEATURE_ID=?",
      arrayOf(userId, evaluation.featureId),
    )
  }

  override fun get(userId: String): List<Evaluation> {
    val projection = arrayOf(COLUMN_USER_ID, COLUMN_EVALUATION)
    val c = sqLiteOpenHelper.readableDatabase.select(
      table = TABLE_NAME,
      columns = projection,
      selection = "$COLUMN_USER_ID=?",
      selectionArgs = arrayOf(userId),
    )

    return c.use {
      c.asSequence()
        .mapNotNull { adapter.fromJson(it.getString(COLUMN_EVALUATION)) }
        .toList()
    }
  }

  private fun deleteAll(
    database: SupportSQLiteDatabase,
    userId: String,
  ) {
    database.delete(
      TABLE_NAME,
      "$COLUMN_USER_ID=? AND $COLUMN_FEATURE_ID=?",
      arrayOf(userId),
    )
  }

  override fun deleteBy(userId: String, featureIds: List<String>) {
    sqLiteOpenHelper.writableDatabase.delete(
      TABLE_NAME,
      "$COLUMN_USER_ID=? AND $COLUMN_FEATURE_ID IN ?",
      arrayOf(userId, featureIds),
    )
  }

  override fun deleteAll(userId: String) {
    sqLiteOpenHelper.writableDatabase.delete(
      TABLE_NAME,
      "$COLUMN_USER_ID=?",
      arrayOf(userId),
    )
  }

  override fun startTransaction(block: () -> Unit) {
    sqLiteOpenHelper.writableDatabase.transaction {
      block()
    }
  }
}
