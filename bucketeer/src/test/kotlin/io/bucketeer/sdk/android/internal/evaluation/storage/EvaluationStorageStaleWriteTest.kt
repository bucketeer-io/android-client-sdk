package io.bucketeer.sdk.android.internal.evaluation.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.deleteSharedPreferences
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.cache.MemCache
import io.bucketeer.sdk.android.internal.database.OpenHelperCallback
import io.bucketeer.sdk.android.internal.database.createDatabase
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.evaluation.cache.EvaluationSharedPrefs
import io.bucketeer.sdk.android.internal.evaluation.cache.EvaluationSharedPrefsImpl
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationSQLDao
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationSQLDaoImpl
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.mocks.evaluation1
import io.bucketeer.sdk.android.mocks.evaluation2
import io.bucketeer.sdk.android.mocks.user1
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EvaluationStorageStaleWriteTest {
  private lateinit var userId: String
  private lateinit var openHelper: SupportSQLiteOpenHelper
  private lateinit var evaluationSQLDao: CountingEvaluationSQLDao
  private lateinit var evaluationSharedPrefs: EvaluationSharedPrefs
  private lateinit var memCache: MemCache<String, List<Evaluation>>
  private lateinit var storage: EvaluationStorage

  @Before
  fun setUp() {
    userId = user1.id
    val moshi = DataModule.createMoshi()
    val context: Context = ApplicationProvider.getApplicationContext()
    val sharedPreferences =
      context.getSharedPreferences(
        Constants.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
      )
    openHelper = createDatabase(context, OpenHelperCallback.FILE_NAME, sharedPreferences)
    memCache = MemCache.Builder<String, List<Evaluation>>().build()
    evaluationSharedPrefs = EvaluationSharedPrefsImpl(sharedPreferences)
    evaluationSQLDao = CountingEvaluationSQLDao(EvaluationSQLDaoImpl(openHelper, moshi))
    storage =
      EvaluationStorageImpl(
        userId,
        evaluationSQLDao,
        evaluationSharedPrefs,
        memCache,
      )
  }

  @After
  fun tearDown() {
    openHelper.close()
    deleteSharedPreferences(ApplicationProvider.getApplicationContext())
  }

  @Test
  fun `deleteAllAndInsert skips a strictly older write`() {
    storage.deleteAllAndInsert("id_1024", listOf(evaluation1), "1024")

    val result = storage.deleteAllAndInsert("id_1023", listOf(evaluation2), "1023")

    assertThat(result).isEqualTo(EvaluationWriteResult.SkippedStale)
    assertStored("id_1024", "1024", listOf(evaluation1))

    // Positive control: a newer write lands.
    val newer = storage.deleteAllAndInsert("id_1025", listOf(evaluation2), "1025")
    assertThat(newer).isEqualTo(EvaluationWriteResult.Landed(shouldNotify = true))
    assertStored("id_1025", "1025", listOf(evaluation2))
  }

  @Test
  fun `deleteAllAndInsert applies an equal evaluatedAt`() {
    storage.deleteAllAndInsert("id_a", listOf(evaluation1), "1024")

    val result = storage.deleteAllAndInsert("id_b", listOf(evaluation2), "1024")

    assertThat(result).isEqualTo(EvaluationWriteResult.Landed(shouldNotify = true))
    assertStored("id_b", "1024", listOf(evaluation2))
  }

  @Test
  fun `update skips a strictly older write before reading the database`() {
    storage.deleteAllAndInsert("id_1024", listOf(evaluation1), "1024")
    evaluationSQLDao.getCount = 0

    val result = storage.update("id_1023", listOf(evaluation2), emptyList(), "1023")

    assertThat(result).isEqualTo(EvaluationWriteResult.SkippedStale)
    assertThat(evaluationSQLDao.getCount).isEqualTo(0)
    assertStored("id_1024", "1024", listOf(evaluation1))

    // Positive control: a newer update reads the database once and merges.
    val newer = storage.update("id_1025", listOf(evaluation2), emptyList(), "1025")
    assertThat(newer).isEqualTo(EvaluationWriteResult.Landed(shouldNotify = true))
    assertThat(evaluationSQLDao.getCount).isEqualTo(1)
    assertStored("id_1025", "1025", listOf(evaluation1, evaluation2))
  }

  @Test
  fun `update applies an equal evaluatedAt`() {
    storage.deleteAllAndInsert("id_a", listOf(evaluation1), "1024")

    val result = storage.update("id_b", listOf(evaluation2), emptyList(), "1024")

    assertThat(result).isEqualTo(EvaluationWriteResult.Landed(shouldNotify = true))
    assertStored("id_b", "1024", listOf(evaluation1, evaluation2))
  }

  @Test
  fun `write is allowed when the stored evaluatedAt is unreadable`() {
    listOf("", "abc").forEach { stored ->
      evaluationSharedPrefs.evaluatedAt = stored

      val result = storage.deleteAllAndInsert("id_$stored", listOf(evaluation1), "1")

      assertThat(result).isEqualTo(EvaluationWriteResult.Landed(shouldNotify = true))
      assertStored("id_$stored", "1", listOf(evaluation1))
    }
  }

  @Test
  fun `write is allowed when the incoming evaluatedAt is unreadable`() {
    storage.deleteAllAndInsert("id_a", listOf(evaluation1), "1024")

    val result = storage.deleteAllAndInsert("id_b", listOf(evaluation2), "")

    assertThat(result).isEqualTo(EvaluationWriteResult.Landed(shouldNotify = true))
    assertStored("id_b", "", listOf(evaluation2))
  }

  @Test
  fun `an emptying snapshot still lands and notifies`() {
    storage.deleteAllAndInsert("id_a", listOf(evaluation1, evaluation2), "1024")

    val result = storage.deleteAllAndInsert("id_b", emptyList(), "1025")

    assertThat(result).isEqualTo(EvaluationWriteResult.Landed(shouldNotify = true))
    assertStored("id_b", "1025", emptyList())
  }

  private fun assertStored(
    evaluationsId: String,
    evaluatedAt: String,
    evaluations: List<Evaluation>,
  ) {
    assertThat(evaluationSharedPrefs.currentEvaluationsId).isEqualTo(evaluationsId)
    assertThat(evaluationSharedPrefs.evaluatedAt).isEqualTo(evaluatedAt)
    assertThat(evaluationSQLDao.real.get(userId)).containsExactlyElementsIn(evaluations)
    assertThat(memCache.get(userId) ?: emptyList<Evaluation>()).containsExactlyElementsIn(evaluations)
  }
}

/** Counts database reads, so a test can tell whether a write read the database first. */
private class CountingEvaluationSQLDao(
  val real: EvaluationSQLDao,
) : EvaluationSQLDao by real {
  var getCount = 0

  override fun get(userId: String): List<Evaluation> {
    getCount++
    return real.get(userId)
  }
}
