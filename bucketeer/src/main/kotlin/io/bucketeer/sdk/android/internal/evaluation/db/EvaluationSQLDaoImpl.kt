package io.bucketeer.sdk.android.internal.evaluation.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.VisibleForTesting
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.BKTException
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
  @get:VisibleForTesting
  internal val sqLiteOpenHelper: SupportSQLiteOpenHelper,
  moshi: Moshi,
) : EvaluationSQLDao {
  override var isClosed = false
  private val adapter = moshi.adapter(Evaluation::class.java)

  override fun put(userId: String, list: List<Evaluation>) {
    if (isClosed) {
      return
    }
    sqLiteOpenHelper.writableDatabase.apply {
      list.forEach { evaluation ->
        val affectedRow = update(this, userId, evaluation)
        if (affectedRow == 0) {
          if (insert(this, userId, evaluation) == -1L) {
            throw BKTException.IllegalStateException("Could not insert data")
          }
        }
      }
    }
  }

  private fun insert(
    database: SupportSQLiteDatabase,
    userId: String,
    evaluation: Evaluation,
  ): Long {
    if (isClosed) {
      return -1
    }
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
    if (isClosed) {
      return 0
    }
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
    if (isClosed) {
      return listOf()
    }
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

  override fun deleteAll(userId: String) {
    if (isClosed) {
      return
    }
    sqLiteOpenHelper.writableDatabase.delete(
      TABLE_NAME,
      "$COLUMN_USER_ID=?",
      arrayOf(userId),
    )
  }

  override fun startTransaction(block: () -> Unit) {
    if (isClosed) {
      return
    }
    sqLiteOpenHelper.writableDatabase.transaction {
      block()
    }
  }

  override fun close() {
    synchronized(this) {
      isClosed = true
    }
  }
}
