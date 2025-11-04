package io.bucketeer.sdk.android.internal.remote

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.BuildConfig
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.model.SourceId
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsResponse
import io.bucketeer.sdk.android.mocks.evaluationEvent1
import io.bucketeer.sdk.android.mocks.user1
import io.bucketeer.sdk.android.mocks.user1Evaluations
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
internal class ApiClientImplRetryTest {
  private lateinit var server: MockWebServer
  private lateinit var client: ApiClientImpl
  private lateinit var apiEndpoint: String
  private lateinit var moshi: Moshi
  private lateinit var mockClientClosedRequestResponse: MockResponse
  private lateinit var mockOtherErrorStatusResponse: MockResponse
  private lateinit var mockSuccessResponse: MockResponse

  @Before
  fun setup() {
    server = MockWebServer()
    apiEndpoint = server.url("").toString()
    moshi = DataModule.createMoshi()
    mockClientClosedRequestResponse =
      MockResponse()
        .setResponseCode(499)
        .setBody(
          moshi
            .adapter(ErrorResponse::class.java)
            .toJson(
              ErrorResponse(
                ErrorResponse.ErrorDetail(
                  code = 499,
                  message = "client closed request",
                ),
              ),
            ),
        )
    mockOtherErrorStatusResponse =
      MockResponse()
        .setResponseCode(530)
        .setBody(
          moshi
            .adapter(ErrorResponse::class.java)
            .toJson(
              ErrorResponse(
                ErrorResponse.ErrorDetail(
                  code = 530,
                  message = "custom server error",
                ),
              ),
            ),
        )
    mockSuccessResponse =
      MockResponse()
        .setBodyDelay(1, TimeUnit.SECONDS)
        .apply {
          this.setBody(
            moshi.adapter(GetEvaluationsResponse::class.java).toJson(
              GetEvaluationsResponse(
                evaluations = user1Evaluations,
                userEvaluationsId = "user_evaluation_id",
              ),
            ),
          )
        }.setResponseCode(200)
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  /*
  cases
  200 -> done, no retry, request count = 1
  530 -> error, no retry, request count = 1
  499, 530 -> error, retry once, request count = 2
  499, 200 -> done okay, request count = 2
  499, 499, 200 -> done okay, retry twice, request count = 3
  499, 499, 499, 200 -> done okay, max retry reached, request count = 4
  499, 499, 499, 530 -> error, max retry reached, request count = 4
   */

  @Test
  fun `getEvaluations - success - 200 no retry`() {
    server.enqueue(mockSuccessResponse)

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result =
      client.getEvaluations(
        user = user1,
        userEvaluationsId = "user_evaluation_id",
        condition =
          UserEvaluationCondition(
            evaluatedAt = "1690799200",
            userAttributesUpdated = true,
          ),
      )

    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
    assertThat(server.requestCount).isEqualTo(1)
  }

  @Test
  fun `getEvaluations - error 530 no retry`() {
    server.enqueue(mockOtherErrorStatusResponse)

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result =
      client.getEvaluations(
        user = user1,
        userEvaluationsId = "user_evaluation_id",
        condition =
          UserEvaluationCondition(
            evaluatedAt = "1690799200",
            userAttributesUpdated = true,
          ),
      )

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    val failure = result as GetEvaluationsResult.Failure
    assertThat(failure.error).isInstanceOf(BKTException.UnknownServerException::class.java)
    assertThat(server.requestCount).isEqualTo(1)
  }

  @Test
  fun `getEvaluations - error 499 then 530 retry once and stop`() {
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockOtherErrorStatusResponse)

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result =
      client.getEvaluations(
        user = user1,
        userEvaluationsId = "user_evaluation_id",
        condition =
          UserEvaluationCondition(
            evaluatedAt = "1690799200",
            userAttributesUpdated = true,
          ),
      )

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    val failure = result as GetEvaluationsResult.Failure
    assertThat(failure.error).isInstanceOf(BKTException.UnknownServerException::class.java)
    assertThat(server.requestCount).isEqualTo(2)
  }

