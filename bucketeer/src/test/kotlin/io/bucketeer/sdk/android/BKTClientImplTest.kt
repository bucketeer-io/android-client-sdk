package io.bucketeer.sdk.android

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.internal.di.ComponentImpl
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationSQLDaoImpl
import io.bucketeer.sdk.android.internal.evaluation.getVariationValue
import io.bucketeer.sdk.android.internal.model.ApiId
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.EventData
import io.bucketeer.sdk.android.internal.model.EventType
import io.bucketeer.sdk.android.internal.model.MetricsEventData
import io.bucketeer.sdk.android.internal.model.MetricsEventType
import io.bucketeer.sdk.android.internal.model.SourceID
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.model.UserEvaluations
import io.bucketeer.sdk.android.internal.model.request.RegisterEventsRequest
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsResponse
import io.bucketeer.sdk.android.internal.user.toBKTUser
import io.bucketeer.sdk.android.internal.util.contains
import io.bucketeer.sdk.android.mocks.booleanEvaluation
import io.bucketeer.sdk.android.mocks.doubleEvaluation
import io.bucketeer.sdk.android.mocks.evaluation1
import io.bucketeer.sdk.android.mocks.intValueEvaluation
import io.bucketeer.sdk.android.mocks.jsonEvaluation
import io.bucketeer.sdk.android.mocks.stringEvaluation
import io.bucketeer.sdk.android.mocks.user1
import io.bucketeer.sdk.android.mocks.user1Evaluations
import io.bucketeer.sdk.android.mocks.userEvaluationsForTestGetDetailsByVariationType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class BKTClientImplTest {
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

    val future =
      BKTClient.initialize(
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

    assertThat(
      client.componentImpl.dataModule.evaluationStorage
        .get(),
    ).isEqualTo(user1Evaluations.evaluations)

    assertThat(
      client.componentImpl.dataModule.evaluationSQLDao
        .get(user1.id),
    ).isEqualTo(user1Evaluations.evaluations)

    Thread.sleep(100)

    val dbEvents =
      client.componentImpl.dataModule.eventSQLDao
        .getEvents()
    assertThat(dbEvents).hasSize(2)
    assertLatencyMetricsEvent(dbEvents[0], mapOf("tag" to config.featureTag), ApiId.GET_EVALUATIONS)
    val lastEvent = dbEvents[1]
    assertSizeMetricsEvent(
      lastEvent,
      MetricsEventData.SizeMetricsEvent(
        ApiId.GET_EVALUATIONS,
        mapOf("tag" to config.featureTag),
        713,
      ),
    )
  }

  @Test
  fun `initialize - first call - timeout`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBodyDelay(2, TimeUnit.SECONDS)
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

    val future =
      BKTClient.initialize(
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

    assertThat(
      client.componentImpl.dataModule.evaluationStorage
        .get(),
    ).isEmpty()
    assertThat(
      client.componentImpl.dataModule.evaluationSQLDao
        .get(user1.id),
    ).isEmpty()

    Thread.sleep(100)

    // timeout event should be saved
    val dbEvents =
      client.componentImpl.dataModule.eventSQLDao
        .getEvents()
    assertThat(dbEvents).hasSize(1)
    assertTimeoutErrorMetricsEvent(
      dbEvents[0],
      MetricsEventData.TimeoutErrorMetricsEvent(
        ApiId.GET_EVALUATIONS,
        mapOf("tag" to config.featureTag, "timeout" to "30.0"),
      ),
    )
  }

  @Test
  fun `initialize - second call`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBodyDelay(500, TimeUnit.MILLISECONDS)
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
    server.enqueue(MockResponse().setResponseCode(500).setBody("500 error"))

    val future1 =
      BKTClient.initialize(
        ApplicationProvider.getApplicationContext(),
        config,
        user1.toBKTUser(),
        1000,
      )

    val future2 =
      BKTClient.initialize(
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
  @LooperMode(LooperMode.Mode.PAUSED)
  fun destroyAndReinitializeImmediately() {
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

    BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )

    // Allow all code that posted to the main run loop for executing here
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    assertThat(BKTClient.getInstance()).isInstanceOf(BKTClient::class.java)
  }

  @Test
  fun `flush - success`() {
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
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(RegisterEventsResponse::class.java).toJson(
            RegisterEventsResponse(errors = emptyMap()),
          ),
        ),
    )

    val initializeFuture =
      BKTClient.initialize(
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
    val requestBody =
      requireNotNull(
        moshi
          .adapter(RegisterEventsRequest::class.java)
          .fromJson(request.body.readString(Charsets.UTF_8)),
      )

    assertThat(requestBody.events).hasSize(2)
    assertThat(requestBody.sourceId).isEqualTo(SourceID.ANDROID)
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
    server.enqueue(
      MockResponse()
        .setResponseCode(500)
        .setBody(
          moshi
            .adapter(ErrorResponse::class.java)
            .toJson(ErrorResponse(ErrorResponse.ErrorDetail(code = 500, message = "500 error"))),
        ),
    )

    val initializeFuture =
      BKTClient.initialize(
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
    assertThat(result).isInstanceOf(BKTException.InternalServerErrorException::class.java)

    assertThat(server.requestCount).isEqualTo(2)
    val request = server.takeRequest()
    val requestBody =
      requireNotNull(
        moshi
          .adapter(RegisterEventsRequest::class.java)
          .fromJson(request.body.readString(Charsets.UTF_8)),
      )

    assertThat(requestBody.events).hasSize(2)
    assertThat(requestBody.sourceId).isEqualTo(SourceID.ANDROID)
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

    val initializeFuture =
      BKTClient.initialize(
        ApplicationProvider.getApplicationContext(),
        config,
        user1.toBKTUser(),
        1000,
      )
    initializeFuture.get()

    val client = BKTClient.getInstance() as BKTClientImpl
    client.track("goal_id_value", 0.4)

    Thread.sleep(100)

    val actualEvents =
      client.componentImpl.dataModule.eventSQLDao
        .getEvents()

    assertThat(actualEvents).hasSize(3)
    val goalEvent = actualEvents.last()

    assertGoalEvent(goalEvent, "goal_id_value", 0.4, user1, config.featureTag)
  }

  @Test
  fun stringVariationDetails() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi
            .adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                evaluations = userEvaluationsForTestGetDetailsByVariationType,
                userEvaluationsId = "user_evaluations_id_value",
              ),
            ),
        ),
    )

    val initializeFuture =
      BKTClient.initialize(
        ApplicationProvider.getApplicationContext(),
        config,
        user1.toBKTUser(),
        1000,
      )
    initializeFuture.get()

    val expectedEvaluation = stringEvaluation
    val featureId = expectedEvaluation.featureId
    val expectedBKTEvaluationDetailStringValue: String? = expectedEvaluation.getVariationValue()
    assertThat(expectedBKTEvaluationDetailStringValue).isEqualTo("test variation value1")

    @Suppress("DEPRECATION")
    val actual = BKTClient.getInstance().evaluationDetails(featureId)

    assertThat(actual).isEqualTo(
      @Suppress("DEPRECATION")
      BKTEvaluation(
        id = expectedEvaluation.id,
        featureId = expectedEvaluation.featureId,
        featureVersion = expectedEvaluation.featureVersion,
        userId = expectedEvaluation.userId,
        variationId = expectedEvaluation.variationId,
        variationName = expectedEvaluation.variationName,
        variationValue = expectedBKTEvaluationDetailStringValue!!,
        reason = BKTEvaluation.Reason.DEFAULT,
      ),
    )

    val actualEvaluationDetails =
      BKTClient.getInstance().stringVariationDetails(featureId, defaultValue = "1")
    assertThat(actualEvaluationDetails).isEqualTo(
      BKTEvaluationDetails(
        featureId = expectedEvaluation.featureId,
        featureVersion = expectedEvaluation.featureVersion,
        userId = expectedEvaluation.userId,
        variationId = expectedEvaluation.variationId,
        variationName = expectedEvaluation.variationName,
        variationValue = expectedBKTEvaluationDetailStringValue,
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )

    assertThat(
      BKTClient.getInstance().intVariationDetails(featureId, defaultValue = 1),
    ).isEqualTo(
      BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = user1.id,
        defaultValue = 1,
      ),
    )

    assertThat(
      BKTClient.getInstance().boolVariationDetails(featureId, defaultValue = false),
    ).isEqualTo(
      BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = user1.id,
        defaultValue = false,
      ),
    )

    assertThat(
      BKTClient.getInstance().doubleVariationDetails(featureId, defaultValue = 1.0),
    ).isEqualTo(
      BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = user1.id,
        defaultValue = 1.0,
      ),
    )

    assertThat(
      BKTClient.getInstance().objectVariationDetails(
        featureId,
        defaultValue =
          BKTValue.Structure(
            mapOf("key1" to BKTValue.String("value-2")),
          ),
      ),
    ).isEqualTo(
      BKTEvaluationDetails(
        featureId = expectedEvaluation.featureId,
        featureVersion = expectedEvaluation.featureVersion,
        userId = expectedEvaluation.userId,
        variationId = expectedEvaluation.variationId,
        variationName = expectedEvaluation.variationName,
        variationValue = BKTValue.String(expectedBKTEvaluationDetailStringValue),
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )

    @Suppress("DEPRECATION")
    assertThat(
      BKTClient
        .getInstance()
        .jsonVariation(
          featureId,
          defaultValue = JSONObject("""{ "key1": "value-2" }"""),
        ).contains(JSONObject("""{ "key1": "value-2" }""")),
    ).isTrue()
  }

  @Test
  fun testDefaultEvaluationDetail() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi
            .adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                evaluations = userEvaluationsForTestGetDetailsByVariationType,
                userEvaluationsId = "user_evaluations_id_value",
              ),
            ),
        ),
    )

    val initializeFuture =
      BKTClient.initialize(
        ApplicationProvider.getApplicationContext(),
        config,
        user1.toBKTUser(),
        1000,
      )
    initializeFuture.get()

    val userId = "user id 1"
    val unknownFeatureId = "unknownFeatureId"
    val intDefaultInstance: BKTEvaluationDetails<Int> =
      BKTEvaluationDetails.newDefaultInstance(featureId = unknownFeatureId, userId = userId, 1)
    Assert.assertEquals(
      intDefaultInstance,
      BKTClient.getInstance().intVariationDetails(unknownFeatureId, 1),
    )

    val doubleDefaultInstance: BKTEvaluationDetails<Double> =
      BKTEvaluationDetails.newDefaultInstance(featureId = unknownFeatureId, userId = userId, 1.0)
    Assert.assertEquals(
      doubleDefaultInstance,
      BKTClient.getInstance().doubleVariationDetails(unknownFeatureId, 1.0),
    )

    val booleanDefaultInstance: BKTEvaluationDetails<Boolean> =
      BKTEvaluationDetails.newDefaultInstance(featureId = unknownFeatureId, userId = userId, true)
    Assert.assertEquals(
      booleanDefaultInstance,
      BKTClient.getInstance().boolVariationDetails(unknownFeatureId, true),
    )

    val stringDefaultInstance: BKTEvaluationDetails<String> =
      BKTEvaluationDetails.newDefaultInstance(featureId = unknownFeatureId, userId = userId, "1")
    Assert.assertEquals(
      stringDefaultInstance,
      BKTClient.getInstance().stringVariationDetails(unknownFeatureId, "1"),
    )

    val object1 =
      BKTValue.Structure(
        mapOf(
          "key1" to BKTValue.String("value1"),
          "key" to BKTValue.String("value"),
        ),
      )
    val objectDefaultInstance: BKTEvaluationDetails<BKTValue> =
      BKTEvaluationDetails.newDefaultInstance(
        featureId = unknownFeatureId,
        userId = userId,
        object1,
      )
    Assert.assertEquals(
      objectDefaultInstance,
      BKTClient.getInstance().objectVariationDetails(unknownFeatureId, object1),
    )
  }

  @Test
  fun intVariationDetails() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi
            .adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                evaluations = userEvaluationsForTestGetDetailsByVariationType,
                userEvaluationsId = "user_evaluations_id_value",
              ),
            ),
        ),
    )

    val initializeFuture =
      BKTClient.initialize(
        ApplicationProvider.getApplicationContext(),
        config,
        user1.toBKTUser(),
        1000,
      )
    initializeFuture.get()

    val expectedEvaluation = intValueEvaluation
    val featureId = expectedEvaluation.featureId
    val expectedBKTEvaluationDetailStringValue: String? = expectedEvaluation.getVariationValue()
    assertThat(expectedBKTEvaluationDetailStringValue).isEqualTo("1")

    val expectedBKTEvaluationDetailIntValue: Int? = expectedEvaluation.getVariationValue()
    assertThat(expectedBKTEvaluationDetailIntValue).isEqualTo(1)

    val expectedBKTEvaluationDetailDoubleValue: Double? = expectedEvaluation.getVariationValue()
    assertThat(expectedBKTEvaluationDetailDoubleValue).isEqualTo(1.0)

    val actualEvaluationDetails =
      BKTClient.getInstance().stringVariationDetails(featureId, defaultValue = "")
    assertThat(actualEvaluationDetails).isEqualTo(
      BKTEvaluationDetails(
        featureId = expectedEvaluation.featureId,
        featureVersion = expectedEvaluation.featureVersion,
        userId = expectedEvaluation.userId,
        variationId = expectedEvaluation.variationId,
        variationName = expectedEvaluation.variationName,
        variationValue = expectedBKTEvaluationDetailStringValue,
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )

    assertThat(
      BKTClient.getInstance().intVariationDetails(featureId, defaultValue = 1),
    ).isEqualTo(
      BKTEvaluationDetails(
        featureId = expectedEvaluation.featureId,
        featureVersion = expectedEvaluation.featureVersion,
        userId = expectedEvaluation.userId,
        variationId = expectedEvaluation.variationId,
        variationName = expectedEvaluation.variationName,
        variationValue = expectedBKTEvaluationDetailIntValue,
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )

    assertThat(
      BKTClient.getInstance().boolVariationDetails(featureId, defaultValue = true),
    ).isEqualTo(
      BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = user1.id,
        defaultValue = true,
      ),
    )

    assertThat(
      BKTClient.getInstance().doubleVariationDetails(featureId, defaultValue = 4.1),
    ).isEqualTo(
      BKTEvaluationDetails(
        featureId = expectedEvaluation.featureId,
        featureVersion = expectedEvaluation.featureVersion,
        userId = expectedEvaluation.userId,
        variationId = expectedEvaluation.variationId,
        variationName = expectedEvaluation.variationName,
        variationValue = expectedBKTEvaluationDetailDoubleValue,
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )

    assertThat(
      BKTClient
        .getInstance()
        .objectVariationDetails(featureId, defaultValue = BKTValue.Integer(100)),
    ).isEqualTo(
      BKTEvaluationDetails(
        featureId = expectedEvaluation.featureId,
        featureVersion = expectedEvaluation.featureVersion,
        userId = expectedEvaluation.userId,
        variationId = expectedEvaluation.variationId,
        variationName = expectedEvaluation.variationName,
        variationValue = BKTValue.Integer(expectedBKTEvaluationDetailIntValue as Int),
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )

    @Suppress("DEPRECATION")
    assertThat(
      BKTClient
        .getInstance()
        .jsonVariation(
          featureId,
          defaultValue = JSONObject("""{ "key1": "value-2" }"""),
        ).contains(JSONObject("""{ "key1": "value-2" }""")),
    ).isTrue()
  }

  @Test
  fun doubleVariationDetails() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi
            .adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                evaluations = userEvaluationsForTestGetDetailsByVariationType,
                userEvaluationsId = "user_evaluations_id_value",
              ),
            ),
        ),
    )

    val initializeFuture =
      BKTClient.initialize(
        ApplicationProvider.getApplicationContext(),
        config,
        user1.toBKTUser(),
        1000,
      )
    initializeFuture.get()

    val expectedEvaluation = doubleEvaluation
    val featureId = expectedEvaluation.featureId
    val expectedBKTEvaluationDetailStringValue: String? = expectedEvaluation.getVariationValue()
    assertThat(expectedBKTEvaluationDetailStringValue).isEqualTo("2.0")

    val expectedBKTEvaluationDetailDoubleValue: Double? = expectedEvaluation.getVariationValue()
    assertThat(expectedBKTEvaluationDetailDoubleValue).isEqualTo(2.0)

    val expectedBKTEvaluationDetailObjectValue: BKTValue? = expectedEvaluation.getVariationValue()
    assertThat(expectedBKTEvaluationDetailObjectValue).isEqualTo(BKTValue.Double(2.0))

    val actualEvaluationDetails =
      BKTClient.getInstance().stringVariationDetails(featureId, defaultValue = "2")
    assertThat(actualEvaluationDetails).isEqualTo(
      BKTEvaluationDetails(
        featureId = expectedEvaluation.featureId,
        featureVersion = expectedEvaluation.featureVersion,
        userId = expectedEvaluation.userId,
        variationId = expectedEvaluation.variationId,
        variationName = expectedEvaluation.variationName,
        variationValue = expectedBKTEvaluationDetailStringValue,
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )

    assertThat(
      BKTClient.getInstance().intVariationDetails(featureId, defaultValue = 2),
    ).isEqualTo(
      BKTEvaluationDetails.newDefaultInstance(featureId = featureId, userId = user1.id, 2),
    )

    assertThat(
      BKTClient.getInstance().boolVariationDetails(featureId, defaultValue = false),
    ).isEqualTo(
      BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = user1.id,
        defaultValue = false,
      ),
    )

    assertThat(
      BKTClient.getInstance().doubleVariationDetails(featureId, defaultValue = 4.2),
    ).isEqualTo(
      BKTEvaluationDetails(
        featureId = expectedEvaluation.featureId,
        featureVersion = expectedEvaluation.featureVersion,
        userId = expectedEvaluation.userId,
        variationId = expectedEvaluation.variationId,
        variationName = expectedEvaluation.variationName,
        variationValue = expectedBKTEvaluationDetailDoubleValue,
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )

    assertThat(
      BKTClient
        .getInstance()
        .objectVariationDetails(featureId, defaultValue = BKTValue.Double(100.1)),
    ).isEqualTo(
      BKTEvaluationDetails(
        featureId = expectedEvaluation.featureId,
        featureVersion = expectedEvaluation.featureVersion,
        userId = expectedEvaluation.userId,
        variationId = expectedEvaluation.variationId,
        variationName = expectedEvaluation.variationName,
        variationValue = BKTValue.Double(expectedBKTEvaluationDetailDoubleValue as Double),
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )

    @Suppress("DEPRECATION")
    assertThat(
      BKTClient
        .getInstance()
        .jsonVariation(
          featureId,
          defaultValue = JSONObject("""{ "key1": "value-2" }"""),
        ).contains(JSONObject("""{ "key1": "value-2" }""")),
    ).isTrue()
  }

  @Test
  fun booleanEvaluationDetails() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi
            .adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                evaluations = userEvaluationsForTestGetDetailsByVariationType,
                userEvaluationsId = "user_evaluations_id_value",
              ),
            ),
        ),
    )

    val initializeFuture =
      BKTClient.initialize(
        ApplicationProvider.getApplicationContext(),
        config,
        user1.toBKTUser(),
        1000,
      )
    initializeFuture.get()

    val expectedEvaluation = booleanEvaluation
    val featureId = expectedEvaluation.featureId
    val expectedBKTEvaluationDetailStringValue: String? = expectedEvaluation.getVariationValue()
    assertThat(expectedBKTEvaluationDetailStringValue).isEqualTo("true")

    val expectedBKTEvaluationDetailBooleanValue: Boolean? = expectedEvaluation.getVariationValue()
    assertThat(expectedBKTEvaluationDetailBooleanValue).isTrue()

    val actualEvaluationDetails =
      BKTClient.getInstance().stringVariationDetails(featureId, defaultValue = "3")
    assertThat(actualEvaluationDetails).isEqualTo(
      BKTEvaluationDetails(
        featureId = expectedEvaluation.featureId,
        featureVersion = expectedEvaluation.featureVersion,
        userId = expectedEvaluation.userId,
        variationId = expectedEvaluation.variationId,
        variationName = expectedEvaluation.variationName,
        variationValue = expectedBKTEvaluationDetailStringValue,
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )

    assertThat(
      BKTClient.getInstance().intVariationDetails(featureId, defaultValue = 3),
    ).isEqualTo(
      BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = user1.id,
        defaultValue = 3,
      ),
    )

    assertThat(
      BKTClient.getInstance().boolVariationDetails(featureId, defaultValue = false),
    ).isEqualTo(
      BKTEvaluationDetails(
        featureId = expectedEvaluation.featureId,
        featureVersion = expectedEvaluation.featureVersion,
        userId = expectedEvaluation.userId,
        variationId = expectedEvaluation.variationId,
        variationName = expectedEvaluation.variationName,
        variationValue = expectedBKTEvaluationDetailBooleanValue,
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )

    assertThat(
      BKTClient.getInstance().doubleVariationDetails(featureId, defaultValue = 2.0),
    ).isEqualTo(
      BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = user1.id,
        defaultValue = 2.0,
      ),
    )

    assertThat(
      BKTClient
        .getInstance()
        .objectVariationDetails(featureId, defaultValue = BKTValue.Double(100.1)),
    ).isEqualTo(
      BKTEvaluationDetails(
        featureId = expectedEvaluation.featureId,
        featureVersion = expectedEvaluation.featureVersion,
        userId = expectedEvaluation.userId,
        variationId = expectedEvaluation.variationId,
        variationName = expectedEvaluation.variationName,
        variationValue = BKTValue.Boolean(expectedBKTEvaluationDetailBooleanValue as Boolean),
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )

    @Suppress("DEPRECATION")
    assertThat(
      BKTClient
        .getInstance()
        .jsonVariation(
          featureId,
          defaultValue = JSONObject("""{ "key1": "value-2" }"""),
        ).contains(JSONObject("""{ "key1": "value-2" }""")),
    ).isTrue()
  }

  @Test
  fun jsonVariationDetails() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi
            .adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                evaluations = userEvaluationsForTestGetDetailsByVariationType,
                userEvaluationsId = "user_evaluations_id_value",
              ),
            ),
        ),
    )

    val initializeFuture =
      BKTClient.initialize(
        ApplicationProvider.getApplicationContext(),
        config,
        user1.toBKTUser(),
        1000,
      )
    initializeFuture.get()

    val expectedEvaluation = jsonEvaluation
    val featureId = expectedEvaluation.featureId
    val expectedBKTEvaluationDetailStringValue: String? = expectedEvaluation.getVariationValue()
    assertThat(expectedBKTEvaluationDetailStringValue).isEqualTo("""{ "key": "value-1" }""")

    val expectedBKTEvaluationDetailBKTValue: BKTValue? = expectedEvaluation.getVariationValue()
    assertThat(expectedBKTEvaluationDetailBKTValue).isEqualTo(
      BKTValue.Structure(
        mapOf(
          "key" to
            BKTValue.String(
              "value-1",
            ),
        ),
      ),
    )

    val actualEvaluationObjectDetails =
      BKTClient.getInstance().objectVariationDetails(
        featureId,
        defaultValue =
          BKTValue.Structure(
            mapOf(
              "key" to
                BKTValue.String(
                  "value-2",
                ),
            ),
          ),
      )

    assertThat(actualEvaluationObjectDetails).isEqualTo(
      BKTEvaluationDetails(
        featureId = expectedEvaluation.featureId,
        featureVersion = expectedEvaluation.featureVersion,
        userId = expectedEvaluation.userId,
        variationId = expectedEvaluation.variationId,
        variationName = expectedEvaluation.variationName,
        variationValue =
          BKTValue.Structure(
            mapOf(
              "key" to
                BKTValue.String(
                  "value-1",
                ),
            ),
          ),
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )

    assertThat(
      BKTClient.getInstance().stringVariationDetails(featureId, defaultValue = ""),
    ).isEqualTo(
      BKTEvaluationDetails(
        featureId = expectedEvaluation.featureId,
        featureVersion = expectedEvaluation.featureVersion,
        userId = expectedEvaluation.userId,
        variationId = expectedEvaluation.variationId,
        variationName = expectedEvaluation.variationName,
        variationValue = expectedBKTEvaluationDetailStringValue,
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )

    assertThat(
      BKTClient.getInstance().intVariationDetails(featureId, defaultValue = 10),
    ).isEqualTo(
      BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = user1.id,
        defaultValue = 10,
      ),
    )

    assertThat(
      BKTClient.getInstance().boolVariationDetails(featureId, defaultValue = false),
    ).isEqualTo(
      BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = user1.id,
        defaultValue = false,
      ),
    )

    assertThat(
      BKTClient.getInstance().doubleVariationDetails(featureId, defaultValue = 5.5),
    ).isEqualTo(
      BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = user1.id,
        defaultValue = 5.5,
      ),
    )

    @Suppress("DEPRECATION")
    assertThat(
      BKTClient
        .getInstance()
        .jsonVariation(
          featureId,
          defaultValue = JSONObject("""{ "key1": "value-2" }"""),
        ).contains(JSONObject("""{ "key": "value-1" }""")),
    ).isTrue()
  }

  @Test
  fun `evaluationDetails - unknown feature_id`() {
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

    val initializeFuture =
      BKTClient.initialize(
        ApplicationProvider.getApplicationContext(),
        config,
        user1.toBKTUser(),
        1000,
      )
    initializeFuture.get()

    val unknownFeature = "unknown_feature_id"
    @Suppress("DEPRECATION")
    assertThat(
      BKTClient.getInstance().evaluationDetails(unknownFeature),
    ).isNull()

    assertThat(
      BKTClient.getInstance().stringVariationDetails(unknownFeature, "33"),
    ).isEqualTo(
      BKTEvaluationDetails.newDefaultInstance(
        featureId = unknownFeature,
        userId = user1.id,
        defaultValue = "33",
      ),
    )

    assertThat(
      BKTClient.getInstance().intVariationDetails(unknownFeature, defaultValue = 9),
    ).isEqualTo(
      BKTEvaluationDetails.newDefaultInstance(
        featureId = unknownFeature,
        userId = user1.id,
        defaultValue = 9,
      ),
    )

    assertThat(
      BKTClient.getInstance().doubleVariationDetails(unknownFeature, defaultValue = 10.2),
    ).isEqualTo(
      BKTEvaluationDetails.newDefaultInstance(
        featureId = unknownFeature,
        userId = user1.id,
        defaultValue = 10.2,
      ),
    )

    assertThat(
      BKTClient.getInstance().boolVariationDetails(unknownFeature, defaultValue = true),
    ).isEqualTo(
      BKTEvaluationDetails.newDefaultInstance(
        featureId = unknownFeature,
        userId = user1.id,
        defaultValue = true,
      ),
    )

    assertThat(
      BKTClient.getInstance().objectVariationDetails(
        unknownFeature,
        defaultValue = BKTValue.Structure(mapOf("key" to BKTValue.String("value-1"))),
      ),
    ).isEqualTo(
      BKTEvaluationDetails.newDefaultInstance(
        featureId = unknownFeature,
        userId = user1.id,
        defaultValue = BKTValue.Structure(mapOf("key" to BKTValue.String("value-1"))),
      ),
    )
  }

  @Test
  fun `fetchEvaluations - success`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi
            .adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                evaluations =
                  UserEvaluations(
                    id = "id_value",
                    evaluations = listOf(evaluation1),
                    createdAt = "1690798021",
                    forceUpdate = true,
                  ),
                userEvaluationsId = "user_evaluations_id_value",
              ),
            ),
        ),
    )
    val updatedEvaluation1 =
      evaluation1.copy(
        variationValue = "test variation value1 updated",
      )
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi
            .adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                evaluations =
                  user1Evaluations.copy(
                    evaluations = listOf(updatedEvaluation1),
                  ),
                userEvaluationsId = "user_evaluations_id_value_updated",
              ),
            ),
        ),
    )

    val initializeFuture =
      BKTClient.initialize(
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

    assertThat(
      client.componentImpl.dataModule.evaluationStorage
        .getCurrentEvaluationId(),
    ).isEqualTo("user_evaluations_id_value_updated")

    assertThat(
      client.componentImpl.dataModule.evaluationStorage
        .get(),
    ).isEqualTo(listOf(updatedEvaluation1))

    // 2 metrics events (latency , size) from the BKTClient internal init()
    // 2 metrics events (latency , size) from the test code above
    // Because we filter duplicate
    // Finally we will have only 2 items
    assertThat(
      client.componentImpl.dataModule.eventSQLDao
        .getEvents(),
    ).hasSize(2)
  }

  @Test
  fun `fetchEvaluations - failure`() {
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
    server.enqueue(
      MockResponse()
        .setResponseCode(500)
        .setBody(
          moshi
            .adapter(ErrorResponse::class.java)
            .toJson(ErrorResponse(ErrorResponse.ErrorDetail(code = 500, message = "500 error"))),
        ),
    )

    val initializeFuture =
      BKTClient.initialize(
        ApplicationProvider.getApplicationContext(),
        config,
        user1.toBKTUser(),
        1000,
      )
    initializeFuture.get()

    val client = BKTClient.getInstance() as BKTClientImpl
    val result = client.fetchEvaluations().get()

    Thread.sleep(100)

    assertThat(result).isInstanceOf(BKTException.InternalServerErrorException::class.java)

    assertThat(
      client.componentImpl.dataModule.evaluationStorage
        .getCurrentEvaluationId(),
    ).isEqualTo("user_evaluations_id_value")

    assertThat(
      client.componentImpl.dataModule.evaluationStorage
        .get(),
    ).hasSize(2)

    val actualEvents =
      client.componentImpl.dataModule.eventSQLDao
        .getEvents()
    assertThat(actualEvents).hasSize(3)

    val lastEvent = actualEvents.last()
    assertThat(lastEvent.type).isEqualTo(EventType.METRICS)
    assertThat((lastEvent.event as EventData.MetricsEvent).type)
      .isEqualTo(MetricsEventType.INTERNAL_SERVER_ERROR)
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

    val attributes =
      mapOf(
        "custom_key" to "custom_key_value",
        "custom_key_2" to "custom_key_value_2",
      )

    BKTClient.getInstance().updateUserAttributes(attributes)

    assertThat(BKTClient.getInstance().currentUser())
      .isEqualTo(user1.toBKTUser().copy(attributes = attributes))

    val client = BKTClient.getInstance() as BKTClientImpl
    assertThat(
      client.componentImpl.dataModule.evaluationStorage
        .getCurrentEvaluationId(),
    ).isEmpty()
  }

  @Test
  fun shouldCloseDatabase() {
    BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      config,
      user1.toBKTUser(),
      1000,
    )
    val clientImpl = BKTClient.getInstance() as BKTClientImpl
    val dataModule = (clientImpl.component as ComponentImpl).dataModule
    val evaluationSQLDao = (dataModule.evaluationSQLDao as EvaluationSQLDaoImpl)
    val eventSQLDao = (dataModule.evaluationSQLDao as EvaluationSQLDaoImpl)
    val sqliteHelper = evaluationSQLDao.sqLiteOpenHelper
    val db = sqliteHelper.writableDatabase
    BKTClient.destroy()
    Thread.sleep(2000L)
    assertThat(evaluationSQLDao.isClosed).isTrue()
    assertThat(eventSQLDao.isClosed).isTrue()
    assertThat(db.isOpen).isFalse()
  }

  @Test
  fun getEvaluationDetailsWhileInitialization() {
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

    val configWithLogger =
      BKTConfig
        .builder()
        .apiEndpoint(config.apiEndpoint)
        .apiKey(config.apiKey)
        .featureTag(config.featureTag)
        .appVersion(config.appVersion)
        .logger(DefaultLogger())
        .build()

    BKTClient.initialize(
      ApplicationProvider.getApplicationContext(),
      configWithLogger,
      user1.toBKTUser(),
      1000,
    )

    @Suppress("DEPRECATION")
    val actual = BKTClient.getInstance().evaluationDetails(evaluation1.featureId)
    assertThat(actual).isNotInstanceOf(BKTException::class.java)

    val actualEvaluationDetails =
      BKTClient.getInstance().stringVariationDetails(evaluation1.featureId, defaultValue = "")
    assertThat(actualEvaluationDetails).isNotInstanceOf(BKTException::class.java)
  }
}

