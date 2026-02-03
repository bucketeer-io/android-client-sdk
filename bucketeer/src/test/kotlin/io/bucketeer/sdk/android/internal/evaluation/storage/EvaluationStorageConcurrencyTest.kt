package io.bucketeer.sdk.android.internal.evaluation.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.cache.MemCache
import io.bucketeer.sdk.android.internal.database.OpenHelperCallback
import io.bucketeer.sdk.android.internal.database.createDatabase
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.evaluation.cache.EvaluationSharedPrefsImpl
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationSQLDaoImpl
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.mocks.user1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch

@RunWith(RobolectricTestRunner::class)
class EvaluationStorageConcurrencyTest {
  private lateinit var storage: EvaluationStorageImpl
  private lateinit var openHelper: androidx.sqlite.db.SupportSQLiteOpenHelper

  @Before
  fun setUp() {
    val context: Context = ApplicationProvider.getApplicationContext()
    val sharedPreferences = context.getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE)
    openHelper = createDatabase(context, OpenHelperCallback.FILE_NAME, sharedPreferences)
    val evaluationSharedPrefs = EvaluationSharedPrefsImpl(sharedPreferences)
    val evaluationSQLDao = EvaluationSQLDaoImpl(openHelper, DataModule.createMoshi())
    val memCache = MemCache.Builder<String, List<Evaluation>>().build()

    storage =
      EvaluationStorageImpl(
        userId = user1.id,
        evaluationSQLDao = evaluationSQLDao,
        evaluationSharedPrefs = evaluationSharedPrefs,
        memCache = memCache,
      )
  }

  @After
  fun tearDown() {
    openHelper.close()
  }

  @Test
  fun `concurrent user attributes updates should correct versioning`() =
    runBlocking {
      val updateCount = 100
      val latch = CountDownLatch(1)

      // Launch concurrent updates
      val jobs =
        (1..updateCount).map {
          async(Dispatchers.IO) {
            latch.await() // Wait for start signal
            storage.setUserAttributesUpdated()
          }
        }

      latch.countDown() // Start all threads
      jobs.awaitAll()

      val state = storage.getUserAttributesState()
      assert(state.userAttributesUpdated)
      // Version should be at least updateCount (or close to it dependent on initial value, but definitely incremented)
      assert(state.version >= updateCount)
    }

  @Test
  fun `race condition - update during fetch should NOT be cleared`() =
    runBlocking {
      // 1. Initial State: Updated = true (v1)
      storage.setUserAttributesUpdated()
      val stateV1 = storage.getUserAttributesState()
      assert(stateV1.userAttributesUpdated)

      // 2. Simulate Fetch Start (capture stateV1)
      // ... logic inside Interactor would capture stateV1 ...

      // 3. Concurrent Update happens (v2)
      storage.setUserAttributesUpdated()
      val stateV2 = storage.getUserAttributesState()
      assert(stateV2.version > stateV1.version)

      // 4. Fetch Completes: Attempt to clear using captured stateV1
      storage.clearUserAttributesUpdated(stateV1)

      // 5. Result: Should STILL be updated because v2 > v1
      assert(storage.getUserAttributesUpdated()) { "Should remain updated because a newer update occurred" }

      // 6. Finally clear using latest state
      storage.clearUserAttributesUpdated(stateV2)
      assert(!storage.getUserAttributesUpdated()) { "Should be cleared now" }
    }
}
