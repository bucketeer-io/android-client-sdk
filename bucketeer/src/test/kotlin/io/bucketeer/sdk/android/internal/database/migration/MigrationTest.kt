package io.bucketeer.sdk.android.internal.database.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.internal.database.getString
import io.bucketeer.sdk.android.internal.database.transaction
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
  private lateinit var openHelper: SupportSQLiteOpenHelper

  @Before
  fun setup() {
    val config =
      SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
        .callback(MigrationTestSQLiteOpenHelperCallback())
        .name(null)
        .build()

    openHelper = FrameworkSQLiteOpenHelperFactory().create(config)
  }

  @Test
  fun test() {
    openHelper.writableDatabase.transaction {
      // add new migration class here
      listOf(Migration1to2()).forEach { migration ->
        migration.migrate(this)
      }
    }

    val db = openHelper.writableDatabase

    val c = db.query(
      """
      SELECT name, sql FROM sqlite_master 
      WHERE type = 'table' 
      AND (name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%')
      """.trimIndent(),
    )

    // Here we just compare table definition.
    // Update assertions as we add new migration.
    val tables = mutableListOf("event", "evaluation")
    c.use {
      it.moveToFirst()
      while (!it.isAfterLast) {
        val name = c.getString("name")
        val sql = c.getString("sql")
        when (name) {
          "event" -> {
            tables.remove(name)
            assertThat(sql).contains("event TEXT")
            assertThat(sql).doesNotContain("BLOB")
          }
          "evaluation" -> {
            tables.remove(name)
            assertThat(sql).contains("evaluation TEXT")
            assertThat(sql).doesNotContain("BLOB")
          }
          else -> {
            fail("unknown table: $name, $sql")
          }
        }

        it.moveToNext()
      }
    }

    // make sure every table exists
    assertThat(tables).isEmpty()
  }
}

private class MigrationTestSQLiteOpenHelperCallback : SupportSQLiteOpenHelper.Callback(1) {
  override fun onCreate(db: SupportSQLiteDatabase) {
    v1Schema(db)
  }

  override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // no-op
  }
}

private fun v1Schema(db: SupportSQLiteDatabase) {
  db.execSQL(
    """
      |CREATE TABLE current_evaluation (
      |   user_id TEXT NOT NULL,
      |   feature_id TEXT NOT NULL,
      |   evaluation BLOB NOT NULL,
      |   PRIMARY KEY(
      |     user_id,
      |     feature_id
      |   )
      |)
    """.trimMargin(),
  )

  db.execSQL(
    """
      |CREATE TABLE latest_evaluation (
      |   user_id TEXT,
      |   feature_id TEXT,
      |   evaluation BLOB,
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
      |   event BLOB
      |)
    """.trimMargin(),
  )
}
