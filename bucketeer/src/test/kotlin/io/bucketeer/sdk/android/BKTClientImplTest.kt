package io.bucketeer.sdk.android

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.internal.di.ComponentImpl
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.EventData
import io.bucketeer.sdk.android.internal.model.EventType
import io.bucketeer.sdk.android.internal.model.MetricsEventData
import io.bucketeer.sdk.android.internal.model.MetricsEventType
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.model.UserEvaluations
import io.bucketeer.sdk.android.internal.model.request.RegisterEventsRequest
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsDataResponse
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsDataResponse
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsResponse
import io.bucketeer.sdk.android.internal.user.toBKTUser
import io.bucketeer.sdk.android.mocks.evaluation1
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
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class BKTClientImplTest {
  private lateinit var server: MockWebServer

  private lateinit var config: BKTConfig

  private lateinit var moshi: Moshi

  @Before
  fun setup() {
    server = MockWebServer()

    config = BKTConfig.builder()
      .endpoint(server.url("").toString())
      .apiKey("api_key_value")
      .featureTag("feature_tag_value")
      .build()

    moshi = DataModule.createMoshi()
  }

  @After
  fun tearDown() {
    server.shutdown()

    deleteDatabase(ApplicationProvider.getApplicationContext())
    deleteSharedPreferences(ApplicationProvider.getApplicationContext())

    BKTClient.destroy()
  }

  @Test
  fun `initialize - first call - success`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                GetEvaluationsDataResponse(
                  evaluations = user1Evaluations,
                  user_evaluations_id = "user_evaluations_id_value",
                ),
              ),
            ),
        ),
    )

    val future = BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )

    val result = future.get()

    // success
    assertThat(result).isNull()

    assertThat(server.requestCount).isEqualTo(1)

    val client = BKTClient.getInstance() as BKTClientImpl

    assertThat(client.componentImpl.evaluationInteractor.evaluations)
      .isEqualTo(mapOf(user1.id to user1Evaluations.evaluations))

    assertThat(client.componentImpl.dataModule.evaluationDao.get(user1.id))
      .isEqualTo(user1Evaluations.evaluations)

    Thread.sleep(100)

    val dbEvents = client.componentImpl.dataModule.eventDao.getEvents()
    assertThat(dbEvents).hasSize(2)
    assertGetEvaluationLatencyMetricsEvent(dbEvents[0], mapOf("tag" to config.featureTag))
    assertGetEvaluationSizeMetricsEvent(
      dbEvents[1],
      MetricsEventData.GetEvaluationSizeMetricsEvent(mapOf("tag" to config.featureTag), 734),
    )
  }

  @Test
  fun `initialize - first call - timeout`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBodyDelay(2, TimeUnit.SECONDS)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                GetEvaluationsDataResponse(
                  evaluations = user1Evaluations,
                  user_evaluations_id = "user_evaluations_id_value",
                ),
              ),
            ),
        ),
    )

    val future = BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )

    val result = future.get()

    // failure
    assertThat(result).isInstanceOf(BKTException.TimeoutException::class.java)

    assertThat(server.requestCount).isEqualTo(1)

    val client = BKTClient.getInstance() as BKTClientImpl

    assertThat(client.componentImpl.evaluationInteractor.evaluations)
      .isEqualTo(mapOf(user1.id to emptyList<Evaluation>()))
    assertThat(client.componentImpl.dataModule.evaluationDao.get(user1.id)).isEmpty()

    Thread.sleep(100)

    // timeout event should be saved
    val dbEvents = client.componentImpl.dataModule.eventDao.getEvents()
    assertThat(dbEvents).hasSize(1)
    assertTimeoutErrorCountMetricsEvent(
      dbEvents[0],
      MetricsEventData.TimeoutErrorCountMetricsEvent(config.featureTag),
    )
  }

  @Test
  fun `initialize - second call`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBodyDelay(500, TimeUnit.MILLISECONDS)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                GetEvaluationsDataResponse(
                  evaluations = user1Evaluations,
                  user_evaluations_id = "user_evaluations_id_value",
                ),
              ),
            ),
        ),
    )
    server.enqueue(MockResponse().setResponseCode(500).setBody("500 error"))

    val future1 = BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )

    val future2 = BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )

    // future1 has not finished yet
    assertThat(future1.isDone).isFalse()

    // second call should finish immediately
    assertThat(future2.isDone).isTrue()
    assertThat(future2.get()).isNull()

    assertThat(future1.get()).isNull()
  }

  @Test
  fun destroy() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                GetEvaluationsDataResponse(
                  evaluations = user1Evaluations,
                  user_evaluations_id = "user_evaluations_id_value",
                ),
              ),
            ),
        ),
    )

    BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )

    assertThat(BKTClient.getInstance()).isInstanceOf(BKTClient::class.java)

    BKTClient.destroy()

    assertThrows(BKTException.IllegalArgumentException::class.java) {
      BKTClient.getInstance()
    }
  }

  @Test
  fun `flush - success`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                GetEvaluationsDataResponse(
                  evaluations = user1Evaluations,
                  user_evaluations_id = "user_evaluations_id_value",
                ),
              ),
            ),
        ),
    )
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(RegisterEventsResponse::class.java).toJson(
            RegisterEventsResponse(RegisterEventsDataResponse(errors = emptyMap())),
          ),
        ),
    )

    val initializeFuture = BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )
    initializeFuture.get()

    assertThat(server.requestCount).isEqualTo(1)
    server.takeRequest()

    Thread.sleep(100)

    // we should have 2 events now

    val flushFuture = BKTClient.getInstance().flush()

    // should return null if a request succeeds
    val result = flushFuture.get()
    assertThat(result).isNull()

    assertThat(server.requestCount).isEqualTo(2)
    val request = server.takeRequest()
    val requestBody = requireNotNull(
      moshi.adapter(RegisterEventsRequest::class.java)
        .fromJson(request.body.readString(Charsets.UTF_8)),
    )

    assertThat(requestBody.events).hasSize(2)
    assertThat(requestBody.events.map { it.type }).isEqualTo(
      listOf(
        EventType.METRICS,
        EventType.METRICS,
      ),
    )
  }

  @Test
  fun `flush - failure`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                GetEvaluationsDataResponse(
                  evaluations = user1Evaluations,
                  user_evaluations_id = "user_evaluations_id_value",
                ),
              ),
            ),
        ),
    )
    server.enqueue(
      MockResponse()
        .setResponseCode(500)
        .setBody(
          moshi.adapter(ErrorResponse::class.java)
            .toJson(ErrorResponse(ErrorResponse.ErrorDetail(code = 500, message = "500 error"))),
        ),
    )

    val initializeFuture = BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )
    initializeFuture.get()

    assertThat(server.requestCount).isEqualTo(1)
    server.takeRequest()

    Thread.sleep(100)

    // we should have 2 events now

    val flushFuture = BKTClient.getInstance().flush()

    // should return exception if a request fails
    val result = flushFuture.get()
    assertThat(result).isInstanceOf(BKTException.ApiServerException::class.java)

    assertThat(server.requestCount).isEqualTo(2)
    val request = server.takeRequest()
    val requestBody = requireNotNull(
      moshi.adapter(RegisterEventsRequest::class.java)
        .fromJson(request.body.readString(Charsets.UTF_8)),
    )

    assertThat(requestBody.events).hasSize(2)
    assertThat(requestBody.events.map { it.type }).isEqualTo(
      listOf(
        EventType.METRICS,
        EventType.METRICS,
      ),
    )
  }

  @Test
  fun track() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                GetEvaluationsDataResponse(
                  evaluations = user1Evaluations,
                  user_evaluations_id = "user_evaluations_id_value",
                ),
              ),
            ),
        ),
    )

    val initializeFuture = BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )
    initializeFuture.get()

    val client = BKTClient.getInstance() as BKTClientImpl
    client.track("goal_id_value", 0.4)

    Thread.sleep(100)

    val actualEvents = client.componentImpl.dataModule.eventDao.getEvents()

    assertThat(actualEvents).hasSize(3)
    val goalEvent = actualEvents.last()

    assertGoalEvent(goalEvent, "goal_id_value", 0.4, user1, config.featureTag)
  }

  @Test
  fun evaluationDetails() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                GetEvaluationsDataResponse(
                  evaluations = user1Evaluations,
                  user_evaluations_id = "user_evaluations_id_value",
                ),
              ),
            ),
        ),
    )

    val initializeFuture = BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )
    initializeFuture.get()

    val actual = BKTClient.getInstance().evaluationDetails(evaluation1.feature_id)

    assertThat(actual).isEqualTo(
      BKTEvaluation(
        id = evaluation1.id,
        featureId = evaluation1.feature_id,
        featureVersion = evaluation1.feature_version,
        userId = evaluation1.user_id,
        variationId = evaluation1.variation_id,
        variationValue = evaluation1.variation_value,
        reason = BKTEvaluation.Reason.DEFAULT,
      ),
    )
  }

  @Test
  fun `evaluationDetails - unknown feature_id`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                GetEvaluationsDataResponse(
                  evaluations = user1Evaluations,
                  user_evaluations_id = "user_evaluations_id_value",
                ),
              ),
            ),
        ),
    )

    val initializeFuture = BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )
    initializeFuture.get()

    val actual = BKTClient.getInstance().evaluationDetails("unknown_feature_id")

    assertThat(actual).isNull()
  }

  @Test
  fun `fetchEvaluations - success`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                GetEvaluationsDataResponse(
                  evaluations = UserEvaluations(
                    id = "id_value",
                    evaluations = listOf(evaluation1),
                  ),
                  user_evaluations_id = "user_evaluations_id_value",
                ),
              ),
            ),
        ),
    )
    val updatedEvaluation1 = evaluation1.copy(
      variation_value = "test variation value1 updated",
      variation = evaluation1.variation.copy(
        value = "test variation value1 updated",
      ),
    )
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                GetEvaluationsDataResponse(
                  evaluations = user1Evaluations.copy(
                    evaluations = listOf(updatedEvaluation1),
                  ),
                  user_evaluations_id = "user_evaluations_id_value_updated",
                ),
              ),
            ),
        ),
    )

    val initializeFuture = BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )
    initializeFuture.get()

    val client = BKTClient.getInstance() as BKTClientImpl
    val result = client.fetchEvaluations().get()

    Thread.sleep(100)

    assertThat(result).isNull()

    assertThat(client.componentImpl.evaluationInteractor.currentEvaluationsId)
      .isEqualTo("user_evaluations_id_value_updated")

    assertThat(client.componentImpl.dataModule.evaluationDao.get(user1.id))
      .isEqualTo(listOf(updatedEvaluation1))

    assertThat(client.componentImpl.dataModule.eventDao.getEvents()).hasSize(4)
  }

  @Test
  fun `fetchEvaluations - failure`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                GetEvaluationsDataResponse(
                  evaluations = user1Evaluations,
                  user_evaluations_id = "user_evaluations_id_value",
                ),
              ),
            ),
        ),
    )
    server.enqueue(
      MockResponse()
        .setResponseCode(500)
        .setBody(
          moshi.adapter(ErrorResponse::class.java)
            .toJson(ErrorResponse(ErrorResponse.ErrorDetail(code = 500, message = "500 error"))),
        ),
    )

    val initializeFuture = BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )
    initializeFuture.get()

    val client = BKTClient.getInstance() as BKTClientImpl
    val result = client.fetchEvaluations().get()

    Thread.sleep(100)

    assertThat(result).isInstanceOf(BKTException.ApiServerException::class.java)

    assertThat(client.componentImpl.evaluationInteractor.currentEvaluationsId)
      .isEqualTo("user_evaluations_id_value")

    assertThat(client.componentImpl.dataModule.evaluationDao.get(user1.id)).hasSize(2)

    val actualEvents = client.componentImpl.dataModule.eventDao.getEvents()
    assertThat(actualEvents).hasSize(3)

    val lastEvent = actualEvents.last()
    assertThat(lastEvent.type).isEqualTo(EventType.METRICS)
    assertThat((lastEvent.event as EventData.MetricsEvent).type)
      .isEqualTo(MetricsEventType.INTERNAL_ERROR_COUNT)
  }

  @Test
  fun currentUser() {
    BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )

    val actual = BKTClient.getInstance().currentUser()

    assertThat(actual).isEqualTo(user1.toBKTUser())
  }

  @Test
  fun setUserAttributes() {
    BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )

    val attributes = mapOf(
      "custom_key" to "custom_key_value",
      "custom_key_2" to "custom_key_value_2",
    )

    BKTClient.getInstance().setUserAttributes(attributes)

    assertThat(BKTClient.getInstance().currentUser())
      .isEqualTo(user1.toBKTUser().copy(attributes = attributes))
  }
}

