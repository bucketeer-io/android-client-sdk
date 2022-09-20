package io.bucketeer.sdk.android.internal.remote

import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.model.SourceID
import io.bucketeer.sdk.android.internal.model.request.GetEvaluationsRequest
import io.bucketeer.sdk.android.internal.model.request.RegisterEventsRequest
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsDataResponse
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsDataResponse
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsErrorResponse
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsResponse
import io.bucketeer.sdk.android.mocks.evaluationEvent1
import io.bucketeer.sdk.android.mocks.metricsEvent1
import io.bucketeer.sdk.android.mocks.user1
import io.bucketeer.sdk.android.mocks.user1Evaluations
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(TestParameterInjector::class)
internal class ApiClientImplTest {
  private lateinit var server: MockWebServer
  private lateinit var client: ApiClientImpl
  private lateinit var endpoint: String
  private lateinit var moshi: Moshi

  @Suppress("unused")
  enum class ErrorTestCase(
    val code: Int,
    val expected: Class<*>,
  ) {
    BAD_REQUEST(400, BKTException.BadRequestException::class.java),
    UNAUTHORIZED(401, BKTException.UnauthorizedException::class.java),
    NOT_FOUND(404, BKTException.FeatureNotFoundException::class.java),
    METHOD_NOT_ALLOWED(405, BKTException.InvalidHttpMethodException::class.java),
    INTERNAL_SERVER_ERROR(500, BKTException.ApiServerException::class.java),
  }

  @Before
  fun setup() {
    server = MockWebServer()
    endpoint = server.url("").toString()
    moshi = DataModule.createMoshi()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `getEvaluations - success`() {
    val expected = GetEvaluationsResponse(
      data = GetEvaluationsDataResponse(
        evaluations = user1Evaluations,
        user_evaluations_id = "user_evaluation_id",
      ),
    )
    server.enqueue(
      MockResponse()
        .setBodyDelay(1, TimeUnit.SECONDS)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java).toJson(expected),
        )
        .setResponseCode(200),
    )

    client = ApiClientImpl(
      endpoint = endpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.getEvaluations(
      user = user1,
      userEvaluationsId = "user_evaluation_id",
    )

    // assert request
    assertThat(server.requestCount).isEqualTo(1)
    val request = server.takeRequest()
    assertThat(request.method).isEqualTo("POST")
    assertThat(request.path).isEqualTo("/v1/gateway/evaluations")
    assertThat(
      moshi.adapter(GetEvaluationsRequest::class.java)
        .fromJson(request.body.readString(Charsets.UTF_8)),
    ).isEqualTo(
      GetEvaluationsRequest(
        tag = "feature_tag_value",
        user = user1,
        user_evaluations_id = "user_evaluation_id",
        source_id = SourceID.ANDROID,
      ),
    )

    // assert response
    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
    val success = result as GetEvaluationsResult.Success
    assertThat(success.value).isEqualTo(expected)
    assertThat(success.seconds).isAtLeast(1)
    assertThat(success.sizeByte).isEqualTo(727)
    assertThat(success.featureTag).isEqualTo("feature_tag_value")
  }

  @Test
  fun `getEvaluations - default timeout`() {
    client = ApiClientImpl(
      endpoint = endpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
      defaultRequestTimeoutMillis = 1_000,
    )

    val (millis, result) = measureTimeMillisWithResult {
      client.getEvaluations(
        user = user1,
        userEvaluationsId = "user_evaluation_id",
      )
    }

    assertThat(millis).isGreaterThan(1_000)
    assertThat(millis).isLessThan(1_500)

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    val failure = result as GetEvaluationsResult.Failure

    assertThat(failure.error).isInstanceOf(BKTException.TimeoutException::class.java)
    assertThat(failure.featureTag).isEqualTo("feature_tag_value")
  }

