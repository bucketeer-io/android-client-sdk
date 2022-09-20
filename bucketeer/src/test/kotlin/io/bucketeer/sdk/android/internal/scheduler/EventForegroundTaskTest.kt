package io.bucketeer.sdk.android.internal.scheduler

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.createTestBKTConfig
import io.bucketeer.sdk.android.enqueueResponse
import io.bucketeer.sdk.android.internal.di.ComponentImpl
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.di.InteractorModule
import io.bucketeer.sdk.android.internal.model.EventType
import io.bucketeer.sdk.android.internal.model.request.RegisterEventsRequest
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsDataResponse
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsResponse
import io.bucketeer.sdk.android.internal.remote.measureTimeMillisWithResult
import io.bucketeer.sdk.android.mocks.evaluation1
import io.bucketeer.sdk.android.mocks.evaluation2
import io.bucketeer.sdk.android.mocks.user1
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class EventForegroundTaskTest {
  private lateinit var server: MockWebServer

  private lateinit var component: ComponentImpl
  private lateinit var moshi: Moshi
  private lateinit var executor: ScheduledExecutorService
  private lateinit var task: EventForegroundTask

  @Before
  fun setup() {
    server = MockWebServer()

    component = ComponentImpl(
      dataModule = DataModule(
        application = ApplicationProvider.getApplicationContext(),
        config = createTestBKTConfig(
          apiKey = "api_key_value",
          endpoint = server.url("").toString(),
          featureTag = "feature_tag_value",
          eventsMaxBatchQueueCount = 3,
          eventsFlushInterval = 1000,
        ),
        user = user1,
        inMemoryDB = true,
      ),
      interactorModule = InteractorModule(),
    )

    moshi = component.dataModule.moshi

    executor = Executors.newSingleThreadScheduledExecutor()

    task = EventForegroundTask(component, executor)
  }

  @After
  fun tearDown() {
    task.stop()
    server.shutdown()
    executor.shutdownNow()
  }

  @Test
  fun start() {
    server.enqueueResponse(
      moshi,
      200,
      RegisterEventsResponse(RegisterEventsDataResponse(errors = emptyMap())),
    )

    task.start()

    assertThat(server.requestCount).isEqualTo(0)

    component.eventInteractor.trackEvaluationEvent("feature_tag_value", user1, evaluation1)

    val (time, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }

    assertThat(server.requestCount).isEqualTo(1)
    assertThat(time).isAtLeast(990)
  }

  @Test
  fun `sending via EventUpdateListener should reschedule`() {
    server.enqueueResponse(
      moshi,
      200,
      RegisterEventsResponse(RegisterEventsDataResponse(errors = emptyMap())),
    )
    server.enqueueResponse(
      moshi,
      200,
      RegisterEventsResponse(RegisterEventsDataResponse(errors = emptyMap())),
    )

    task.start()

    component.eventInteractor.trackEvaluationEvent("feature_tag_value", user1, evaluation1)
    component.eventInteractor.trackEvaluationEvent("feature_tag_value", user1, evaluation2)

    assertThat(server.requestCount).isEqualTo(0)

    // 3rd event should trigger request and reschedule next flushing
    component.eventInteractor.trackGoalEvent("feature_tag_value", user1, "goal_id_value", 0.4)

    val (time, request) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }

    requireNotNull(request)
    val requestBody = requireNotNull(
      moshi.adapter(RegisterEventsRequest::class.java)
        .fromJson(request.body.readString(Charsets.UTF_8)),
    )

    assertThat(time).isLessThan(100)

    assertThat(requestBody.events.map { it.type })
      .isEqualTo(listOf(EventType.EVALUATION, EventType.EVALUATION, EventType.GOAL))

    component.eventInteractor.trackGoalEvent("feature_tag_value", user1, "goal_id_value", 0.5)

    val (time2, request2) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }

    requireNotNull(request2)
    val requestBody2 = requireNotNull(
      moshi.adapter(RegisterEventsRequest::class.java)
        .fromJson(request2.body.readString(Charsets.UTF_8)),
    )
    assertThat(requestBody2.events).hasSize(1)
    assertThat(time2).isAtLeast(990)
  }

  @Test
  fun `stop should cancel scheduling`() {
    server.enqueueResponse(
      moshi,
      200,
      RegisterEventsResponse(RegisterEventsDataResponse(errors = emptyMap())),
    )

    task.start()
    task.stop()

    val request = server.takeRequest(2, TimeUnit.SECONDS)
    assertThat(request).isNull()
    assertThat(server.requestCount).isEqualTo(0)
  }
}
