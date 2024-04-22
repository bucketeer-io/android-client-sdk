package io.bucketeer.sdk.android.internal.remote

import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.BuildConfig
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.model.SourceID
import io.bucketeer.sdk.android.internal.model.request.GetEvaluationsRequest
import io.bucketeer.sdk.android.internal.model.request.RegisterEventsRequest
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsErrorResponse
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsResponse
import io.bucketeer.sdk.android.mocks.evaluationEvent1
import io.bucketeer.sdk.android.mocks.latencyMetricsEvent1
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
  private lateinit var apiEndpoint: String
  private lateinit var moshi: Moshi

  @Suppress("unused")
  enum class SuccessWithAcceptableCodeTestCase(
    val code: Int,
    val expectedJSONResponse: GetEvaluationsResponse? = null,
    val expectedTextResponse: String? = null,
    val shouldSuccess: Boolean = false,
  ) {
    OKAY_200_VALID_RESPONSE(
      code = 200,
      expectedJSONResponse = GetEvaluationsResponse(
        evaluations = user1Evaluations,
        userEvaluationsId = "user_evaluation_id",
      ),
      expectedTextResponse = null,
      shouldSuccess = true,
    ),
    OKAY_200_NULL_RESPONSE(
      code = 200,
    ),
    OKAY_200_EMPTY_TEXT_RESPONSE(
      code = 200,
      expectedTextResponse = "",
    ),
    OKAY_200_RANDOM_TEXT_RESPONSE(
      code = 200,
      expectedTextResponse = "okay",
    ),
  }

  @Suppress("unused")
  enum class ErrorTestCase(
    val code: Int,
    val expectedClass: Class<*>,
    val expectedResponse: String?,
  ) {
    REDIRECT_REQUEST_301(301, BKTException.RedirectRequestException::class.java, "error: 301"),
    REDIRECT_REQUEST_302(302, BKTException.RedirectRequestException::class.java, "error: 302"),
    BAD_REQUEST(400, BKTException.BadRequestException::class.java, "error: 400"),
    UNAUTHORIZED(401, BKTException.UnauthorizedException::class.java, "error: 401"),
    FORBIDDEN(403, BKTException.ForbiddenException::class.java, "error: 403"),
    NOT_FOUND(404, BKTException.FeatureNotFoundException::class.java, "error: 404"),
    METHOD_NOT_ALLOWED(405, BKTException.InvalidHttpMethodException::class.java, "error: 405"),
    TIMEOUT(
      code = 408,
      expectedClass = BKTException.TimeoutException::class.java,
      // 408 status code, the mock web server will treat it as Socket Exception, we should not control on it error message as it undefined with us.
      // It could be `Request timeout error: timeout` or `Request timeout error: read timeout`
      expectedResponse = null,
    ),
    PAYLOAD_TOO_LARGE(413, BKTException.PayloadTooLargeException::class.java, "error: 413"),
    CLIENT_CLOSED_REQUEST(499, BKTException.ClientClosedRequestException::class.java, "error: 499"),
    INTERNAL_SERVER_ERROR(500, BKTException.InternalServerErrorException::class.java, "error: 500"),
    SERVICE_UNAVAILABLE(503, BKTException.ServiceUnavailableException::class.java, "error: 503"),
    UNKNOWN_SERVER(418, BKTException.UnknownServerException::class.java, "UnknownServerException 418"),
    REDIRECT_REQUEST_301_NULL_BODY_RESPONSE(301, BKTException.RedirectRequestException::class.java, null),
    REDIRECT_REQUEST_302_NULL_BODY_RESPONSE(302, BKTException.RedirectRequestException::class.java, null),
    BAD_REQUEST_NULL_BODY_RESPONSE(400, BKTException.BadRequestException::class.java, null),
    UNAUTHORIZED_NULL_BODY_RESPONSE(401, BKTException.UnauthorizedException::class.java, null),
    FORBIDDEN_NULL_BODY_RESPONSE(403, BKTException.ForbiddenException::class.java, null),
    NOT_FOUND_NULL_BODY_RESPONSE(404, BKTException.FeatureNotFoundException::class.java, null),
    METHOD_NOT_ALLOWED_NULL_BODY_RESPONSE(405, BKTException.InvalidHttpMethodException::class.java, null),
  }

  @Before
  fun setup() {
    server = MockWebServer()
    apiEndpoint = server.url("").toString()
    moshi = DataModule.createMoshi()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `getEvaluations - success`() {
    val expected = GetEvaluationsResponse(
      evaluations = user1Evaluations,
      userEvaluationsId = "user_evaluation_id",
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
      apiEndpoint = apiEndpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.getEvaluations(
      user = user1,
      userEvaluationsId = "user_evaluation_id",
      condition = UserEvaluationCondition(
        evaluatedAt = "1690798100",
        userAttributesUpdated = "true",
      ),
    )

    // assert request
    assertThat(server.requestCount).isEqualTo(1)
    val request = server.takeRequest()
    assertThat(request.method).isEqualTo("POST")
    assertThat(request.path).isEqualTo("/get_evaluations")
    assertThat(
      moshi.adapter(GetEvaluationsRequest::class.java)
        .fromJson(request.body.readString(Charsets.UTF_8)),
    ).isEqualTo(
      GetEvaluationsRequest(
        tag = "feature_tag_value",
        user = user1,
        userEvaluationsId = "user_evaluation_id",
        sourceId = SourceID.ANDROID,
        userEvaluationCondition = UserEvaluationCondition(
          evaluatedAt = "1690798100",
          userAttributesUpdated = "true",
        ),
        sdkVersion = BuildConfig.SDK_VERSION,
      ),
    )

    // assert response
    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
    val success = result as GetEvaluationsResult.Success
    assertThat(success.value).isEqualTo(expected)
    assertThat(success.seconds).isAtLeast(1)
    assertThat(success.sizeByte).isEqualTo(706)
    assertThat(success.featureTag).isEqualTo("feature_tag_value")
  }

  @Test
  fun `getEvaluations - custom timeout`() {
    client = ApiClientImpl(
      apiEndpoint = apiEndpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val (millis, result) = measureTimeMillisWithResult {
      client.getEvaluations(
        user = user1,
        userEvaluationsId = "user_evaluation_id",
        timeoutMillis = TimeUnit.SECONDS.toMillis(2),
        condition = UserEvaluationCondition(
          evaluatedAt = "1690798200",
          userAttributesUpdated = "false",
        ),
      )
    }

    assertThat(millis).isGreaterThan(2_000)
    assertThat(millis).isLessThan(4_000)

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    val failure = result as GetEvaluationsResult.Failure

    assertThat(failure.error).isInstanceOf(BKTException.TimeoutException::class.java)
    assertThat(failure.featureTag).isEqualTo("feature_tag_value")
  }

  @Test
  fun `getEvaluations - default timeout`() {
    client = ApiClientImpl(
      apiEndpoint = apiEndpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val (millis, result) = measureTimeMillisWithResult {
      client.getEvaluations(
        user = user1,
        userEvaluationsId = "user_evaluation_id",
        timeoutMillis = TimeUnit.SECONDS.toMillis(1),
        condition = UserEvaluationCondition(
          evaluatedAt = "1690798200",
          userAttributesUpdated = "false",
        ),
      )
    }

    assertThat(millis).isGreaterThan(1_000)
    assertThat(millis).isLessThan(DEFAULT_REQUEST_TIMEOUT_MILLIS + 4_000)

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    val failure = result as GetEvaluationsResult.Failure

    assertThat(failure.error).isInstanceOf(BKTException.TimeoutException::class.java)
    assertThat(failure.featureTag).isEqualTo("feature_tag_value")
  }

  @Test
  fun `getEvaluations - network error`() {
    client = ApiClientImpl(
      apiEndpoint = "https://thisdoesnotexist.bucketeer.io",
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.getEvaluations(
      user = user1,
      userEvaluationsId = "user_evaluation_id",
      condition = UserEvaluationCondition(
        evaluatedAt = "1690798200",
        userAttributesUpdated = "false",
      ),
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
                  message = case.expectedResponse ?: "",
                ),
              ),
            ),
        ),
    )
    client = ApiClientImpl(
      apiEndpoint = apiEndpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.getEvaluations(
      user = user1,
      userEvaluationsId = "user_evaluation_id",
      condition = UserEvaluationCondition(
        evaluatedAt = "1690799200",
        userAttributesUpdated = "true",
      ),
    )

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    val failure = result as GetEvaluationsResult.Failure
    val error = failure.error

    assertThat(error).isInstanceOf(case.expectedClass)
    if (case.expectedResponse != null) {
      assertThat(error.message).contains(case.expectedResponse)
    }
  }

  @Test
  fun `getEvaluations - error without body`(@TestParameter case: ErrorTestCase) {
    server.enqueue(
      MockResponse()
        .setResponseCode(case.code)
        .apply {
          if (case.expectedResponse != null) {
            this.setBody(case.expectedResponse)
          }
        },
    )
    client = ApiClientImpl(
      apiEndpoint = apiEndpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.getEvaluations(
      user = user1,
      userEvaluationsId = "user_evaluation_id",
      condition = UserEvaluationCondition(
        evaluatedAt = "1690799200",
        userAttributesUpdated = "true",
      ),
    )

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    val failure = result as GetEvaluationsResult.Failure
    val error = failure.error

    assertThat(error).isInstanceOf(case.expectedClass)
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
                errors = mapOf(
                  evaluationEvent1.id to RegisterEventsErrorResponse(
                    retriable = true,
                    message = "error",
                  ),
                ),
              ),
            ),
        ),
    )
    client = ApiClientImpl(
      apiEndpoint = apiEndpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.registerEvents(events = listOf(evaluationEvent1, latencyMetricsEvent1))

    // assert request
    val request = server.takeRequest()
    assertThat(server.requestCount).isEqualTo(1)
    assertThat(request.method).isEqualTo("POST")
    assertThat(request.path).isEqualTo("/register_events")
    assertThat(
      moshi.adapter(RegisterEventsRequest::class.java)
        .fromJson(request.body.readString(Charsets.UTF_8)),
    ).isEqualTo(
      RegisterEventsRequest(
        events = listOf(evaluationEvent1, latencyMetricsEvent1),
        sdkVersion = BuildConfig.SDK_VERSION,
        sourceId = SourceID.ANDROID,
      ),
    )

    // assert response
    assertThat(result).isInstanceOf(RegisterEventsResult.Success::class.java)
    val success = result as RegisterEventsResult.Success
    assertThat(success.value).isEqualTo(
      RegisterEventsResponse(
        errors = mapOf(
          evaluationEvent1.id to RegisterEventsErrorResponse(
            retriable = true,
            message = "error",
          ),
        ),
      ),
    )
  }

  @Test
  fun `registerEvents - timeout`() {
    client = ApiClientImpl(
      apiEndpoint = apiEndpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
      defaultRequestTimeoutMillis = 1_000,
    )

    val (millis, result) = measureTimeMillisWithResult {
      client.registerEvents(events = listOf(evaluationEvent1, latencyMetricsEvent1))
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
      apiEndpoint = "https://thisdoesnotexist.bucketeer.io",
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.registerEvents(events = listOf(evaluationEvent1, latencyMetricsEvent1))

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
                  message = case.expectedResponse ?: "",
                ),
              ),
            ),
        ),
    )
    client = ApiClientImpl(
      apiEndpoint = apiEndpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.registerEvents(events = listOf(evaluationEvent1, latencyMetricsEvent1))

    assertThat(result).isInstanceOf(RegisterEventsResult.Failure::class.java)
    val failure = result as RegisterEventsResult.Failure
    val error = failure.error

    assertThat(error).isInstanceOf(case.expectedClass)
    if (case.expectedResponse != null) {
      assertThat(error.message).contains(case.expectedResponse)
    }
  }

  @Test
  fun `registerEvents - error without body`(@TestParameter case: ErrorTestCase) {
    server.enqueue(
      MockResponse()
        .setResponseCode(case.code)
        .apply {
          if (case.expectedResponse != null) {
            this.setBody(case.expectedResponse)
          }
        },
    )
    client = ApiClientImpl(
      apiEndpoint = apiEndpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.registerEvents(events = listOf(evaluationEvent1, latencyMetricsEvent1))

    assertThat(result).isInstanceOf(RegisterEventsResult.Failure::class.java)
    val failure = result as RegisterEventsResult.Failure
    val error = failure.error

    assertThat(error).isInstanceOf(case.expectedClass)
    assertThat(error.message).doesNotContain("${case.code}")
  }

  @Test
  fun `test request success with acceptable code`(@TestParameter case: SuccessWithAcceptableCodeTestCase) {
    server.enqueue(
      MockResponse()
        .setBodyDelay(1, TimeUnit.SECONDS)
        .apply {
          if (case.expectedJSONResponse != null) {
            this.setBody(
              moshi.adapter(GetEvaluationsResponse::class.java).toJson(case.expectedJSONResponse),
            )
          } else if (case.expectedTextResponse != null) {
            this.setBody(case.expectedTextResponse)
          }
        }
        .setResponseCode(200),
    )

    client = ApiClientImpl(
      apiEndpoint = apiEndpoint,
      apiKey = "api_key_value",
      featureTag = "feature_tag_value",
      moshi = moshi,
    )

    val result = client.getEvaluations(
      user = user1,
      userEvaluationsId = "user_evaluation_id",
      condition = UserEvaluationCondition(
        evaluatedAt = "1690798100",
        userAttributesUpdated = "true",
      ),
    )

    if (case.shouldSuccess) {
      // assert response
      assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
      val success = result as GetEvaluationsResult.Success
      assertThat(success.value).isEqualTo(case.expectedJSONResponse)
    } else {
      assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
      val failure = result as GetEvaluationsResult.Failure

      assertThat(failure.error).isInstanceOf(BKTException.UnknownServerException::class.java)
      val error = failure.error as BKTException.UnknownServerException
      assertThat(error.statusCode).isEqualTo(case.code)
    }
  }
}
