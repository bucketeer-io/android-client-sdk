package io.bucketeer.sdk.android.internal.evaluation.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
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
import io.bucketeer.sdk.android.mocks.evaluation2ForUpdate
import io.bucketeer.sdk.android.mocks.evaluation3
import io.bucketeer.sdk.android.mocks.evaluation4
import io.bucketeer.sdk.android.mocks.evaluationForTestInsert
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
    val sharedPreferences =
      context.getSharedPreferences(
        Constants.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
      )
    openHelper = createDatabase(context, OpenHelperCallback.FILE_NAME, sharedPreferences)
    memCache = MemCache.Builder<String, List<Evaluation>>().build()
    evaluationSharedPrefs = EvaluationSharedPrefsImpl(sharedPreferences)
    evaluationSQLDao = EvaluationSQLDaoImpl(openHelper, moshi)
    evaluationStorage =
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
  }

  @Test
  fun getCurrentUserId() {
    assert(evaluationStorage.userId == userId)
  }

  @Test
  fun testClearCurrentEvaluation() {
    evaluationSharedPrefs.currentEvaluationsId = "evaluation_1"
    assert(evaluationStorage.getCurrentEvaluationId() == "evaluation_1")
    evaluationStorage.clearCurrentEvaluationId()
    assert(evaluationStorage.getCurrentEvaluationId() == "")
  }

  fun testClearUserAttributesUpdated() {
    evaluationSharedPrefs.userAttributesUpdated = true
    assert(evaluationStorage.getUserAttributesUpdated())
    evaluationStorage.clearUserAttributesUpdated()
    assert(!evaluationStorage.getUserAttributesUpdated())
  }

  @Test
  fun getByFeatureId() {
    evaluationStorage.deleteAllAndInsert("2234", listOf(evaluation1, evaluation2), "13345")
    assert(evaluationStorage.getBy(featureId = "test-feature-1") == evaluation1)
    assert(evaluationStorage.getBy(featureId = "test-feature-2") == evaluation2)
  }

  @Test
  fun getByUserId() {
    assert(evaluationStorage.get().isEmpty())
    evaluationStorage.deleteAllAndInsert("2235", listOf(evaluation1, evaluation2), "13346")
    assert(evaluationStorage.get() == listOf(evaluation1, evaluation2))
  }

  @Test
  fun `deleteAllAndInsert - insert`() {
    assert(evaluationStorage.get().isEmpty())
    evaluationStorage.deleteAllAndInsert("2235", listOf(evaluation1), "13346")
    assert(evaluationStorage.get() == listOf(evaluation1))
    assert(evaluationStorage.getEvaluatedAt() == "13346")
    assert(evaluationStorage.getCurrentEvaluationId() == "2235")

    evaluationStorage.deleteAllAndInsert("2236", listOf(evaluation1, evaluation2), "13347")
    assert(evaluationStorage.get() == listOf(evaluation1, evaluation2))
    assert(evaluationStorage.getEvaluatedAt() == "13347")
    assert(evaluationStorage.getCurrentEvaluationId() == "2236")

    // check underlying storage
    assert(evaluationSQLDao.get(userId) == listOf(evaluation1, evaluation2))
    assert(memCache.get(userId) == listOf(evaluation1, evaluation2))
  }

  @Test
  fun `deleteAllAndInsert - remove old items`() {
    assert(evaluationStorage.get().isEmpty())
    evaluationStorage.deleteAllAndInsert("2236", listOf(evaluation1), "13346")
    assert(evaluationStorage.get() == listOf(evaluation1))
    assert(evaluationStorage.getEvaluatedAt() == "13346")
    assert(evaluationStorage.getCurrentEvaluationId() == "2236")

    evaluationStorage.deleteAllAndInsert("2237", listOf(evaluation2), "13347")
    assert(evaluationStorage.get() == listOf(evaluation2))
    assert(evaluationStorage.getEvaluatedAt() == "13347")
    assert(evaluationStorage.getCurrentEvaluationId() == "2237")

    // check underlying storage
    assert(evaluationSQLDao.get(userId) == listOf(evaluation2))
    assert(memCache.get(userId) == listOf(evaluation2))
  }

  @Test
  fun `deleteAllAndInsert - should not return or affected other user's item`() {
    evaluationSQLDao.put(user2.id, listOf(evaluation3, evaluation4))
    evaluationStorage.deleteAllAndInsert("2238", listOf(evaluation1, evaluation2), "13347")
    assert(evaluationStorage.get() == listOf(evaluation1, evaluation2))
    assert(evaluationStorage.getEvaluatedAt() == "13347")
    assert(evaluationStorage.getCurrentEvaluationId() == "2238")
    // check underlying storage
    assert(evaluationSQLDao.get(user2.id) == listOf(evaluation3, evaluation4))
  }

  @Test
  fun testStorageValuesAfterDeleteAllAndInsert() {
    assert(evaluationStorage.userId == userId)
    assert(evaluationStorage.getCurrentEvaluationId() == "")
    assert(evaluationStorage.getEvaluatedAt() == "0")
    assert(evaluationStorage.getFeatureTag() == "")
    assert(!evaluationStorage.getUserAttributesUpdated())

    evaluationStorage.setFeatureTag("tag1")
    evaluationStorage.setUserAttributesUpdated()
    evaluationStorage.deleteAllAndInsert("2239", listOf(evaluation1, evaluation2), evaluatedAt = "10001")
    assert(evaluationStorage.get() == listOf(evaluation1, evaluation2))
    assert(evaluationStorage.getCurrentEvaluationId() == "2239")
    assert(evaluationStorage.getFeatureTag() == "tag1")
    assert(evaluationStorage.getUserAttributesUpdated())
    assert(evaluationStorage.getEvaluatedAt() == "10001")

    // check underlying storage
    assert(evaluationSharedPrefs.currentEvaluationsId == "2239")
    assert(evaluationSharedPrefs.featureTag == "tag1")
    assert(evaluationSharedPrefs.userAttributesUpdated)
    assert(evaluationSharedPrefs.evaluatedAt == "10001")
  }

  @Test
  fun testStorageValuesAfterUpdate() {
    testStorageValuesAfterDeleteAllAndInsert()

    evaluationStorage.setFeatureTag("tag1")
    evaluationStorage.setUserAttributesUpdated()
    evaluationStorage.update("2249", listOf(evaluationForTestInsert), listOf(), evaluatedAt = "10002")
    assert(evaluationStorage.get() == listOf(evaluation1, evaluation2, evaluationForTestInsert))
    assert(evaluationStorage.getCurrentEvaluationId() == "2249")
    assert(evaluationStorage.getFeatureTag() == "tag1")
    assert(evaluationStorage.getUserAttributesUpdated())
    assert(evaluationStorage.getEvaluatedAt() == "10002")

    // check underlying storage
    assert(evaluationSharedPrefs.currentEvaluationsId == "2249")
    assert(evaluationSharedPrefs.featureTag == "tag1")
    assert(evaluationSharedPrefs.userAttributesUpdated)
    assert(evaluationSharedPrefs.evaluatedAt == "10002")
  }

  @Test
  fun refreshCache() {
    evaluationSQLDao.put(userId, listOf(evaluation1, evaluation2))
    val evaluationStorage =
      EvaluationStorageImpl(
        userId,
        evaluationSQLDao,
        evaluationSharedPrefs,
        memCache,
      )
    evaluationStorage.refreshCache()
    assert(evaluationStorage.get() == listOf(evaluation1, evaluation2))
  }

  fun `upsert - insert new`() {
    assert(
      evaluationStorage.update(
        "2240",
        listOf(evaluation1, evaluation2),
        emptyList(),
        "12340",
      ),
    )
    assert(evaluationStorage.get() == listOf(evaluation1, evaluation2))

    // check underlying storage
    assert(evaluationSQLDao.get(userId) == listOf(evaluation1, evaluation2))
    assert(memCache.get(userId) == listOf(evaluation1, evaluation2))
  }

  @Test
  fun `upsert - update one and remove one`() {
    evaluationSQLDao.put(userId, listOf(evaluation1, evaluation2))
    assert(
      evaluationStorage.update(
        "2260",
        listOf(evaluation2ForUpdate),
        listOf(evaluation1.featureId),
        "12341",
      ),
    )
    assert(evaluationStorage.get() == listOf(evaluation2ForUpdate))

    // check underlying storage
    assert(evaluationSQLDao.get(userId) == listOf(evaluation2ForUpdate))
    assert(memCache.get(userId) == listOf(evaluation2ForUpdate))
  }

  @Test
  fun `upsert - update one, remove one, insert new one`() {
    evaluationSQLDao.put(userId, listOf(evaluation1, evaluation2))
    assert(
      evaluationStorage.update(
        "2230",
        listOf(evaluation2ForUpdate, evaluationForTestInsert),
        listOf(evaluation1.featureId),
        "12342",
      ),
    )
    assert(evaluationStorage.get() == listOf(evaluation2ForUpdate, evaluationForTestInsert))

    // check underlying storage
    assert(evaluationSQLDao.get(userId) == listOf(evaluation2ForUpdate, evaluationForTestInsert))
    assert(memCache.get(userId) == listOf(evaluation2ForUpdate, evaluationForTestInsert))
  }

  @Test
  fun `upsert - remove all, insert new one`() {
    evaluationSQLDao.put(userId, listOf(evaluation1, evaluation2))
    assert(
      evaluationStorage.update(
        "22224",
        listOf(evaluationForTestInsert),
        listOf(evaluation1.featureId, evaluation2.featureId),
        "12343",
      ),
    )
    assert(evaluationStorage.get() == listOf(evaluationForTestInsert))

    // check underlying storage
    assert(evaluationSQLDao.get(userId) == listOf(evaluationForTestInsert))
    assert(memCache.get(userId) == listOf(evaluationForTestInsert))
  }
}