private val BKTClient.componentImpl: ComponentImpl
  get() = (this as BKTClientImpl).component as ComponentImpl

// these assertion methods do not check full-equality, but that should be covered in other tests
fun assertGetEvaluationLatencyMetricsEvent(actual: Event, expectedLabels: Map<String, String>) {
  assertThat(actual.id).isNotEmpty() // id is not assertable here
  assertThat(actual.type).isEqualTo(EventType.METRICS)
  assertThat(actual.event).isInstanceOf(EventData.MetricsEvent::class.java)

  val actualMetricsEvent = actual.event as EventData.MetricsEvent

  assertThat(actualMetricsEvent.timestamp).isGreaterThan(0)
  assertThat(actualMetricsEvent.type).isEqualTo(MetricsEventType.GET_EVALUATION_LATENCY)
  assertThat(actualMetricsEvent.event)
    .isInstanceOf(MetricsEventData.GetEvaluationLatencyMetricsEvent::class.java)

  val actualLatencyEvent =
    actualMetricsEvent.event as MetricsEventData.GetEvaluationLatencyMetricsEvent

  assertThat(actualLatencyEvent.labels).isEqualTo(expectedLabels)
  // actualLatencyEvent.duration is not assertable
}

fun assertGetEvaluationSizeMetricsEvent(
  actual: Event,
  expectedSizeEvent: MetricsEventData.GetEvaluationSizeMetricsEvent,
) {
  assertThat(actual.id).isNotEmpty()
  assertThat(actual.type).isEqualTo(EventType.METRICS)
  assertThat(actual.event).isInstanceOf(EventData.MetricsEvent::class.java)

  val actualMetricsEvent = actual.event as EventData.MetricsEvent

  assertThat(actualMetricsEvent.timestamp).isGreaterThan(0)
  assertThat(actualMetricsEvent.type).isEqualTo(MetricsEventType.GET_EVALUATION_SIZE)
  assertThat(actualMetricsEvent.event)
    .isInstanceOf(MetricsEventData.GetEvaluationSizeMetricsEvent::class.java)

  val actualSizeEvent = actualMetricsEvent.event as MetricsEventData.GetEvaluationSizeMetricsEvent

  assertThat(actualSizeEvent).isEqualTo(expectedSizeEvent)
}

fun assertTimeoutErrorCountMetricsEvent(
  actual: Event,
  expectedMetricsEvent: MetricsEventData.TimeoutErrorCountMetricsEvent,
) {
  assertThat(actual.type).isEqualTo(EventType.METRICS)
  val actualMetricsEvent = actual.event as EventData.MetricsEvent
  assertThat(actualMetricsEvent.type).isEqualTo(MetricsEventType.TIMEOUT_ERROR_COUNT)
  assertThat(actualMetricsEvent.event).isEqualTo(expectedMetricsEvent)
}

fun assertGoalEvent(
  actual: Event,
  expectedGoalId: String,
  expectedValue: Double,
  expectedUser: User,
  expectedFeatureTag: String,
) {
  assertThat(actual.type).isEqualTo(EventType.GOAL)
  val actualEventData = actual.event as EventData.GoalEvent

  assertThat(actualEventData.goal_id).isEqualTo(expectedGoalId)
  assertThat(actualEventData.value).isEqualTo(expectedValue)
  assertThat(actualEventData.user).isEqualTo(expectedUser)
  assertThat(actualEventData.tag).isEqualTo(expectedFeatureTag)
}
