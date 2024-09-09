package io.bucketeer.sdk.android.internal.database.migration

import android.content.Context
import android.content.SharedPreferences
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.database.OpenHelperCallback
import io.bucketeer.sdk.android.internal.database.getString
import io.bucketeer.sdk.android.internal.database.transaction
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationSQLDaoImpl
import io.bucketeer.sdk.android.internal.event.db.EventSQLDaoImpl
import io.bucketeer.sdk.android.mocks.evaluation1
import io.bucketeer.sdk.android.mocks.evaluationEvent
import io.bucketeer.sdk.android.mocks.user1
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
  private lateinit var moshi: Moshi
  private lateinit var sharedPreferences: SharedPreferences

  @Before
  fun setup() {
    moshi = DataModule.createMoshi()
    val context: Context = ApplicationProvider.getApplicationContext()
    sharedPreferences =
      context.getSharedPreferences(
        Constants.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
      )
  }

  @Test
  fun testMigration1to2() {
    val openHelper = createOpenHelper(1)
    openHelper.writableDatabase.transaction {
      // add new migration class here
      Migration1to2().migrate(this, sharedPreferences)
    }

    val db = openHelper.writableDatabase

    val c =
      db.query(
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

    openHelper.close()
  }

  @Test
  fun testMigration2to3() {
    val openHelper = createOpenHelper(2)
    val evaluationDao = EvaluationSQLDaoImpl(openHelper, moshi)
    val eventDao = EventSQLDaoImpl(openHelper, moshi)

    // Put some data before migrating
    eventDao.addEvent(evaluationEvent)
    evaluationDao.put(user1.id, listOf(evaluation1))
    sharedPreferences
      .edit()
      .putString(Constants.PREFERENCE_KEY_USER_EVALUATION_ID, "user-evaluation-id")
      .commit()

    // Check the data
    assertThat(evaluationDao.get(user1.id)).isNotEmpty()
    assertThat(eventDao.getEvents()).isNotEmpty()
    assertThat(
      sharedPreferences.getString(Constants.PREFERENCE_KEY_USER_EVALUATION_ID, ""),
    ).isEqualTo("user-evaluation-id")

    // Migrate
    openHelper.writableDatabase.transaction {
      Migration2to3().migrate(this, sharedPreferences)
    }

    // Check the data again if is cleared
    assertThat(evaluationDao.get(user1.id)).isEmpty()
    assertThat(eventDao.getEvents()).isEmpty()
    assertThat(sharedPreferences.getString(Constants.PREFERENCE_KEY_USER_EVALUATION_ID, ""))
      .isEqualTo("")

    openHelper.close()
  }
}

private fun createOpenHelper(version: Int): SupportSQLiteOpenHelper {
  val callback =
    object : SupportSQLiteOpenHelper.Callback(version) {
      override fun onCreate(db: SupportSQLiteDatabase) {
        if (version == 1) {
          v1Schema(db)
        }
        if (version == 2) {
          OpenHelperCallback.v2Schema(db)
        }
      }

      override fun onUpgrade(
        db: SupportSQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
      ) {
        // no-op
      }
    }
  val config =
    SupportSQLiteOpenHelper.Configuration
      .builder(ApplicationProvider.getApplicationContext())
      .callback(callback)
      .name(null)
      .build()

  return FrameworkSQLiteOpenHelperFactory().create(config)
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
