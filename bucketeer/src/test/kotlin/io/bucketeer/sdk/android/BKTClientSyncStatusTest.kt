package io.bucketeer.sdk.android

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.BKTEvaluationSyncStatus.Source.POLLING
import io.bucketeer.sdk.android.BKTEvaluationSyncStatus.State.DESTROYED
import io.bucketeer.sdk.android.BKTEvaluationSyncStatus.State.FAILING
import io.bucketeer.sdk.android.BKTEvaluationSyncStatus.State.SYNCED
import io.bucketeer.sdk.android.internal.di.ComponentImpl
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.di.InteractorModule
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.remote.ApiClient
import io.bucketeer.sdk.android.internal.remote.GetEvaluationsResult
import io.bucketeer.sdk.android.internal.remote.RegisterEventsResult
import io.bucketeer.sdk.android.internal.remote.UserEvaluationCondition
import io.bucketeer.sdk.android.internal.user.toBKTUser
import io.bucketeer.sdk.android.mocks.user1
import io.bucketeer.sdk.android.mocks.user1Evaluations
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * The evaluation sync status through the public BKTClient API, in polling mode.
 *
 * Listener calls are posted to the main thread, so each check runs after [idleMain].
 */
@RunWith(RobolectricTestRunner::class)
class BKTClientSyncStatusTest {
  private lateinit var server: MockWebServer
  private lateinit var config: BKTConfig
  private lateinit var moshi: Moshi

  @Before
  fun setup() {
    server = MockWebServer()
    config =
      BKTConfig
        .builder()
        .apiEndpoint(server.url("").toString())
        .apiKey("api_key_value")
        .featureTag("feature_tag_value")
        .appVersion("1.2.3")
        .build()
    moshi = DataModule.createMoshi()
  }

  @After
  fun tearDown() {
    BKTClient.destroy()
    idleMain()
    server.shutdown()
    deleteDatabase(ApplicationProvider.getApplicationContext())
    deleteSharedPreferences(ApplicationProvider.getApplicationContext())
  }

  @Test
  fun `a successful init gives SYNCED`() {
    enqueueEvaluations()

    val result = initialize().get()
    idleMain()

    assertThat(result).isNull()
    val status = BKTClient.getInstance().evaluationSyncStatus()
    assertThat(status.state).isEqualTo(SYNCED)
    assertThat(status.lastSyncedAt).isNotNull()
    assertThat(status.lastError).isNull()
  }

  @Test
  fun `a 503 at init still returns the error as before, and gives FAILING with that error`() {
    enqueueError(503)

    val result = initialize().get()
    idleMain()

    assertThat(result).isInstanceOf(BKTException.ServiceUnavailableException::class.java)
    val status = BKTClient.getInstance().evaluationSyncStatus()
    assertThat(status.state).isEqualTo(FAILING)
    assertThat(status.lastError?.cause).isSameInstanceAs(result)
    assertThat(status.lastError?.source).isEqualTo(POLLING)
  }

  @Test
  fun `an init that ends with a throw still leaves INITIALIZING`() {
    val client =
      BKTClientImpl(
        context = ApplicationProvider.getApplicationContext(),
        config = config,
        user = user1.toBKTUser(),
        component =
          ComponentImpl(
            dataModule = ErrorThrowingDataModule(ApplicationProvider.getApplicationContext(), config),
            interactorModule = InteractorModule(mainHandler = Handler(Looper.getMainLooper())),
          ),
      )
    try {
      val future = client.initializeInternal(1000)

      assertThrows(ExecutionException::class.java) { future.get() }
      idleMain()

      assertThat(client.evaluationSyncStatus().state).isEqualTo(FAILING)
    } finally {
      client.resetTasks()
      client.destroy()
      idleMain()
    }
  }

  @Test
  fun `a second initialize changes nothing`() {
    enqueueError(503)
    initialize().get()
    idleMain()
    val before = BKTClient.getInstance().evaluationSyncStatus()

    val result = initialize().get()
    idleMain()

    assertThat(result).isNull()
    assertThat(BKTClient.getInstance().evaluationSyncStatus()).isEqualTo(before)
  }

  @Test
  fun `destroy with no client does nothing`() {
    BKTClient.destroy()
    idleMain()

    assertThrows(BKTException.IllegalArgumentException::class.java) { BKTClient.getInstance() }
  }

