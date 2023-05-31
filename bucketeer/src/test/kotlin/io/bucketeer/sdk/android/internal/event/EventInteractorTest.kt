package io.bucketeer.sdk.android.internal.event

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.BuildConfig
import io.bucketeer.sdk.android.internal.Clock
import io.bucketeer.sdk.android.internal.ClockImpl
import io.bucketeer.sdk.android.internal.IdGenerator
import io.bucketeer.sdk.android.internal.IdGeneratorImpl
import io.bucketeer.sdk.android.internal.di.ComponentImpl
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.di.InteractorModule
import io.bucketeer.sdk.android.internal.model.ApiID
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.EventData
import io.bucketeer.sdk.android.internal.model.EventType
import io.bucketeer.sdk.android.internal.model.MetricsEventData
import io.bucketeer.sdk.android.internal.model.MetricsEventType
import io.bucketeer.sdk.android.internal.model.Reason
import io.bucketeer.sdk.android.internal.model.ReasonType
import io.bucketeer.sdk.android.internal.model.SourceID
import io.bucketeer.sdk.android.internal.model.request.RegisterEventsRequest
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsErrorResponse
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsResponse
import io.bucketeer.sdk.android.internal.remote.ApiClient
import io.bucketeer.sdk.android.internal.remote.ApiClientImpl
import io.bucketeer.sdk.android.mocks.evaluation1
import io.bucketeer.sdk.android.mocks.user1
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

// TODO : Kenji : need thinking and add more test to this test file
@RunWith(RobolectricTestRunner::class)
class EventInteractorTest {
  private lateinit var server: MockWebServer

  private lateinit var component: ComponentImpl
  private lateinit var moshi: Moshi
  private lateinit var idGenerator: FakeIdGenerator
  private lateinit var clock: FakeClock

  private lateinit var interactor: EventInteractor

