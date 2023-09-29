package io.bucketeer.sdk.android.internal.evaluation.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.database.OpenHelperCallback
import io.bucketeer.sdk.android.internal.database.createDatabase
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.evaluation.cache.EvaluationSharedPrefs
import io.bucketeer.sdk.android.internal.evaluation.cache.EvaluationSharedPrefsImpl
import io.bucketeer.sdk.android.internal.evaluation.cache.MemCache
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationSQLDao
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationSQLDaoImpl
import io.bucketeer.sdk.android.internal.model.Evaluation
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
class EvaluationStorageImplTest {
  private lateinit var userId: String
  private lateinit var openHelper: SupportSQLiteOpenHelper
  private lateinit var evaluationSQLDao: EvaluationSQLDao
  private lateinit var evaluationSharedPrefs: EvaluationSharedPrefs
  private lateinit var memCache: MemCache<String, List<Evaluation>>
  private lateinit var evaluationStorage: EvaluationStorage

  @Before
  fun setUp() {
    userId = user1.id
    val moshi = DataModule.createMoshi()
    val context: Context = ApplicationProvider.getApplicationContext()
    val sharedPreferences = context.getSharedPreferences(
      Constants.PREFERENCES_NAME,
      Context.MODE_PRIVATE,
    )
    openHelper = createDatabase(context, OpenHelperCallback.FILE_NAME, sharedPreferences)
    memCache = MemCache.Builder<String, List<Evaluation>>().build()
    evaluationSharedPrefs = EvaluationSharedPrefsImpl(sharedPreferences)
    evaluationSQLDao = EvaluationSQLDaoImpl(openHelper, moshi)
    evaluationStorage = EvaluationStorageImpl(
      userId,
      evaluationSQLDao,
      evaluationSharedPrefs,
      memCache,
    )

  }

  @After
  fun tearDown() {
    openHelper.close()
  }

  @Test
  fun getByFeatureId() {
    evaluationStorage.deleteAllAndInsert(listOf(evaluation1, evaluation2), "13345")
    assert(evaluationStorage.getBy(featureId = "test-feature-1") == evaluation1)
    assert(evaluationStorage.getBy(featureId = "test-feature-2") == evaluation2)
    assert(evaluationStorage.evaluatedAt == "13345")
  }

  @Test
  fun getByUserId() {
    assert(evaluationStorage.get().isEmpty())
    evaluationStorage.deleteAllAndInsert(listOf(evaluation1, evaluation2), "13346")
    assert(evaluationStorage.get() == listOf(evaluation1, evaluation2))
    assert(evaluationStorage.evaluatedAt == "13346")
  }

  @Test
  fun `deleteAllAndInsert - insert`() {
    assert(evaluationStorage.get().isEmpty())
    evaluationStorage.deleteAllAndInsert(listOf(evaluation1), "13346")
    assert(evaluationStorage.get() == listOf(evaluation1))
    assert(evaluationStorage.evaluatedAt == "13346")
    evaluationStorage.deleteAllAndInsert(listOf(evaluation1, evaluation2), "13347")
    assert(evaluationStorage.get() == listOf(evaluation1, evaluation2))
    assert(evaluationStorage.evaluatedAt == "13347")

    // check underlying storage
    assert(evaluationSQLDao.get(userId) == listOf(evaluation1, evaluation2))
    assert(memCache.get(userId) == listOf(evaluation1, evaluation2))
  }

  @Test
  fun `deleteAllAndInsert - remove old items`() {
    assert(evaluationStorage.get().isEmpty())
    evaluationStorage.deleteAllAndInsert(listOf(evaluation1), "13346")
    assert(evaluationStorage.get() == listOf(evaluation1))
    assert(evaluationStorage.evaluatedAt == "13346")
    evaluationStorage.deleteAllAndInsert(listOf(evaluation2), "13347")
    assert(evaluationStorage.get() == listOf(evaluation2))
    assert(evaluationStorage.evaluatedAt == "13347")

    // check underlying storage
    assert(evaluationSQLDao.get(userId) == listOf(evaluation2))
    assert(memCache.get(userId) == listOf(evaluation2))
  }

  @Test
  fun `deleteAllAndInsert - should not return or affected other user's item`() {
    evaluationSQLDao.put(user2.id, listOf(evaluation3, evaluation4))
    evaluationStorage.deleteAllAndInsert(listOf(evaluation1, evaluation2), "13347")
    assert(evaluationStorage.get() == listOf(evaluation1, evaluation2))
    assert(evaluationStorage.evaluatedAt == "13347")

    // check underlying storage
    assert(evaluationSQLDao.get(user2.id) == listOf(evaluation3, evaluation4))
  }

  @Test
  fun testStorageValues() {
    assert(evaluationStorage.userId == userId)
    assert(evaluationStorage.currentEvaluationsId == "")
    assert(evaluationStorage.evaluatedAt == "0")
    assert(evaluationStorage.featureTag == "")
    assert(!evaluationStorage.userAttributesUpdated)

    evaluationStorage.featureTag = "tag1"
    evaluationStorage.currentEvaluationsId = "id_001"
    evaluationStorage.userAttributesUpdated = true
    evaluationStorage.deleteAllAndInsert(listOf(), evaluatedAt = "10001")

    assert(evaluationStorage.featureTag == "tag1")
    assert(evaluationStorage.currentEvaluationsId == "id_001")
    assert(evaluationStorage.userAttributesUpdated)
    assert(evaluationStorage.evaluatedAt == "10001")

    // check underlying storage
    assert(evaluationSharedPrefs.featureTag == "tag1")
    assert(evaluationSharedPrefs.currentEvaluationsId  == "id_001")
    assert(evaluationSharedPrefs.userAttributesUpdated)
    assert(evaluationSharedPrefs.evaluatedAt == "10001")

    evaluationStorage.userAttributesUpdated = false
    assert(!evaluationStorage.userAttributesUpdated)
  }

}

