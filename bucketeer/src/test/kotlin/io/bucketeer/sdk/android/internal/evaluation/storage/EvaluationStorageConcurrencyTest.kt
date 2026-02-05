package io.bucketeer.sdk.android.internal.evaluation.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
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
import io.bucketeer.sdk.android.mocks.user1
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
class EvaluationStorageConcurrencyTest {
  private lateinit var userId: String
  private lateinit var openHelper: SupportSQLiteOpenHelper
  private lateinit var evaluationSQLDao: EvaluationSQLDao
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
    evaluationSQLDao = EvaluationSQLDaoImpl(openHelper, moshi)
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
  fun `concurrent user attributes updates should ensure correct versioning`() {
    val updateCount = 100
    val startLatch = CountDownLatch(1)
    val doneLatch = CountDownLatch(updateCount)

    // Launch concurrent updates
    repeat(updateCount) {
      thread {
        startLatch.await() // Wait for start signal
        storage.setUserAttributesUpdated()
        doneLatch.countDown()
      }
    }

    startLatch.countDown() // Start all threads
    assert(doneLatch.await(10, TimeUnit.SECONDS)) { "Timeout waiting for threads to complete" }

    val state = storage.getUserAttributesState()
    assert(state.userAttributesUpdated)
    // Version should be exactly updateCount
    assert(state.version == updateCount)
  }

  @Test
  fun `race condition - update during fetch should NOT be cleared`() {
    // Simulate the race condition scenario:
    // 1. Thread 1 sets userAttributesUpdated
    // 2. Thread 2 gets the state (for fetch)
    // 3. Thread 1 sets userAttributesUpdated again
    // 4. Thread 2 clears with old state
    // Expected: userAttributesUpdated should still be true (because version doesn't match)

    val step1Done = CountDownLatch(1)
    val step2Done = CountDownLatch(1)
    val step3Done = CountDownLatch(1)
    val allDone = CountDownLatch(2)

    var capturedState: UserAttributesState? = null

    // Thread 1: Updates attributes twice
    thread {
      // Step 1: First update
      storage.setUserAttributesUpdated()
      step1Done.countDown()

      // Wait for Thread 2 to capture state
      step2Done.await()

      // Step 3: Second update (while Thread 2 is "fetching")
      storage.setUserAttributesUpdated()
      step3Done.countDown()

      allDone.countDown()
    }

    // Thread 2: Simulates fetch operation
    thread {
      // Wait for first update
      step1Done.await()

      // Step 2: Capture state (simulating start of fetch)
      capturedState = storage.getUserAttributesState()
      step2Done.countDown()

      // Wait for second update to happen
      step3Done.await()

      // Step 4: Try to clear with old state (simulating fetch completion)
      storage.clearUserAttributesUpdated(capturedState!!)

      allDone.countDown()
    }

    assert(allDone.await(10, TimeUnit.SECONDS)) { "Timeout waiting for threads to complete" }

    // The second update should NOT be cleared because version doesn't match
    val finalState = storage.getUserAttributesState()
    assert(finalState.userAttributesUpdated) {
      "userAttributesUpdated should still be true after clearing with old state"
    }
    assert(finalState.version == 2) {
      "version should be 2 (two updates), got ${finalState.version}"
    }
  }
}
