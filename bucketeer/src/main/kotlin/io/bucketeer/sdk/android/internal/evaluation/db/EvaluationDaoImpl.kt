package io.bucketeer.sdk.android.internal.evaluation.db

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

internal class EvaluationDaoImpl(
  private val sqLiteOpenHelper: SupportSQLiteOpenHelper,
  moshi: Moshi,
) : EvaluationDao {

  private val adapter = moshi.adapter(Evaluation::class.java)

  override fun put(userId: String, list: List<Evaluation>) {
    sqLiteOpenHelper.writableDatabase.transaction {
      list.forEach { evaluation ->
        val affectedRow = update(this@transaction, userId, evaluation)
        if (affectedRow == 0) {
          insert(this@transaction, userId, evaluation)
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
      put(COLUMN_FEATURE_ID, evaluation.feature_id)
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
      arrayOf(userId, evaluation.feature_id),
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
      "$COLUMN_USER_ID=?",
      arrayOf(userId),
    )
  }

  override fun deleteAllAndInsert(
    userId: String,
    list: List<Evaluation>,
  ): Boolean {
    sqLiteOpenHelper.writableDatabase.transaction {
      deleteAll(this, userId)
      list.forEach {
        if (insert(this, userId, it) == -1L) {
          return false
        }
      }
    }
    return true
  }
}