private val BKTClient.componentImpl: ComponentImpl
  get() = (this as BKTClientImpl).component as ComponentImpl

// these assertion methods do not check full-equality, but that should be covered in other tests
fun assertLatencyMetricsEvent(
  actual: Event,
  expectedLabels: Map<String, String>,
  apiId: ApiId,
) {
  assertThat(actual.id).isNotEmpty() // id is not assertable here
  assertThat(actual.type).isEqualTo(EventType.METRICS)
  assertThat(actual.event).isInstanceOf(EventData.MetricsEvent::class.java)

  val actualMetricsEvent = actual.event as EventData.MetricsEvent

  assertThat(actualMetricsEvent.timestamp).isGreaterThan(0L)

  assertThat(actualMetricsEvent.type).isEqualTo(MetricsEventType.RESPONSE_LATENCY)
  assertThat(actualMetricsEvent.event)
    .isInstanceOf(MetricsEventData.LatencyMetricsEvent::class.java)

  val actualLatencyEvent =
    actualMetricsEvent.event as MetricsEventData.LatencyMetricsEvent
  assertThat(actualLatencyEvent.apiId).isEqualTo(apiId)
  assertThat(actualLatencyEvent.labels).isEqualTo(expectedLabels)
  // actualLatencyEvent.duration is not assertable
}