  @Before
  fun setup() {
    server = MockWebServer()

    component = ComponentImpl(
      dataModule = TestDataModule(
        application = ApplicationProvider.getApplicationContext(),
        config = BKTConfig.builder()
          .apiEndpoint(server.url("").toString())
          .apiKey("api_key_value")
          .featureTag("feature_tag_value")
          .eventsMaxQueueSize(3)
          .appVersion("1.2.3")
          .build(),
        defaultRequestTimeoutMillis = TimeUnit.SECONDS.toMillis(1),
      ),
      interactorModule = InteractorModule(
        mainHandler = Handler(Looper.getMainLooper()),
      ),
    )

    interactor = component.eventInteractor

    moshi = component.dataModule.moshi
    idGenerator = component.dataModule.idGenerator as FakeIdGenerator
    clock = component.dataModule.clock as FakeClock
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun trackEvaluationEvent() {
    val listener = FakeEventUpdateListener()

    interactor.setEventUpdateListener(listener)

    interactor.trackEvaluationEvent("feature_tag_value", user1, evaluation1)

    assertThat(listener.calls).hasSize(1)
    assertThat(listener.calls[0]).hasSize(1)

    assertThat(idGenerator.calls).hasSize(1)
    assertThat(clock.currentTimeSecondsCalls).hasSize(1)

    val event = listener.calls[0][0]

    assertThat(event).isEqualTo(
      Event(
        id = idGenerator.calls[0],
        type = EventType.EVALUATION,
        event = EventData.EvaluationEvent(
          timestamp = clock.currentTimeSecondsCalls[0],
          featureId = evaluation1.featureId,
          featureVersion = evaluation1.featureVersion,
          userId = user1.id,
          variationId = evaluation1.variationId,
          user = user1,
          reason = evaluation1.reason,
          tag = "feature_tag_value",
          sourceId = SourceID.ANDROID,
          sdkVersion = BuildConfig.SDK_VERSION,
          metadata = mapOf(
            "app_version" to "1.2.3",
            "os_version" to "16",
            "device_model" to "robolectric",
          ),
        ),
      ),
    )
  }

  @Test
  fun trackDefaultEvaluationEvent() {
    val listener = FakeEventUpdateListener()

    interactor.setEventUpdateListener(listener)

    interactor.trackDefaultEvaluationEvent("feature_tag_value", user1, "feature_id_value")

    assertThat(listener.calls).hasSize(1)
    assertThat(listener.calls[0]).hasSize(1)

    assertThat(idGenerator.calls).hasSize(1)
    assertThat(clock.currentTimeSecondsCalls).hasSize(1)

    val event = listener.calls[0][0]

    assertThat(event).isEqualTo(
      Event(
        id = idGenerator.calls[0],
        type = EventType.EVALUATION,
        event = EventData.EvaluationEvent(
          timestamp = clock.currentTimeSecondsCalls[0],
          featureId = "feature_id_value",
          userId = user1.id,
          user = user1,
          reason = Reason(ReasonType.CLIENT),
          tag = "feature_tag_value",
          sourceId = SourceID.ANDROID,
          sdkVersion = BuildConfig.SDK_VERSION,
          metadata = mapOf(
            "app_version" to "1.2.3",
            "os_version" to "16",
            "device_model" to "robolectric",
          ),
        ),
      ),
    )
  }

  @Test
  fun trackGoalEvent() {
    val listener = FakeEventUpdateListener()

    interactor.setEventUpdateListener(listener)

    interactor.trackGoalEvent("feature_tag_value", user1, "goal_id_value", 0.5)

    assertThat(listener.calls).hasSize(1)
    assertThat(listener.calls[0]).hasSize(1)

    assertThat(idGenerator.calls).hasSize(1)
    assertThat(clock.currentTimeSecondsCalls).hasSize(1)

    val event = listener.calls[0][0]

    assertThat(event).isEqualTo(
      Event(
        id = idGenerator.calls[0],
        type = EventType.GOAL,
        event = EventData.GoalEvent(
          timestamp = clock.currentTimeSecondsCalls[0],
          goalId = "goal_id_value",
          userId = user1.id,
          value = 0.5,
          user = user1,
          tag = "feature_tag_value",
          sourceId = SourceID.ANDROID,
          sdkVersion = BuildConfig.SDK_VERSION,
          metadata = mapOf(
            "app_version" to "1.2.3",
            "os_version" to "16",
            "device_model" to "robolectric",
          ),
        ),
      ),
    )
  }

  @Test
  fun trackFetchEvaluationsSuccess() {
    val listener = FakeEventUpdateListener()

    interactor.setEventUpdateListener(listener)

    interactor.trackFetchEvaluationsSuccess("feature_tag_value", 1, 723)

    assertThat(listener.calls).hasSize(1)
    assertThat(listener.calls[0]).hasSize(2)

    assertThat(idGenerator.calls).hasSize(2)
    assertThat(clock.currentTimeSecondsCalls).hasSize(2)

    val latencyEvent = listener.calls[0][0]

    assertThat(latencyEvent).isEqualTo(
      Event(
        id = idGenerator.calls[0],
        type = EventType.METRICS,
        event = EventData.MetricsEvent(
          timestamp = clock.currentTimeSecondsCalls[0],
          type = MetricsEventType.RESPONSE_LATENCY,
          event = MetricsEventData.LatencyMetricsEvent(
            ApiID.GET_EVALUATIONS,
            labels = mapOf(
              "tag" to "feature_tag_value",
            ),
            latencySecond = 1.0,
          ),
          sdkVersion = BuildConfig.SDK_VERSION,
          metadata = mapOf(
            "app_version" to "1.2.3",
            "os_version" to "16",
            "device_model" to "robolectric",
          ),
        ),
      ),
    )

    val sizeEvent = listener.calls[0][1]

    assertThat(sizeEvent).isEqualTo(
      Event(
        id = idGenerator.calls[1],
        type = EventType.METRICS,
        event = EventData.MetricsEvent(
          timestamp = clock.currentTimeSecondsCalls[1],
          type = MetricsEventType.RESPONSE_SIZE,
          event = MetricsEventData.SizeMetricsEvent(
            ApiID.GET_EVALUATIONS,
            labels = mapOf(
              "tag" to "feature_tag_value",
            ),
            sizeByte = 723,
          ),
          sdkVersion = BuildConfig.SDK_VERSION,
          metadata = mapOf(
            "app_version" to "1.2.3",
            "os_version" to "16",
            "device_model" to "robolectric",
          ),
        ),
      ),
    )
  }

  @Test
  fun `trackFetchEvaluationsFailure - timeout error`() {
    val listener = FakeEventUpdateListener()

    interactor.setEventUpdateListener(listener)

    interactor.trackFetchEvaluationsFailure(
      "feature_tag_value",
      BKTException.TimeoutException("timeout", SocketTimeoutException()),
    )

    assertThat(listener.calls).hasSize(1)
    assertThat(listener.calls[0]).hasSize(1)

    assertThat(idGenerator.calls).hasSize(1)
    assertThat(clock.currentTimeSecondsCalls).hasSize(1)

    val event = listener.calls[0][0]

    assertThat(event).isEqualTo(
      Event(
        id = idGenerator.calls[0],
        type = EventType.METRICS,
        event = EventData.MetricsEvent(
          timestamp = clock.currentTimeSecondsCalls[0],
          type = MetricsEventType.TIMEOUT_ERROR,
          event = MetricsEventData.TimeoutErrorMetricsEvent(
            ApiID.GET_EVALUATIONS,
            mapOf(
              "tag" to "feature_tag_value",
            ),
          ),
          sdkVersion = BuildConfig.SDK_VERSION,
          metadata = mapOf(
            "app_version" to "1.2.3",
            "os_version" to "16",
            "device_model" to "robolectric",
          ),
        ),
      ),
    )
  }

  @Test
  fun `trackFetchEvaluationsFailure - other error`() {
    val listener = FakeEventUpdateListener()

    interactor.setEventUpdateListener(listener)

    interactor.trackFetchEvaluationsFailure(
      "feature_tag_value",
      BKTException.BadRequestException("bad request"),
    )

    assertThat(listener.calls).hasSize(1)
    assertThat(listener.calls[0]).hasSize(1)

    assertThat(idGenerator.calls).hasSize(1)
    assertThat(clock.currentTimeSecondsCalls).hasSize(1)

    val event = listener.calls[0][0]

    assertThat(event).isEqualTo(
      Event(
        id = idGenerator.calls[0],
        type = EventType.METRICS,
        event = EventData.MetricsEvent(
          timestamp = clock.currentTimeSecondsCalls[0],
          type = MetricsEventType.BAD_REQUEST_ERROR,
          event = MetricsEventData.BadRequestErrorMetricsEvent(
            ApiID.GET_EVALUATIONS,
            mapOf(
              "tag" to "feature_tag_value",
            ),
          ),
          sdkVersion = BuildConfig.SDK_VERSION,
          metadata = mapOf(
            "app_version" to "1.2.3",
            "os_version" to "16",
            "device_model" to "robolectric",
          ),
        ),
      ),
    )
  }

  @Test
  fun `sendEvents - success`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(RegisterEventsResponse::class.java).toJson(
            RegisterEventsResponse(errors = emptyMap()),
          ),
        ),
    )

    interactor.trackFetchEvaluationsSuccess("feature_tag_value", 1, 723)
    interactor.trackGoalEvent("feature_tag_value", user1, "goal_id_value", 0.5)
    interactor.trackGoalEvent("feature_tag_value", user1, "goal_id_value2", 0.4)

    assertThat(component.dataModule.eventDao.getEvents()).hasSize(4)

    val result = interactor.sendEvents(force = false)

    assertThat(server.requestCount).isEqualTo(1)

    val request = server.takeRequest()
    val requestBody = moshi.adapter(RegisterEventsRequest::class.java)
      .fromJson(request.body.readString(Charsets.UTF_8))

    // eventsMaxBatchQueueCount is 3, so should send 3 events
    assertThat(requestBody).isEqualTo(
      RegisterEventsRequest(
        events = listOf(
          Event(
            id = idGenerator.calls[0],
            type = EventType.METRICS,
            event = EventData.MetricsEvent(
              timestamp = clock.currentTimeSecondsCalls[0],
              type = MetricsEventType.RESPONSE_LATENCY,
              event = MetricsEventData.LatencyMetricsEvent(
                ApiID.GET_EVALUATIONS,
                labels = mapOf(
                  "tag" to "feature_tag_value",
                ),
                latencySecond = 1.0,
              ),
              sdkVersion = BuildConfig.SDK_VERSION,
              metadata = mapOf(
                "app_version" to "1.2.3",
                "os_version" to "16",
                "device_model" to "robolectric",
              ),
            ),
          ),
          Event(
            id = idGenerator.calls[1],
            type = EventType.METRICS,
            event = EventData.MetricsEvent(
              timestamp = clock.currentTimeSecondsCalls[1],
              type = MetricsEventType.RESPONSE_SIZE,
              event = MetricsEventData.SizeMetricsEvent(
                ApiID.GET_EVALUATIONS,
                labels = mapOf(
                  "tag" to "feature_tag_value",
                ),
                sizeByte = 723,
              ),
              sdkVersion = BuildConfig.SDK_VERSION,
              metadata = mapOf(
                "app_version" to "1.2.3",
                "os_version" to "16",
                "device_model" to "robolectric",
              ),
            ),
          ),
          Event(
            id = idGenerator.calls[2],
            type = EventType.GOAL,
            event = EventData.GoalEvent(
              timestamp = clock.currentTimeSecondsCalls[2],
              goalId = "goal_id_value",
              userId = user1.id,
              value = 0.5,
              user = user1,
              tag = "feature_tag_value",
              sourceId = SourceID.ANDROID,
              sdkVersion = BuildConfig.SDK_VERSION,
              metadata = mapOf(
                "app_version" to "1.2.3",
                "os_version" to "16",
                "device_model" to "robolectric",
              ),
            ),
          ),
        ),
      ),
    )

    require(result is SendEventsResult.Success)
    assertThat(result.sent).isTrue()

    val actualEvents = component.dataModule.eventDao.getEvents()
    assertThat(actualEvents).hasSize(1)

    val eventData = actualEvents.first().event as EventData.GoalEvent
    assertThat(eventData.goalId).isEqualTo("goal_id_value2")
    assertThat(eventData.value).isEqualTo(0.4)
  }

  @Test
  fun `sendEvents - failure`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(400)
        .setBody(
          moshi.adapter(ErrorResponse::class.java).toJson(
            ErrorResponse(ErrorResponse.ErrorDetail(400, "400 error")),
          ),
        ),
    )

    interactor.trackFetchEvaluationsSuccess("feature_tag_value", 1, 723)
    interactor.trackGoalEvent("feature_tag_value", user1, "goal_id_value", 0.5)

    assertThat(component.dataModule.eventDao.getEvents()).hasSize(3)

    val result = interactor.sendEvents(force = false)

    assertThat(server.requestCount).isEqualTo(1)

    require(result is SendEventsResult.Failure)
    assertThat(result.error).isInstanceOf(BKTException.BadRequestException::class.java)

    val events = component.dataModule.eventDao.getEvents()
    // Note: because `register_event` fail ,
    // So there will be one more auto tracked event
    // For MetricsEventData.BadRequestErrorMetricsEvent
    assertThat(component.dataModule.eventDao.getEvents()).hasSize(4)

    val expectedEvents = listOf(
      Event(
        id = idGenerator.calls[0],
        type = EventType.METRICS,
        event = EventData.MetricsEvent(
          timestamp = clock.currentTimeSecondsCalls[0],
          type = MetricsEventType.RESPONSE_LATENCY,
          event = MetricsEventData.LatencyMetricsEvent(
            ApiID.GET_EVALUATIONS,
            labels = mapOf(
              "tag" to "feature_tag_value",
            ),
            latencySecond = 1.0,
          ),
          sdkVersion = BuildConfig.SDK_VERSION,
          metadata = mapOf(
            "app_version" to "1.2.3",
            "os_version" to "16",
            "device_model" to "robolectric",
          ),
        ),
      ),
      Event(
        id = idGenerator.calls[1],
        type = EventType.METRICS,
        event = EventData.MetricsEvent(
          timestamp = clock.currentTimeSecondsCalls[1],
          type = MetricsEventType.RESPONSE_SIZE,
          event = MetricsEventData.SizeMetricsEvent(
            ApiID.GET_EVALUATIONS,
            labels = mapOf(
              "tag" to "feature_tag_value",
            ),
            sizeByte = 723,
          ),
          sdkVersion = BuildConfig.SDK_VERSION,
          metadata = mapOf(
            "app_version" to "1.2.3",
            "os_version" to "16",
            "device_model" to "robolectric",
          ),
        ),
      ),
      Event(
        id = idGenerator.calls[2],
        type = EventType.GOAL,
        event = EventData.GoalEvent(
          timestamp = clock.currentTimeSecondsCalls[2],
          goalId = "goal_id_value",
          userId = user1.id,
          value = 0.5,
          user = user1,
          tag = "feature_tag_value",
          sourceId = SourceID.ANDROID,
          sdkVersion = BuildConfig.SDK_VERSION,
          metadata = mapOf(
            "app_version" to "1.2.3",
            "os_version" to "16",
            "device_model" to "robolectric",
          ),
        ),
      ),
      Event(
        id = idGenerator.calls[3],
        type = EventType.METRICS,
        event = EventData.MetricsEvent(
          timestamp = clock.currentTimeSecondsCalls[3],
          type = MetricsEventType.BAD_REQUEST_ERROR,
          event = MetricsEventData.BadRequestErrorMetricsEvent(
            ApiID.REGISTER_EVENTS,
          ),
          sdkVersion = BuildConfig.SDK_VERSION,
          metadata = mapOf(
            "app_version" to "1.2.3",
            "os_version" to "16",
            "device_model" to "robolectric",
          ),
        ),
      ),
    )
    assertThat(events).isEqualTo(expectedEvents)
  }

  @Test
  fun `sendEvents - current is empty`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(RegisterEventsResponse::class.java).toJson(
            RegisterEventsResponse(errors = emptyMap()),
          ),
        ),
    )

    assertThat(component.dataModule.eventDao.getEvents()).isEmpty()

    val result = interactor.sendEvents(force = false)

    require(result is SendEventsResult.Success)
    assertThat(result.sent).isFalse()

    assertThat(server.requestCount).isEqualTo(0)

    assertThat(component.dataModule.eventDao.getEvents()).isEmpty()
  }

  @Test
  fun `sendEvents - current cache is less than threshold`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(RegisterEventsResponse::class.java).toJson(
            RegisterEventsResponse(errors = emptyMap()),
          ),
        ),
    )

    interactor.trackFetchEvaluationsSuccess("feature_tag_value", 1, 723)

    assertThat(component.dataModule.eventDao.getEvents()).hasSize(2)

    val result = interactor.sendEvents(force = false)

    require(result is SendEventsResult.Success)
    assertThat(result.sent).isFalse()

    assertThat(server.requestCount).isEqualTo(0)

    assertThat(component.dataModule.eventDao.getEvents()).hasSize(2)
  }

  @Test
  fun `sendEvents - force`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(RegisterEventsResponse::class.java).toJson(
            RegisterEventsResponse(errors = emptyMap()),
          ),
        ),
    )

    interactor.trackFetchEvaluationsSuccess("feature_tag_value", 1, 723)

    val result = interactor.sendEvents(force = true)

    require(result is SendEventsResult.Success)
    assertThat(result.sent).isTrue()

    assertThat(server.requestCount).isEqualTo(1)

    val request = server.takeRequest()
    val requestBody = moshi.adapter(RegisterEventsRequest::class.java)
      .fromJson(request.body.readString(Charsets.UTF_8))

    assertThat(requestBody).isEqualTo(
      RegisterEventsRequest(
        events = listOf(
          Event(
            id = idGenerator.calls[0],
            type = EventType.METRICS,
            event = EventData.MetricsEvent(
              timestamp = clock.currentTimeSecondsCalls[0],
              type = MetricsEventType.RESPONSE_LATENCY,
              event = MetricsEventData.LatencyMetricsEvent(
                ApiID.GET_EVALUATIONS,
                labels = mapOf(
                  "tag" to "feature_tag_value",
                ),
                latencySecond = 1.0,
              ),
              sdkVersion = BuildConfig.SDK_VERSION,
              metadata = mapOf(
                "app_version" to "1.2.3",
                "os_version" to "16",
                "device_model" to "robolectric",
              ),
            ),
          ),
          Event(
            id = idGenerator.calls[1],
            type = EventType.METRICS,
            event = EventData.MetricsEvent(
              timestamp = clock.currentTimeSecondsCalls[1],
              type = MetricsEventType.RESPONSE_SIZE,
              event = MetricsEventData.SizeMetricsEvent(
                ApiID.GET_EVALUATIONS,
                labels = mapOf(
                  "tag" to "feature_tag_value",
                ),
                sizeByte = 723,
              ),
              sdkVersion = BuildConfig.SDK_VERSION,
              metadata = mapOf(
                "app_version" to "1.2.3",
                "os_version" to "16",
                "device_model" to "robolectric",
              ),
            ),
          ),
        ),
      ),
    )

    assertThat(component.dataModule.eventDao.getEvents()).isEmpty()
  }

  @Test
  fun `sendEvents - retriable error`() {
    interactor.trackFetchEvaluationsSuccess("feature_tag_value", 1, 723)
    interactor.trackGoalEvent("feature_tag_value", user1, "goal_id_value", 0.5)
    interactor.trackGoalEvent("feature_tag_value", user1, "goal_id_value2", 0.4)

    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(RegisterEventsResponse::class.java).toJson(
            RegisterEventsResponse(
              errors = mapOf(
                // TODO: Kenji check RegisterEventsErrorResponse on iOS ref
                idGenerator.calls[0] to RegisterEventsErrorResponse(retriable = true, "error"),
                idGenerator.calls[1] to RegisterEventsErrorResponse(retriable = false, "error"),
              ),
            ),
          ),
        ),
    )

    assertThat(component.dataModule.eventDao.getEvents()).hasSize(4)

    interactor.sendEvents(force = false)

    assertThat(server.requestCount).isEqualTo(1)

    val request = server.takeRequest()
    val requestBody = moshi.adapter(RegisterEventsRequest::class.java)
      .fromJson(request.body.readString(Charsets.UTF_8))

    // eventsMaxBatchQueueCount is 3, so should send 3 events
    assertThat(requestBody).isEqualTo(
      RegisterEventsRequest(
        events = listOf(
          Event(
            id = idGenerator.calls[0],
            type = EventType.METRICS,
            event = EventData.MetricsEvent(
              timestamp = clock.currentTimeSecondsCalls[0],
              type = MetricsEventType.RESPONSE_LATENCY,
              event = MetricsEventData.LatencyMetricsEvent(
                ApiID.GET_EVALUATIONS,
                labels = mapOf(
                  "tag" to "feature_tag_value",
                ),
                latencySecond = 1.0,
              ),
              sdkVersion = BuildConfig.SDK_VERSION,
              metadata = mapOf(
                "app_version" to "1.2.3",
                "os_version" to "16",
                "device_model" to "robolectric",
              ),
            ),
          ),
          Event(
            id = idGenerator.calls[1],
            type = EventType.METRICS,
            event = EventData.MetricsEvent(
              timestamp = clock.currentTimeSecondsCalls[1],
              type = MetricsEventType.RESPONSE_SIZE,
              event = MetricsEventData.SizeMetricsEvent(
                ApiID.GET_EVALUATIONS,
                labels = mapOf(
                  "tag" to "feature_tag_value",
                ),
                sizeByte = 723,
              ),
              sdkVersion = BuildConfig.SDK_VERSION,
              metadata = mapOf(
                "app_version" to "1.2.3",
                "os_version" to "16",
                "device_model" to "robolectric",
              ),
            ),
          ),
          Event(
            id = idGenerator.calls[2],
            type = EventType.GOAL,
            event = EventData.GoalEvent(
              timestamp = clock.currentTimeSecondsCalls[2],
              goalId = "goal_id_value",
              userId = user1.id,
              value = 0.5,
              user = user1,
              tag = "feature_tag_value",
              sourceId = SourceID.ANDROID,
              sdkVersion = BuildConfig.SDK_VERSION,
              metadata = mapOf(
                "app_version" to "1.2.3",
                "os_version" to "16",
                "device_model" to "robolectric",
              ),
            ),
          ),
        ),
      ),
    )

    val actualEvents = component.dataModule.eventDao.getEvents()
    assertThat(actualEvents).hasSize(2)

    // retriable evaluation shouldn't be deleted
    assertThat(actualEvents).isEqualTo(
      listOf(
        Event(
          id = idGenerator.calls[0],
          type = EventType.METRICS,
          event = EventData.MetricsEvent(
            timestamp = clock.currentTimeSecondsCalls[0],
            type = MetricsEventType.RESPONSE_LATENCY,
            event = MetricsEventData.LatencyMetricsEvent(
              ApiID.GET_EVALUATIONS,
              labels = mapOf(
                "tag" to "feature_tag_value",
              ),
              latencySecond = 1.0,
            ),
            sdkVersion = BuildConfig.SDK_VERSION,
            metadata = mapOf(
              "app_version" to "1.2.3",
              "os_version" to "16",
              "device_model" to "robolectric",
            ),
          ),
        ),
        Event(
          id = idGenerator.calls[3],
          type = EventType.GOAL,
          event = EventData.GoalEvent(
            timestamp = clock.currentTimeSecondsCalls[3],
            goalId = "goal_id_value2",
            userId = user1.id,
            value = 0.4,
            user = user1,
            tag = "feature_tag_value",
            sourceId = SourceID.ANDROID,
            sdkVersion = BuildConfig.SDK_VERSION,
            metadata = mapOf(
              "app_version" to "1.2.3",
              "os_version" to "16",
              "device_model" to "robolectric",
            ),
          ),
        ),
      ),
    )
  }
}

