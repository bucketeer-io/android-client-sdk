package io.bucketeer.sdk.android.internal.evaluation

import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.BKTConfig
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
class EvaluationInteractorStaleFetchTest {
  private lateinit var dataModule: DataModule
  private lateinit var storage: EvaluationStorage
  private lateinit var apiClient: RecordingApiClient
  private lateinit var interactor: EvaluationInteractor
  private var updateCount = 0

  @Before
  fun setUp() {
    LoggerHolder.clearLoggers()
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
    apiClient = RecordingApiClient()
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
  fun `stale force update reply keeps the user attributes flag`() {
    storage.deleteAllAndInsert("ueid_new", listOf(evaluation1), "2000")
    interactor.setUserAttributesUpdated()
    apiClient.reply = response("ueid_old", "1000", forceUpdate = true, listOf(evaluation2))

    val result = interactor.fetch(user1, null)
    idleMainLooper()

    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
    assertThat(storage.getUserAttributesState().userAttributesUpdated).isTrue()
    assertThat(storage.get()).containsExactly(evaluation1)
    assertThat(updateCount).isEqualTo(0)

    // Positive control: a newer reply lands, clears the flag and notifies.
    apiClient.reply = response("ueid_newer", "3000", forceUpdate = true, listOf(evaluation2))
    interactor.fetch(user1, null)
    idleMainLooper()
    assertThat(storage.getUserAttributesState().userAttributesUpdated).isFalse()
    assertThat(updateCount).isEqualTo(1)
  }

  @Test
  fun `stale upsert reply keeps the user attributes flag`() {
    storage.deleteAllAndInsert("ueid_new", listOf(evaluation1), "2000")
    interactor.setUserAttributesUpdated()
    apiClient.reply = response("ueid_old", "1000", forceUpdate = false, listOf(evaluation2))

    val result = interactor.fetch(user1, null)
    idleMainLooper()

    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
    assertThat(storage.getUserAttributesState().userAttributesUpdated).isTrue()
    assertThat(storage.get()).containsExactly(evaluation1)
    assertThat(updateCount).isEqualTo(0)

    // Positive control: a newer upsert lands, clears the flag and notifies.
    apiClient.reply = response("ueid_newer", "3000", forceUpdate = false, listOf(evaluation2))
    interactor.fetch(user1, null)
    idleMainLooper()
    assertThat(storage.getUserAttributesState().userAttributesUpdated).isFalse()
    assertThat(updateCount).isEqualTo(1)
  }

  @Test
  fun `next poll after a stale reply still sends userAttributesUpdated`() {
    storage.deleteAllAndInsert("ueid_new", listOf(evaluation1), "2000")
    interactor.setUserAttributesUpdated()
    apiClient.reply = response("ueid_old", "1000", forceUpdate = true, listOf(evaluation2))

    interactor.fetch(user1, null)
    interactor.fetch(user1, null)

    assertThat(apiClient.conditions.map { it.userAttributesUpdated }).containsExactly(true, true).inOrder()
  }

  @Test
  fun `landed reply clears the user attributes flag`() {
    interactor.setUserAttributesUpdated()
    apiClient.reply = response("ueid_1", "1000", forceUpdate = true, listOf(evaluation1))

    val result = interactor.fetch(user1, null)
    idleMainLooper()

    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
    assertThat(storage.getUserAttributesState().userAttributesUpdated).isFalse()
    assertThat(storage.get()).containsExactly(evaluation1)
    assertThat(updateCount).isEqualTo(1)
  }

  @Test
  fun `failed storage write keeps the user attributes flag`() {
    val interactor = newInteractor(ThrowingWritesEvaluationStorage(storage))
    interactor.setUserAttributesUpdated()
    apiClient.reply = response("ueid_1", "1000", forceUpdate = true, listOf(evaluation1))

    val result = interactor.fetch(user1, null)
    idleMainLooper()

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    assertThat(storage.getUserAttributesState().userAttributesUpdated).isTrue()
    assertThat(updateCount).isEqualTo(0)
  }

  @Test
  fun `attributes changed during a fetch keep the flag after the reply lands`() {
    interactor.setUserAttributesUpdated()
    apiClient.reply = response("ueid_1", "1000", forceUpdate = true, listOf(evaluation1))
    // The change lands after the request was built, so this request did not carry it.
    apiClient.onRequest = { interactor.setUserAttributesUpdated() }

    val result = interactor.fetch(user1, null)
    idleMainLooper()

    // Positive control: the reply landed.
    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
    assertThat(storage.get()).containsExactly(evaluation1)
    assertThat(updateCount).isEqualTo(1)
    assertThat(storage.getUserAttributesState().userAttributesUpdated).isTrue()
  }

  private fun newInteractor(storage: EvaluationStorage): EvaluationInteractor =
    EvaluationInteractor(
      apiClient = apiClient,
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
  ): GetEvaluationsResponse =
    GetEvaluationsResponse(
      evaluations =
        UserEvaluations(
          id = userEvaluationsId,
          createdAt = createdAt,
          forceUpdate = forceUpdate,
          evaluations = evaluations,
        ),
      userEvaluationsId = userEvaluationsId,
    )

  private fun idleMainLooper() {
    shadowOf(Looper.getMainLooper()).idle()
  }
}

/** Replies with [reply] and records the condition each request carried. */
private class RecordingApiClient : ApiClient {
  lateinit var reply: GetEvaluationsResponse
  var onRequest: () -> Unit = {}
  val conditions = mutableListOf<UserEvaluationCondition>()

  override fun getEvaluations(
    user: User,
    userEvaluationsId: String,
    timeoutMillis: Long?,
    condition: UserEvaluationCondition,
  ): GetEvaluationsResult {
    conditions.add(condition)
    onRequest()
    return GetEvaluationsResult.Success(
      value = reply,
      seconds = 0.1,
      sizeByte = 100,
      featureTag = "feature_tag_value",
    )
  }

  override fun registerEvents(events: List<Event>): RegisterEventsResult = throw AssertionError("registerEvents must not be called")
}
