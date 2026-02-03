package io.bucketeer.sdk.android.internal.scheduler

import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.createTestBKTConfig
import io.bucketeer.sdk.android.enqueueResponse
import io.bucketeer.sdk.android.internal.di.ComponentImpl
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.di.InteractorModule
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse
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
    val config =
      createTestBKTConfig(
        apiKey = "api_key_value",
        apiEndpoint = server.url("").toString(),
        featureTag = "feature_tag_value",
        eventsMaxBatchQueueCount = 3,
        pollingInterval = 1000,
        appVersion = "1.2.3",
      )

    executor = Executors.newSingleThreadScheduledExecutor()

    component =
      ComponentImpl(
        dataModule =
          DataModule(
            application = ApplicationProvider.getApplicationContext(),
            config = config,
            user = user1,
            executor = executor,
            inMemoryDB = true,
          ),
        interactorModule =
          InteractorModule(
            mainHandler = Handler(Looper.getMainLooper()),
          ),
      )

    moshi = component.dataModule.moshi


    task = EvaluationForegroundTask(component, executor)
  }

  @After
  fun tearDown() {
    task.stop()
    server.shutdown()
    executor.submit {
      component.dataModule.destroy()
    }
  }

  @Test
  fun start() {
    server.enqueueResponse(
      moshi,
      200,
      GetEvaluationsResponse(
        evaluations = user1Evaluations,
        userEvaluationsId = "user_evaluations_id_value",
      ),
    )

    task.start()
    assertThat(server.requestCount).isEqualTo(0)

    val (time, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }

    assertThat(server.requestCount).isEqualTo(1)
    assertThat(time).isAtLeast(980L)

    // Should continue scheduling after success when retryCount is 0
    val (time2, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(server.requestCount).isEqualTo(2)
    assertThat(time2).isAtLeast(980L)
  }

  @Test
  fun `stop should cancel scheduling`() {
    server.enqueueResponse(
      moshi,
      200,
      GetEvaluationsResponse(
        evaluations = user1Evaluations,
        userEvaluationsId = "user_evaluations_id_value",
      ),
    )

    task.start()
    assertThat(server.requestCount).isEqualTo(0)

    val (time, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }

    assertThat(server.requestCount).isEqualTo(1)
    assertThat(time).isAtLeast(980L)

    task.stop()

    val request = server.takeRequest(2, TimeUnit.SECONDS)
    assertThat(request).isNull()
    assertThat(server.requestCount).isEqualTo(1)
  }

  @Test
  fun `retry - should back to normal interval after maxRetryCount`() {
    task =
      EvaluationForegroundTask(
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
        evaluations = user1Evaluations,
        userEvaluationsId = "user_evaluations_id_value",
      ),
    )

    task.start()

    // initial request
    val (time1, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }

    assertThat(time1).isAtLeast(990L)

    // retry request 1
    val (time2, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }

    assertThat(time2).isAtLeast(790L)
    assertThat(time2).isLessThan(1000L)

    // retry request 2
    val (time3, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time3).isAtLeast(790L)
    assertThat(time3).isLessThan(1000L)

    // retry request 3
    val (time4, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time4).isAtLeast(790L)
    assertThat(time4).isLessThan(1000L)

    // back to normal interval after retryMaxCount
    val (time5, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time5).isAtLeast(990L)

    // and then retry interval again
    val (time6, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time6).isAtLeast(790L)
    assertThat(time6).isLessThan(1000L)
  }

  @Test
  fun `retry - should back to normal after successful request`() {
    task =
      EvaluationForegroundTask(
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
        evaluations = user1Evaluations,
        userEvaluationsId = "user_evaluations_id_value",
      ),
    )
    server.enqueueResponse(
      moshi,
      200,
      GetEvaluationsResponse(
        evaluations = user1Evaluations,
        userEvaluationsId = "user_evaluations_id_value",
      ),
    )

    task.start()

    // initial request
    val (time1, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }

    assertThat(time1).isAtLeast(990L)

    // retry request 1
    val (time2, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }

    assertThat(time2).isAtLeast(790L)
    assertThat(time2).isLessThan(1000L)

    // back to normal interval after successful request
    val (time3, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time3).isAtLeast(990L)
  }

  @Test
  fun `retry - should not retry when pollingInterval is short enough`() {
    // Set up a config with pollingInterval <= retryPollingInterval
    val shortPollingConfig =
      createTestBKTConfig(
        apiKey = "api_key_value",
        apiEndpoint = server.url("").toString(),
        featureTag = "feature_tag_value",
        eventsMaxBatchQueueCount = 3,
        pollingInterval = 500, // shorter than retry interval
        appVersion = "1.2.3",
      )

    val shortPollingComponent =
      ComponentImpl(
        dataModule =
          DataModule(
            application = ApplicationProvider.getApplicationContext(),
            config = shortPollingConfig,
            user = user1,
            executor = executor,
            inMemoryDB = true,
          ),
        interactorModule =
          InteractorModule(
            mainHandler = Handler(Looper.getMainLooper()),
          ),
      )

    task =
      EvaluationForegroundTask(
        shortPollingComponent,
        executor,
        retryPollingInterval = 800, // longer than pollingInterval
        maxRetryCount = 3,
      )

    // Enqueue error responses
    server.enqueueResponse(moshi, 500, ErrorResponse(ErrorResponse.ErrorDetail(500, "500 error")))
    server.enqueueResponse(moshi, 500, ErrorResponse(ErrorResponse.ErrorDetail(500, "500 error")))
    server.enqueueResponse(moshi, 500, ErrorResponse(ErrorResponse.ErrorDetail(500, "500 error")))

    task.start()

    // Note: adding 150ms buffer to account for execution/mock server delays
    // 490L < x < pollingInterval + 150ms
    // initial request
    val (time1, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time1).isAtLeast(490L)
    assertThat(time1).isLessThan(690L)
    assertThat(server.requestCount).isEqualTo(1)

    // subsequent requests should continue with normal polling interval, not retry interval
    val (time2, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time2).isAtLeast(490L)
    assertThat(time2).isLessThan(600L)
    assertThat(server.requestCount).isEqualTo(2)

    val (time3, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time3).isAtLeast(490L)
    assertThat(time3).isLessThan(600L)
    assertThat(server.requestCount).isEqualTo(3)

    // Cleanup
    executor.submit {
      shortPollingComponent.dataModule.destroy()
    }
  }

  @Test
  fun `should continue scheduling after error when pollingInterval equal retryInterval`() {
    // Set up a config with pollingInterval equal to retryPollingInterval
    val equalIntervalConfig =
      createTestBKTConfig(
        apiKey = "api_key_value",
        apiEndpoint = server.url("").toString(),
        featureTag = "feature_tag_value",
        eventsMaxBatchQueueCount = 3,
        pollingInterval = 800, // equal to retry interval
        appVersion = "1.2.3",
      )

    val equalIntervalComponent =
      ComponentImpl(
        dataModule =
          DataModule(
            application = ApplicationProvider.getApplicationContext(),
            config = equalIntervalConfig,
            user = user1,
            executor = executor,
            inMemoryDB = true,
          ),
        interactorModule =
          InteractorModule(
            mainHandler = Handler(Looper.getMainLooper()),
          ),
      )

    task =
      EvaluationForegroundTask(
        equalIntervalComponent,
        executor,
        retryPollingInterval = 800, // equal to pollingInterval
        maxRetryCount = 3,
      )

    // Enqueue multiple error responses followed by a success
    server.enqueueResponse(moshi, 500, ErrorResponse(ErrorResponse.ErrorDetail(500, "500 error")))
    server.enqueueResponse(moshi, 500, ErrorResponse(ErrorResponse.ErrorDetail(500, "500 error")))
    server.enqueueResponse(moshi, 500, ErrorResponse(ErrorResponse.ErrorDetail(500, "500 error")))
    server.enqueueResponse(
      moshi,
      200,
      GetEvaluationsResponse(
        evaluations = user1Evaluations,
        userEvaluationsId = "user_evaluations_id_value",
      ),
    )

    task.start()

    // All requests should continue with the same interval (790ms < x < retryPollingInterval + 150ms ) despite errors
    // Note: adding 150ms buffer to account for execution/mock server delays
    val (time1, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time1).isAtLeast(790L)
    assertThat(time1).isLessThan(950L)
    assertThat(server.requestCount).isEqualTo(1)

    val (time2, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time2).isAtLeast(790L)
    assertThat(time2).isLessThan(950L)
    assertThat(server.requestCount).isEqualTo(2)

    val (time3, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time3).isAtLeast(790L)
    assertThat(time3).isLessThan(950L)
    assertThat(server.requestCount).isEqualTo(3)

    // Success request should also continue with same interval
    val (time4, _) = measureTimeMillisWithResult { server.takeRequest(2, TimeUnit.SECONDS) }
    assertThat(time4).isAtLeast(790L)
    assertThat(time4).isLessThan(950L)
    assertThat(server.requestCount).isEqualTo(4)

    // Cleanup
    executor.submit {
      equalIntervalComponent.dataModule.destroy()
    }
  }
}
