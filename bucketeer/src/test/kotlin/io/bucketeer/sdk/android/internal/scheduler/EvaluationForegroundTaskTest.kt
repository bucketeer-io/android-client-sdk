package io.bucketeer.sdk.android.internal.scheduler

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.createTestBKTConfig
import io.bucketeer.sdk.android.enqueueResponse
import io.bucketeer.sdk.android.internal.di.ComponentImpl
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.di.InteractorModule
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsDataResponse
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.remote.measureTimeMillisWithResult
import io.bucketeer.sdk.android.mocks.user1
import io.bucketeer.sdk.android.mocks.user1Evaluations
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
class EvaluationForegroundTaskTest {
  private lateinit var server: MockWebServer

  private lateinit var component: ComponentImpl
  private lateinit var moshi: Moshi
  private lateinit var executor: ScheduledExecutorService
  private lateinit var task: EvaluationForegroundTask

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
          pollingInterval = 1000,
        ),
        user = user1,
        inMemoryDB = true,
      ),
      interactorModule = InteractorModule(),
    )

    moshi = component.dataModule.moshi

    executor = Executors.newSingleThreadScheduledExecutor()

    task = EvaluationForegroundTask(component, executor)
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
      GetEvaluationsResponse(
        GetEvaluationsDataResponse(
          evaluations = user1Evaluations,
          user_evaluations_id = "user_evaluations_id_value",
        ),
      ),
    )

    task.start()
    assertThat(server.requestCount).isEqualTo(0)

    val (time, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }

    assertThat(server.requestCount).isEqualTo(1)
    assertThat(time).isAtLeast(980)
  }

  @Test
  fun `stop should cancel scheduling`() {
    server.enqueueResponse(
      moshi,
      200,
      GetEvaluationsResponse(
        GetEvaluationsDataResponse(
          evaluations = user1Evaluations,
          user_evaluations_id = "user_evaluations_id_value",
        ),
      ),
    )

    task.start()
    task.stop()

    val request = server.takeRequest(2, TimeUnit.SECONDS)
    assertThat(request).isNull()
    assertThat(server.requestCount).isEqualTo(0)
  }

  @Test
  fun `retry - should back to normal interval after maxRetryCount`() {
    task = EvaluationForegroundTask(
      component,
      executor,
      retryPollingInterval = 800,
      maxRetryCount = 3,
    )

    server.enqueueResponse(moshi, 500, ErrorResponse(ErrorResponse.ErrorDetail(500, "500 error")))
    server.enqueueResponse(moshi, 500, ErrorResponse(ErrorResponse.ErrorDetail(500, "500 error")))
    server.enqueueResponse(moshi, 500, ErrorResponse(ErrorResponse.ErrorDetail(500, "500 error")))
    server.enqueueResponse(moshi, 500, ErrorResponse(ErrorResponse.ErrorDetail(500, "500 error")))
    server.enqueueResponse(moshi, 500, ErrorResponse(ErrorResponse.ErrorDetail(500, "500 error")))
    server.enqueueResponse(
      moshi,
      500,
      GetEvaluationsResponse(
        GetEvaluationsDataResponse(
          evaluations = user1Evaluations,
          user_evaluations_id = "user_evaluations_id_value",
        ),
      ),
    )

    task.start()

    // initial request
    val (time1, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }

    assertThat(time1).isAtLeast(990)

    // retry request 1
    val (time2, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }

    assertThat(time2).isAtLeast(790)
    assertThat(time2).isLessThan(1000)

    // retry request 2
    val (time3, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time3).isAtLeast(790)
    assertThat(time3).isLessThan(1000)

    // retry request 3
    val (time4, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time4).isAtLeast(790)
    assertThat(time4).isLessThan(1000)

    // back to normal interval after retryMaxCount
    val (time5, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time5).isAtLeast(990)

    // and then retry interval again
    val (time6, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time6).isAtLeast(790)
    assertThat(time6).isLessThan(1000)
  }

  @Test
  fun `retry - should back to normal after successful request`() {
    task = EvaluationForegroundTask(
      component,
      executor,
      retryPollingInterval = 800,
      maxRetryCount = 3,
    )

    server.enqueueResponse(moshi, 500, ErrorResponse(ErrorResponse.ErrorDetail(500, "500 error")))
    server.enqueueResponse(
      moshi,
      200,
      GetEvaluationsResponse(
        GetEvaluationsDataResponse(
          evaluations = user1Evaluations,
          user_evaluations_id = "user_evaluations_id_value",
        ),
      ),
    )
    server.enqueueResponse(
      moshi,
      200,
      GetEvaluationsResponse(
        GetEvaluationsDataResponse(
          evaluations = user1Evaluations,
          user_evaluations_id = "user_evaluations_id_value",
        ),
      ),
    )

    task.start()

    // initial request
    val (time1, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }

    assertThat(time1).isAtLeast(990)

    // retry request 1
    val (time2, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }

    assertThat(time2).isAtLeast(790)
    assertThat(time2).isLessThan(1000)

    // back to normal interval after successful request
    val (time3, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time3).isAtLeast(990)
  }
}
