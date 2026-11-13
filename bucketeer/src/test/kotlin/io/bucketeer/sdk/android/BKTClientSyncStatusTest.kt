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
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CountDownLatch
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
  fun `destroy while the first init fetch waits for a 200 response gives DESTROYED once, and init returns no error`() {
    val result = destroyWhileFirstInitFetchWaits(evaluationsResponse())

    assertThat(result).isNull()
  }

  @Test
  fun `destroy while the first init fetch waits for a 500 response gives DESTROYED once, and init returns the error`() {
    val result = destroyWhileFirstInitFetchWaits(errorResponse(500))

    assertThat(result).isInstanceOf(BKTException.InternalServerErrorException::class.java)
  }

  /**
   * Destroys the client while the first init fetch waits for its response, then releases the
   * response. Checks that DESTROYED is the only listener call and that the status keeps no sync
   * time and no error. Returns what init returned.
   */
  private fun destroyWhileFirstInitFetchWaits(response: MockResponse): BKTException? {
    val held = holdResponse(response)
    val init = initialize()
    val client = BKTClient.getInstance()
    val listener = RecordingListener().also { client.addEvaluationSyncStatusListener(it) }

    // Destroy only after the request reached the server, so init waits for the first fetch.
    assertThat(held.awaitRequest()).isTrue()
    BKTClient.destroy()
    idleMain()
    assertThat(listener.calls.map { it.state }).containsExactly(DESTROYED)

    held.release()
    // The init future completes only after its finally block ran, so the checks below see every
    // listener call that init can make.
    val result = init.get(5, TimeUnit.SECONDS)
    idleMain()

    assertThat(listener.calls.map { it.state }).containsExactly(DESTROYED)
    val status = client.evaluationSyncStatus()
    assertThat(status.state).isEqualTo(DESTROYED)
    assertThat(status.lastSyncedAt).isNull()
    assertThat(status.lastError).isNull()
    return result
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
  fun `destroy gives the listener DESTROYED once, and a fetch that ends later calls no listener and changes nothing`() {
    enqueueEvaluations()
    initialize().get()
    idleMain()
    val client = BKTClient.getInstance()
    val listener = RecordingListener().also { client.addEvaluationSyncStatusListener(it) }
    val held = holdResponse(errorResponse(500))
    val fetch = client.fetchEvaluations()

    // Destroy only after the request reached the server, so the fetch ends after destroy().
    assertThat(held.awaitRequest()).isTrue()
    BKTClient.destroy()
    val destroyed = client.evaluationSyncStatus()
    held.release()
    fetch.get(5, TimeUnit.SECONDS)
    idleMain()

    assertThat(destroyed.state).isEqualTo(DESTROYED)
    assertThat(listener.calls).containsExactly(destroyed)
    // The whole status, not only the state: a result after destroy must not move lastError or
    // lastSyncedAt either.
    assertThat(client.evaluationSyncStatus()).isEqualTo(destroyed)
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

  /** From now on, every request gets [response], but only after [HeldResponse.release]. */
  private fun holdResponse(response: MockResponse): HeldResponse = HeldResponse(response).also { server.dispatcher = it }

  private fun enqueueEvaluations() {
    server.enqueue(evaluationsResponse())
  }

  private fun evaluationsResponse(): MockResponse =
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
      )

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

private class HeldResponse(
  private val response: MockResponse,
) : Dispatcher() {
  private val requestReceived = CountDownLatch(1)
  private val releaseResponse = CountDownLatch(1)

  override fun dispatch(request: RecordedRequest): MockResponse {
    requestReceived.countDown()
    releaseResponse.await(5, TimeUnit.SECONDS)
    return response
  }

  /** True once the server got a request. */
  fun awaitRequest(): Boolean = requestReceived.await(5, TimeUnit.SECONDS)

  fun release() {
    releaseResponse.countDown()
  }
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
