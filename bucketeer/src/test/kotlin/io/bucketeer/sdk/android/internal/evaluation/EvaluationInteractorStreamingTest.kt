package io.bucketeer.sdk.android.internal.evaluation

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.BKTLogger
import io.bucketeer.sdk.android.internal.IdGeneratorImpl
import io.bucketeer.sdk.android.internal.LoggerHolder
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.evaluation.storage.EvaluationStorage
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.model.UserEvaluations
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.remote.ApiClient
import io.bucketeer.sdk.android.internal.remote.GetEvaluationsResult
import io.bucketeer.sdk.android.internal.remote.RegisterEventsResult
import io.bucketeer.sdk.android.internal.remote.UserEvaluationCondition
import io.bucketeer.sdk.android.mocks.evaluation1
import io.bucketeer.sdk.android.mocks.evaluation2
import io.bucketeer.sdk.android.mocks.user1
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class EvaluationInteractorStreamingTest {
  private lateinit var dataModule: DataModule
  private lateinit var storage: EvaluationStorage
  private lateinit var interactor: EvaluationInteractor
  private lateinit var logger: RecordingLogger
  private var updateCount = 0

  @Before
  fun setUp() {
    LoggerHolder.clearLoggers()
    logger = RecordingLogger()
    LoggerHolder.addLogger(logger)

    val config =
      BKTConfig
        .builder()
        .apiEndpoint("https://example.com")
        .apiKey("api_key_value")
        .featureTag("feature_tag_value")
        .appVersion("1.2.3")
        .build()
    dataModule =
      DataModule(
        application = ApplicationProvider.getApplicationContext(),
        user = user1,
        config = config,
        inMemoryDB = true,
      )
    storage = dataModule.evaluationStorage
    interactor = newInteractor(storage)
    updateCount = 0
  }

  @After
  fun tearDown() {
    dataModule.sharedPreferences
      .edit()
      .clear()
      .commit()
    dataModule.destroy()
    LoggerHolder.clearLoggers()
  }

  @Test
  fun `streamed upsert is stored and notifies`() {
    interactor.applyStreamedEvaluations(response("ueid_1", "1000", forceUpdate = false, listOf(evaluation1)))
    idleMainLooper()

    assertThat(interactor.getLatest(evaluation1.featureId)).isEqualTo(evaluation1)
    assertThat(updateCount).isEqualTo(1)
  }

  @Test
  fun `streamed force update replaces everything and notifies`() {
    storage.deleteAllAndInsert("ueid_0", listOf(evaluation1), "1000")

    interactor.applyStreamedEvaluations(response("ueid_1", "1001", forceUpdate = true, listOf(evaluation2)))
    idleMainLooper()

    assertThat(storage.get()).containsExactly(evaluation2)
    assertThat(interactor.getLatest(evaluation1.featureId)).isNull()
    assertThat(updateCount).isEqualTo(1)
  }

  @Test
  fun `streamed upsert with nothing changed does not notify`() {
    storage.deleteAllAndInsert("ueid_0", listOf(evaluation1), "1000")

    interactor.applyStreamedEvaluations(response("ueid_1", "1001", forceUpdate = false, emptyList()))
    idleMainLooper()

    assertThat(storage.getCurrentEvaluationId()).isEqualTo("ueid_1")
    assertThat(storage.getEvaluatedAt()).isEqualTo("1001")
    assertThat(updateCount).isEqualTo(0)

    // Positive control: an upsert that changes something notifies.
    interactor.applyStreamedEvaluations(response("ueid_2", "1002", forceUpdate = false, listOf(evaluation2)))
    idleMainLooper()
    assertThat(updateCount).isEqualTo(1)
  }

  @Test
  fun `streamed payload with unreadable createdAt is dropped`() {
    storage.deleteAllAndInsert("ueid_0", listOf(evaluation1), "1000")

    interactor.applyStreamedEvaluations(response("ueid_bad", "", forceUpdate = true, listOf(evaluation2)))
    idleMainLooper()

    assertThat(storage.getCurrentEvaluationId()).isEqualTo("ueid_0")
    assertThat(storage.getEvaluatedAt()).isEqualTo("1000")
    assertThat(storage.get()).containsExactly(evaluation1)
    assertThat(updateCount).isEqualTo(0)
    assertThat(logger.messages(Log.WARN).any { it.contains("ueid_bad") }).isTrue()

    // Positive control: the same payload with a readable createdAt is applied.
    interactor.applyStreamedEvaluations(response("ueid_good", "1001", forceUpdate = true, listOf(evaluation2)))
    idleMainLooper()
    assertThat(storage.get()).containsExactly(evaluation2)
    assertThat(updateCount).isEqualTo(1)
  }

  @Test
  fun `shouldNotify false suppresses listeners but the write lands`() {
    interactor.applyStreamedEvaluations(
      response("ueid_1", "1000", forceUpdate = false, listOf(evaluation1)),
      shouldNotify = { false },
    )
    idleMainLooper()

    assertThat(interactor.getLatest(evaluation1.featureId)).isEqualTo(evaluation1)
    assertThat(updateCount).isEqualTo(0)
  }

  @Test
  fun `shouldNotify is read on the main thread when listeners run`() {
    var allowed = true
    interactor.applyStreamedEvaluations(
      response("ueid_1", "1000", forceUpdate = false, listOf(evaluation1)),
      shouldNotify = { allowed },
    )
    // Turned off after the write, before the posted listener call runs.
    allowed = false
    idleMainLooper()

    assertThat(interactor.getLatest(evaluation1.featureId)).isEqualTo(evaluation1)
    assertThat(updateCount).isEqualTo(0)

    // Positive control: left on, the listener runs.
    interactor.applyStreamedEvaluations(
      response("ueid_2", "1001", forceUpdate = false, listOf(evaluation2)),
      shouldNotify = { true },
    )
    idleMainLooper()
    assertThat(updateCount).isEqualTo(1)
  }

  @Test
  fun `stale streamed write does not notify and never asks shouldNotify`() {
    storage.deleteAllAndInsert("ueid_0", listOf(evaluation1), "2000")
    var shouldNotifyCalls = 0
    val shouldNotify = {
      shouldNotifyCalls++
      true
    }

    interactor.applyStreamedEvaluations(
      response("ueid_old", "1000", forceUpdate = true, listOf(evaluation2)),
      shouldNotify = shouldNotify,
    )
    idleMainLooper()

    assertThat(storage.getCurrentEvaluationId()).isEqualTo("ueid_0")
    assertThat(storage.getEvaluatedAt()).isEqualTo("2000")
    assertThat(storage.get()).containsExactly(evaluation1)
    assertThat(updateCount).isEqualTo(0)
    assertThat(shouldNotifyCalls).isEqualTo(0)

    // Positive control: a newer payload lands, asks once and notifies.
    interactor.applyStreamedEvaluations(
      response("ueid_new", "3000", forceUpdate = true, listOf(evaluation2)),
      shouldNotify = shouldNotify,
    )
    idleMainLooper()
    assertThat(storage.get()).containsExactly(evaluation2)
    assertThat(updateCount).isEqualTo(1)
    assertThat(shouldNotifyCalls).isEqualTo(1)
  }

  @Test
  fun `streamed data never clears the user attributes flag`() {
    interactor.setUserAttributesUpdated()

    interactor.applyStreamedEvaluations(response("ueid_1", "1000", forceUpdate = false, listOf(evaluation1)))
    idleMainLooper()

    // Positive control: the payload landed.
    assertThat(interactor.getLatest(evaluation1.featureId)).isEqualTo(evaluation1)
    assertThat(updateCount).isEqualTo(1)
    assertThat(interactor.userAttributesState.userAttributesUpdated).isTrue()
  }

  @Test
  fun `storage error during a streamed write is swallowed`() {
    val throwingStorage = ThrowingWritesEvaluationStorage(storage)
    val interactor = newInteractor(throwingStorage)

    interactor.applyStreamedEvaluations(response("ueid_1", "1000", forceUpdate = true, listOf(evaluation1)))
    idleMainLooper()

    assertThat(updateCount).isEqualTo(0)
    assertThat(logger.throwables(Log.ERROR)).contains(throwingStorage.error)
  }

  @Test
  fun `cachedEvaluationsState reflects storage`() {
    assertThat(interactor.cachedEvaluationsState).isEqualTo(CachedEvaluationsState("", "0"))

    storage.deleteAllAndInsert("ueid_1", listOf(evaluation1), "1234")

    assertThat(interactor.cachedEvaluationsState).isEqualTo(
      CachedEvaluationsState(userEvaluationsId = "ueid_1", evaluatedAt = "1234"),
    )
  }

  @Test
  fun `listener exception during a streamed notify is reported, not thrown`() {
    val errors = mutableListOf<BKTException>()
    interactor.setErrorListener { errors.add(it) }
    interactor.addUpdateListener { throw RuntimeException("listener failed") }

    interactor.applyStreamedEvaluations(response("ueid_1", "1000", forceUpdate = false, listOf(evaluation1)))
    idleMainLooper()

    assertThat(errors).hasSize(1)
    assertThat(errors[0]).isInstanceOf(BKTException.IllegalStateException::class.java)
  }

  private fun newInteractor(storage: EvaluationStorage): EvaluationInteractor =
    EvaluationInteractor(
      apiClient = UnusedApiClient(),
      evaluationStorage = storage,
      idGenerator = IdGeneratorImpl(),
      featureTag = "feature_tag_value",
      mainHandler = Handler(Looper.getMainLooper()),
    ).also { it.addUpdateListener { updateCount++ } }

  private fun response(
    userEvaluationsId: String,
    createdAt: String,
    forceUpdate: Boolean,
    evaluations: List<Evaluation>,
    archivedFeatureIds: List<String> = emptyList(),
  ): GetEvaluationsResponse =
    GetEvaluationsResponse(
      evaluations =
        UserEvaluations(
          id = userEvaluationsId,
          createdAt = createdAt,
          forceUpdate = forceUpdate,
          evaluations = evaluations,
          archivedFeatureIds = archivedFeatureIds,
        ),
      userEvaluationsId = userEvaluationsId,
    )

  private fun idleMainLooper() {
    shadowOf(Looper.getMainLooper()).idle()
  }
}

private class RecordingLogger : BKTLogger {
  private val entries = mutableListOf<Triple<Int, String?, Throwable?>>()

  override fun log(
    priority: Int,
    messageCreator: (() -> String?)?,
    throwable: Throwable?,
  ) {
    synchronized(entries) {
      entries.add(Triple(priority, messageCreator?.invoke(), throwable))
    }
  }

  fun messages(priority: Int): List<String> = synchronized(entries) { entries.filter { it.first == priority }.mapNotNull { it.second } }

  fun throwables(priority: Int): List<Throwable> = synchronized(entries) { entries.filter { it.first == priority }.mapNotNull { it.third } }
}

/** The streaming path never polls. */
private class UnusedApiClient : ApiClient {
  override fun getEvaluations(
    user: User,
    userEvaluationsId: String,
    timeoutMillis: Long?,
    condition: UserEvaluationCondition,
  ): GetEvaluationsResult = throw AssertionError("getEvaluations must not be called")

  override fun registerEvents(events: List<Event>): RegisterEventsResult = throw AssertionError("registerEvents must not be called")
}