private class FakeEventUpdateListener() : EventInteractor.EventUpdateListener {

  val calls = mutableListOf<List<Event>>()

  override fun onUpdate(events: List<Event>) {
    calls.add(events)
  }
}

private class FakeIdGenerator : IdGenerator {
  val calls = mutableListOf<String>()

  private val impl = IdGeneratorImpl()

  override fun newId(): String {
    return impl.newId()
      .also { calls.add(it) }
  }
}

private class FakeClock : Clock {

  val currentTimeMillisCalls = mutableListOf<Long>()

  val currentTimeSecondsCalls = mutableListOf<Long>()

  private val impl = ClockImpl()

  override fun currentTimeMillis(): Long {
    return impl.currentTimeMillis()
      .also { currentTimeMillisCalls.add(it) }
  }

  override fun currentTimeSeconds(): Long {
    return impl.currentTimeSeconds()
      .also { currentTimeSecondsCalls.add(it) }
  }
}

private class TestDataModule(
  application: Application,
  config: BKTConfig,
  defaultRequestTimeoutMillis: Long,
) : DataModule(application, user1, config, inMemoryDB = true) {

  override val clock: Clock by lazy { FakeClock() }

  override val idGenerator: IdGenerator by lazy { FakeIdGenerator() }

  override val apiClient: ApiClient by lazy {
    ApiClientImpl(
      apiEndpoint = config.apiEndpoint,
      apiKey = config.apiKey,
      featureTag = config.featureTag,
      moshi = moshi,
      defaultRequestTimeoutMillis = defaultRequestTimeoutMillis,
    )
  }
}