  @Test
  fun `getEvaluations - error 499 then 200 success`() {
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockSuccessResponse)

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result =
      client.getEvaluations(
        user = user1,
        userEvaluationsId = "user_evaluation_id",
        condition =
          UserEvaluationCondition(
            evaluatedAt = "1690799200",
            userAttributesUpdated = true,
          ),
      )

    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
    assertThat(server.requestCount).isEqualTo(2)
  }

  @Test
  fun `getEvaluations - error 499 499 then 200 success with 2 retries`() {
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockSuccessResponse)

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result =
      client.getEvaluations(
        user = user1,
        userEvaluationsId = "user_evaluation_id",
        condition =
          UserEvaluationCondition(
            evaluatedAt = "1690799200",
            userAttributesUpdated = true,
          ),
      )

    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
    assertThat(server.requestCount).isEqualTo(3)
  }

  @Test
  fun `getEvaluations - error 499 499 499 then 200 success with max 3 retries`() {
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockSuccessResponse)

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result =
      client.getEvaluations(
        user = user1,
        userEvaluationsId = "user_evaluation_id",
        condition =
          UserEvaluationCondition(
            evaluatedAt = "1690799200",
            userAttributesUpdated = true,
          ),
      )

    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
    assertThat(server.requestCount).isEqualTo(4)
  }

  @Test
  fun `getEvaluations - error 499 499 499 then 530 max retry reached and fail`() {
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockOtherErrorStatusResponse)

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result =
      client.getEvaluations(
        user = user1,
        userEvaluationsId = "user_evaluation_id",
        condition =
          UserEvaluationCondition(
            evaluatedAt = "1690799200",
            userAttributesUpdated = true,
          ),
      )

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    val failure = result as GetEvaluationsResult.Failure
    assertThat(failure.error).isInstanceOf(BKTException.UnknownServerException::class.java)
    assertThat(server.requestCount).isEqualTo(4)
  }

  @Test
  fun `should not retry when got 3xx error`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(301)
        .setBody(
          moshi
            .adapter(ErrorResponse::class.java)
            .toJson(
              ErrorResponse(
                ErrorResponse.ErrorDetail(
                  code = 301,
                  message = "redirect",
                ),
              ),
            ),
        ),
    )

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result =
      client.getEvaluations(
        user = user1,
        userEvaluationsId = "user_evaluation_id",
        condition =
          UserEvaluationCondition(
            evaluatedAt = "1690799200",
            userAttributesUpdated = true,
          ),
      )

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    val failure = result as GetEvaluationsResult.Failure
    assertThat(failure.error).isInstanceOf(BKTException.RedirectRequestException::class.java)
    assertThat(server.requestCount).isEqualTo(1)
  }

  @Test
  fun `should not retry when got 4xx error except 499`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(400)
        .setBody(
          moshi
            .adapter(ErrorResponse::class.java)
            .toJson(
              ErrorResponse(
                ErrorResponse.ErrorDetail(
                  code = 400,
                  message = "bad request",
                ),
              ),
            ),
        ),
    )

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result =
      client.getEvaluations(
        user = user1,
        userEvaluationsId = "user_evaluation_id",
        condition =
          UserEvaluationCondition(
            evaluatedAt = "1690799200",
            userAttributesUpdated = true,
          ),
      )

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    val failure = result as GetEvaluationsResult.Failure
    assertThat(failure.error).isInstanceOf(BKTException.BadRequestException::class.java)
    assertThat(server.requestCount).isEqualTo(1)
  }

  @Test
  fun `should not retry when got 5xx error`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(500)
        .setBody(
          moshi
            .adapter(ErrorResponse::class.java)
            .toJson(
              ErrorResponse(
                ErrorResponse.ErrorDetail(
                  code = 500,
                  message = "internal server error",
                ),
              ),
            ),
        ),
    )

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result =
      client.getEvaluations(
        user = user1,
        userEvaluationsId = "user_evaluation_id",
        condition =
          UserEvaluationCondition(
            evaluatedAt = "1690799200",
            userAttributesUpdated = true,
          ),
      )

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    val failure = result as GetEvaluationsResult.Failure
    assertThat(failure.error).isInstanceOf(BKTException.InternalServerErrorException::class.java)
    assertThat(server.requestCount).isEqualTo(1)
  }

  @Test
  fun `registerEvents - success - 200 no retry`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(RegisterEventsResponse::class.java).toJson(
            RegisterEventsResponse(
              errors = emptyMap(),
            ),
          ),
        ),
    )

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result = client.registerEvents(events = listOf(evaluationEvent1))

    assertThat(result).isInstanceOf(RegisterEventsResult.Success::class.java)
    assertThat(server.requestCount).isEqualTo(1)
  }

  @Test
  fun `registerEvents - error 530 no retry`() {
    server.enqueue(mockOtherErrorStatusResponse)

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result = client.registerEvents(events = listOf(evaluationEvent1))

    assertThat(result).isInstanceOf(RegisterEventsResult.Failure::class.java)
    val failure = result as RegisterEventsResult.Failure
    assertThat(failure.error).isInstanceOf(BKTException.UnknownServerException::class.java)
    assertThat(server.requestCount).isEqualTo(1)
  }

  @Test
  fun `registerEvents - error 499 then 530 retry once and stop`() {
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockOtherErrorStatusResponse)

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result = client.registerEvents(events = listOf(evaluationEvent1))

    assertThat(result).isInstanceOf(RegisterEventsResult.Failure::class.java)
    val failure = result as RegisterEventsResult.Failure
    assertThat(failure.error).isInstanceOf(BKTException.UnknownServerException::class.java)
    assertThat(server.requestCount).isEqualTo(2)
  }

  @Test
  fun `registerEvents - error 499 then 200 success`() {
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(RegisterEventsResponse::class.java).toJson(
            RegisterEventsResponse(
              errors = emptyMap(),
            ),
          ),
        ),
    )

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result = client.registerEvents(events = listOf(evaluationEvent1))

    assertThat(result).isInstanceOf(RegisterEventsResult.Success::class.java)
    assertThat(server.requestCount).isEqualTo(2)
  }

  @Test
  fun `registerEvents - error 499 499 then 200 success with 2 retries`() {
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(RegisterEventsResponse::class.java).toJson(
            RegisterEventsResponse(
              errors = emptyMap(),
            ),
          ),
        ),
    )

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result = client.registerEvents(events = listOf(evaluationEvent1))

    assertThat(result).isInstanceOf(RegisterEventsResult.Success::class.java)
    assertThat(server.requestCount).isEqualTo(3)
  }

  @Test
  fun `registerEvents - error 499 499 499 then 200 success with max 3 retries`() {
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(RegisterEventsResponse::class.java).toJson(
            RegisterEventsResponse(
              errors = emptyMap(),
            ),
          ),
        ),
    )

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result = client.registerEvents(events = listOf(evaluationEvent1))

    assertThat(result).isInstanceOf(RegisterEventsResult.Success::class.java)
    assertThat(server.requestCount).isEqualTo(4)
  }

  @Test
  fun `registerEvents - error 499 499 499 then 530 max retry reached and fail`() {
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockClientClosedRequestResponse)
    server.enqueue(mockOtherErrorStatusResponse)

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result = client.registerEvents(events = listOf(evaluationEvent1))

    assertThat(result).isInstanceOf(RegisterEventsResult.Failure::class.java)
    val failure = result as RegisterEventsResult.Failure
    assertThat(failure.error).isInstanceOf(BKTException.UnknownServerException::class.java)
    assertThat(server.requestCount).isEqualTo(4)
  }

  @Test
  fun `should not retry when got 3xx error for registerEvents`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(301)
        .setBody(
          moshi
            .adapter(ErrorResponse::class.java)
            .toJson(
              ErrorResponse(
                ErrorResponse.ErrorDetail(
                  code = 301,
                  message = "redirect",
                ),
              ),
            ),
        ),
    )

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result =
      client.registerEvents(
        events = listOf(evaluationEvent1),
      )

    assertThat(result).isInstanceOf(RegisterEventsResult.Failure::class.java)
    val failure = result as RegisterEventsResult.Failure
    assertThat(failure.error).isInstanceOf(BKTException.RedirectRequestException::class.java)
    assertThat(server.requestCount).isEqualTo(1)
  }

  @Test
  fun `should not retry when got 4xx error except 499 for registerEvents`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(400)
        .setBody(
          moshi
            .adapter(ErrorResponse::class.java)
            .toJson(
              ErrorResponse(
                ErrorResponse.ErrorDetail(
                  code = 400,
                  message = "bad request",
                ),
              ),
            ),
        ),
    )

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result =
      client.registerEvents(
        events = listOf(evaluationEvent1),
      )

    assertThat(result).isInstanceOf(RegisterEventsResult.Failure::class.java)
    val failure = result as RegisterEventsResult.Failure
    assertThat(failure.error).isInstanceOf(BKTException.BadRequestException::class.java)
    assertThat(server.requestCount).isEqualTo(1)
  }

  @Test
  fun `should not retry when got 5xx error for registerEvents`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(500)
        .setBody(
          moshi
            .adapter(ErrorResponse::class.java)
            .toJson(
              ErrorResponse(
                ErrorResponse.ErrorDetail(
                  code = 500,
                  message = "internal server error",
                ),
              ),
            ),
        ),
    )

    client =
      ApiClientImpl(
        apiEndpoint = apiEndpoint,
        apiKey = "api_key_value",
        featureTag = "feature_tag_value",
        moshi = moshi,
        sourceId = SourceId.ANDROID,
        sdkVersion = BuildConfig.SDK_VERSION,
      )

    val result =
      client.registerEvents(
        events = listOf(evaluationEvent1),
      )

    assertThat(result).isInstanceOf(RegisterEventsResult.Failure::class.java)
    val failure = result as RegisterEventsResult.Failure
    assertThat(failure.error).isInstanceOf(BKTException.InternalServerErrorException::class.java)
    assertThat(server.requestCount).isEqualTo(1)
  }
}