fun assertSizeMetricsEvent(
  actual: Event,
  expectedSizeEvent: MetricsEventData.SizeMetricsEvent,
) {
  assertThat(actual.id).isNotEmpty()
  assertThat(actual.type).isEqualTo(EventType.METRICS)
  assertThat(actual.event).isInstanceOf(EventData.MetricsEvent::class.java)

  val actualMetricsEvent = actual.event as EventData.MetricsEvent

  assertThat(actualMetricsEvent.timestamp).isGreaterThan(0L)
  assertThat(actualMetricsEvent.type).isEqualTo(MetricsEventType.RESPONSE_SIZE)
  assertThat(actualMetricsEvent.event)
    .isInstanceOf(MetricsEventData.SizeMetricsEvent::class.java)

  val actualSizeEvent = actualMetricsEvent.event as MetricsEventData.SizeMetricsEvent

  assertThat(actualSizeEvent).isEqualTo(expectedSizeEvent)
}

fun assertTimeoutErrorMetricsEvent(
  actual: Event,
  expectedMetricsEvent: MetricsEventData.TimeoutErrorMetricsEvent,
) {
  assertThat(actual.type).isEqualTo(EventType.METRICS)
  val actualMetricsEvent = actual.event as EventData.MetricsEvent
  assertThat(actualMetricsEvent.type).isEqualTo(MetricsEventType.TIMEOUT_ERROR)
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

  assertThat(actualEventData.goalId).isEqualTo(expectedGoalId)
  assertThat(actualEventData.value).isEqualTo(expectedValue)
  assertThat(actualEventData.user).isEqualTo(expectedUser)
  assertThat(actualEventData.tag).isEqualTo(expectedFeatureTag)
}