  @Test
  fun `getEvaluations - custom timeout`() {
    client = ApiClientImpl(
      endpoint = endpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val (millis, result) = measureTimeMillisWithResult {
      client.getEvaluations(
        user = user1,
        userEvaluationsId = "user_evaluation_id",
        timeoutMillis = TimeUnit.SECONDS.toMillis(1),
      )
    }

    assertThat(millis).isGreaterThan(1_000)
    assertThat(millis).isLessThan(1_500)

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    val failure = result as GetEvaluationsResult.Failure

    assertThat(failure.error).isInstanceOf(BKTException.TimeoutException::class.java)
    assertThat(failure.featureTag).isEqualTo("feature_tag_value")
  }

  @Test
  fun `getEvaluations - network error`() {
    client = ApiClientImpl(
      endpoint = "https://thisdoesnotexist.bucketeer.io",
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.getEvaluations(
      user = user1,
      userEvaluationsId = "user_evaluation_id",
    )

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    val failure = result as GetEvaluationsResult.Failure

    assertThat(failure.error).isInstanceOf(BKTException.NetworkException::class.java)
    assertThat(failure.featureTag).isEqualTo("feature_tag_value")
  }

  @Test
  fun `getEvaluations - error with body`(@TestParameter case: ErrorTestCase) {
    server.enqueue(
      MockResponse()
        .setResponseCode(case.code)
        .setBody(
          moshi.adapter(ErrorResponse::class.java)
            .toJson(
              ErrorResponse(
                ErrorResponse.ErrorDetail(
                  code = case.code,
                  message = "error: ${case.code}",
                ),
              ),
            ),
        ),
    )
    client = ApiClientImpl(
      endpoint = endpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.getEvaluations(
      user = user1,
      userEvaluationsId = "user_evaluation_id",
    )

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    val failure = result as GetEvaluationsResult.Failure
    val error = failure.error

    assertThat(error).isInstanceOf(case.expected)
    assertThat(error.message).isEqualTo("error: ${case.code}")
  }

  @Test
  fun `getEvaluations - error without body`(@TestParameter case: ErrorTestCase) {
    server.enqueue(
      MockResponse()
        .setResponseCode(case.code)
        .setBody("error: ${case.code}"),
    )
    client = ApiClientImpl(
      endpoint = endpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.getEvaluations(
      user = user1,
      userEvaluationsId = "user_evaluation_id",
    )

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    val failure = result as GetEvaluationsResult.Failure
    val error = failure.error

    assertThat(error).isInstanceOf(case.expected)
    assertThat(error.message).doesNotContain("${case.code}")
  }

  @Test
  fun `registerEvents - success`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(RegisterEventsResponse::class.java)
            .toJson(
              RegisterEventsResponse(
                RegisterEventsDataResponse(
                  errors = mapOf(
                    evaluationEvent1.id to RegisterEventsErrorResponse(
                      retriable = true,
                      message = "error",
                    ),
                  ),
                ),
              ),
            ),
        ),
    )
    client = ApiClientImpl(
      endpoint = endpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.registerEvents(events = listOf(evaluationEvent1, metricsEvent1))

    // assert request
    val request = server.takeRequest()
    assertThat(server.requestCount).isEqualTo(1)
    assertThat(request.method).isEqualTo("POST")
    assertThat(request.path).isEqualTo("/v1/gateway/events")
    assertThat(
      moshi.adapter(RegisterEventsRequest::class.java)
        .fromJson(request.body.readString(Charsets.UTF_8)),
    ).isEqualTo(RegisterEventsRequest(events = listOf(evaluationEvent1, metricsEvent1)))

    // assert response
    assertThat(result).isInstanceOf(RegisterEventsResult.Success::class.java)
    val success = result as RegisterEventsResult.Success
    assertThat(success.value).isEqualTo(
      RegisterEventsResponse(
        RegisterEventsDataResponse(
          errors = mapOf(
            evaluationEvent1.id to RegisterEventsErrorResponse(
              retriable = true,
              message = "error",
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `registerEvents - timeout`() {
    client = ApiClientImpl(
      endpoint = endpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
      defaultRequestTimeoutMillis = 1_000,
    )

    val (millis, result) = measureTimeMillisWithResult {
      client.registerEvents(events = listOf(evaluationEvent1, metricsEvent1))
    }

    assertThat(millis).isGreaterThan(1_000)
    assertThat(millis).isLessThan(1_500)

    assertThat(result).isInstanceOf(RegisterEventsResult.Failure::class.java)
    val failure = result as RegisterEventsResult.Failure

    assertThat(failure.error).isInstanceOf(BKTException.TimeoutException::class.java)
  }

  @Test
  fun `registerEvents - network error`() {
    client = ApiClientImpl(
      endpoint = "https://thisdoesnotexist.bucketeer.io",
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.registerEvents(events = listOf(evaluationEvent1, metricsEvent1))

    assertThat(result).isInstanceOf(RegisterEventsResult.Failure::class.java)
    val failure = result as RegisterEventsResult.Failure

    assertThat(failure.error).isInstanceOf(BKTException.NetworkException::class.java)
  }

  @Test
  fun `registerEvents - error with body`(@TestParameter case: ErrorTestCase) {
    server.enqueue(
      MockResponse()
        .setResponseCode(case.code)
        .setBody(
          moshi.adapter(ErrorResponse::class.java)
            .toJson(
              ErrorResponse(
                ErrorResponse.ErrorDetail(
                  code = case.code,
                  message = "error: ${case.code}",
                ),
              ),
            ),
        ),
    )
    client = ApiClientImpl(
      endpoint = endpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.registerEvents(events = listOf(evaluationEvent1, metricsEvent1))

    assertThat(result).isInstanceOf(RegisterEventsResult.Failure::class.java)
    val failure = result as RegisterEventsResult.Failure
    val error = failure.error

    assertThat(error).isInstanceOf(case.expected)
    assertThat(error.message).isEqualTo("error: ${case.code}")
  }

  @Test
  fun `registerEvents - error without body`(@TestParameter case: ErrorTestCase) {
    server.enqueue(
      MockResponse()
        .setResponseCode(case.code)
        .setBody("error: ${case.code}"),
    )
    client = ApiClientImpl(
      endpoint = endpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.registerEvents(events = listOf(evaluationEvent1, metricsEvent1))

    assertThat(result).isInstanceOf(RegisterEventsResult.Failure::class.java)
    val failure = result as RegisterEventsResult.Failure
    val error = failure.error

    assertThat(error).isInstanceOf(case.expected)
    assertThat(error.message).doesNotContain("${case.code}")
  }
}