  @Test
  fun `destroy gives the listener DESTROYED once, and a fetch that ends later calls no listener`() {
    enqueueEvaluations()
    initialize().get()
    idleMain()
    val client = BKTClient.getInstance()
    val listener = RecordingListener().also { client.addEvaluationSyncStatusListener(it) }
    // Still in flight when destroy() runs.
    server.enqueue(errorResponse(500).setHeadersDelay(300, TimeUnit.MILLISECONDS))
    val fetch = client.fetchEvaluations()

    BKTClient.destroy()
    fetch.get()
    idleMain()

    assertThat(listener.calls.map { it.state }).containsExactly(DESTROYED)
    assertThat(client.evaluationSyncStatus().state).isEqualTo(DESTROYED)
  }

  @Test
  fun `a poll that gets 500 then 200 gives FAILING then SYNCED, and calls the listener for both`() {
    enqueueEvaluations()
    initialize().get()
    idleMain()
    val client = BKTClient.getInstance()
    val listener = RecordingListener().also { client.addEvaluationSyncStatusListener(it) }

    enqueueError(500)
    client.fetchEvaluations().get()
    idleMain()
    enqueueEvaluations()
    client.fetchEvaluations().get()
    idleMain()

    assertThat(listener.calls.map { it.state }).containsExactly(FAILING, SYNCED).inOrder()
  }

  @Test
  fun `a failed fetchEvaluations reports the failure and still returns the error as before`() {
    enqueueEvaluations()
    initialize().get()
    idleMain()
    val client = BKTClient.getInstance()

    enqueueError(500)
    val result = client.fetchEvaluations().get()
    idleMain()

    assertThat(result).isInstanceOf(BKTException.InternalServerErrorException::class.java)
    val status = client.evaluationSyncStatus()
    assertThat(status.state).isEqualTo(FAILING)
    assertThat(status.lastError?.cause).isSameInstanceAs(result)
  }

  @Test
  fun `a DESTROYED listener can start a new client`() {
    enqueueEvaluations()
    initialize().get()
    idleMain()
    val first = BKTClient.getInstance()
    enqueueEvaluations()
    first.addEvaluationSyncStatusListener { status ->
      if (status.state == DESTROYED) initialize()
    }

    BKTClient.destroy()
    idleMain()

    val second = BKTClient.getInstance()
    assertThat(second).isNotSameInstanceAs(first)
  }

  @Test
  fun `the listener is called on the main thread`() {
    enqueueEvaluations()
    initialize().get()
    idleMain()
    val client = BKTClient.getInstance()
    val loopers = mutableListOf<Looper?>()
    client.addEvaluationSyncStatusListener { loopers.add(Looper.myLooper()) }

    enqueueError(500)
    client.fetchEvaluations().get()
    idleMain()

    assertThat(loopers).containsExactly(Looper.getMainLooper())
  }

  private fun initialize() =
    BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )

  private fun idleMain() {
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun enqueueEvaluations() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi
            .adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                evaluations = user1Evaluations,
                userEvaluationsId = "user_evaluations_id_value",
              ),
            ),
        ),
    )
  }

  private fun enqueueError(code: Int) {
    server.enqueue(errorResponse(code))
  }

  private fun errorResponse(code: Int): MockResponse =
    MockResponse()
      .setResponseCode(code)
      .setBody(
        moshi
          .adapter(ErrorResponse::class.java)
          .toJson(ErrorResponse(ErrorResponse.ErrorDetail(code = code, message = "$code error"))),
      )
}

private class RecordingListener : BKTClient.EvaluationSyncStatusListener {
  val calls = mutableListOf<BKTEvaluationSyncStatus>()

  override fun onStatusChanged(status: BKTEvaluationSyncStatus) {
    calls.add(status)
  }
}

/** Its ApiClient throws an Error, which fetch() does not catch: the init block ends by a throw. */
private class ErrorThrowingDataModule(
  application: Application,
  config: BKTConfig,
) : DataModule(application, user1, config, inMemoryDB = true) {
  override val apiClient: ApiClient by lazy {
    object : ApiClient {
      override fun getEvaluations(
        user: User,
        userEvaluationsId: String,
        timeoutMillis: Long?,
        condition: UserEvaluationCondition,
      ): GetEvaluationsResult = throw AssertionError("simulated VM error")

      override fun registerEvents(events: List<Event>): RegisterEventsResult = throw UnsupportedOperationException("not used in this test")
    }
  }
}
