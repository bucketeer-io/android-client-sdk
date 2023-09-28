package io.bucketeer.sdk.android.internal.evaluation.db

import android.content.Context
import android.content.SharedPreferences
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.database.OpenHelperCallback
import io.bucketeer.sdk.android.internal.database.createDatabase
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.mocks.evaluation1
import io.bucketeer.sdk.android.mocks.evaluation2
import io.bucketeer.sdk.android.mocks.evaluation3
import io.bucketeer.sdk.android.mocks.evaluation4
import io.bucketeer.sdk.android.mocks.user1
import io.bucketeer.sdk.android.mocks.user2
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EvaluationSQLDaoImplTest {
  private lateinit var sqlDao: EvaluationSQLDaoImpl
  private lateinit var openHelper: SupportSQLiteOpenHelper
  private lateinit var moshi: Moshi
  private lateinit var sharedPreferences: SharedPreferences

  @Before
  fun setup() {
    moshi = DataModule.createMoshi()
    val context: Context = ApplicationProvider.getApplicationContext()
    sharedPreferences = context.getSharedPreferences(
      Constants.PREFERENCES_NAME,
      Context.MODE_PRIVATE,
    )
    openHelper = createDatabase(context, OpenHelperCallback.FILE_NAME, sharedPreferences)
    sqlDao = EvaluationSQLDaoImpl(openHelper, moshi)
  }

  @After
  fun tearDown() {
    openHelper.close()
  }

  @Test
  fun get() {
    // empty
    assert(sqlDao.get(user1.id).isEmpty())
    sqlDao.put(user1.id, listOf(evaluation1))
    // single item
    assert(sqlDao.get(user1.id) == listOf(evaluation1))
    sqlDao.put(user1.id, listOf(evaluation2))
    // multiple item
    assert(sqlDao.get(user1.id) == listOf(evaluation1, evaluation2))
    // get upsert, should not duplicate
    sqlDao.put(user1.id, listOf(evaluation1, evaluation2))
    assert(sqlDao.get(user1.id) == listOf(evaluation1, evaluation2))
  }

  @Test
  fun putDataForUser1() {
    val expected = listOf(evaluation1, evaluation2)
    sqlDao.put(user1.id, expected)
    val actual = sqlDao.get(user1.id)
    assert(actual == expected)
  }

  @Test
  fun deleteAllForUser1() {
    putDataForUser1()
    sqlDao.deleteAll(user1.id)
    val actual = sqlDao.get(user1.id)
    assert(actual.isEmpty())
  }

  @Test
  fun `put - should only insert data for correct user1`() {
    putDataForUser1()
    val actual = sqlDao.get(user2.id)
    assert(actual.isEmpty())
  }

  @Test
  fun `delete - should only delete data for correct user1`() {
    sqlDao.put(user2.id, listOf(evaluation3, evaluation4))
    val actual = sqlDao.get(user2.id)
    assert(actual == listOf(evaluation3, evaluation4))
    deleteAllForUser1()
    val expected = sqlDao.get(user2.id)
    assert(expected == listOf(evaluation3, evaluation4))
  }

  @Test
  fun startTransaction() {
    try {
      sqlDao.startTransaction {
        putDataForUser1()
        sqlDao.put(user2.id, listOf(evaluation3, evaluation4))
        assert(sqlDao.get(user1.id) == listOf(evaluation1, evaluation2))
        assert(sqlDao.get(user2.id) == listOf(evaluation3, evaluation4))
        throw Exception("unknown")
      }
    } catch (ex: Exception) {
      print(ex.localizedMessage)
    }
    // should empty because the transaction was cancelled
    assert(sqlDao.get(user1.id).isEmpty())
    assert(sqlDao.get(user2.id).isEmpty())

    sqlDao.startTransaction {
      putDataForUser1()
      sqlDao.put(user2.id, listOf(evaluation3, evaluation4))
    }

    // should success save the data
    assert(sqlDao.get(user1.id) == listOf(evaluation1, evaluation2))
    assert(sqlDao.get(user2.id) == listOf(evaluation3, evaluation4))
  }
}
