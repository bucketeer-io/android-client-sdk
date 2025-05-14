package io.bucketeer.sdk.android.internal.event

import com.google.common.truth.Truth
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.model.ApiId
import io.bucketeer.sdk.android.internal.model.EventData
import io.bucketeer.sdk.android.internal.model.MetricsEventData
import io.bucketeer.sdk.android.internal.model.MetricsEventType
import io.bucketeer.sdk.android.internal.model.SourceId
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
internal class BKTExceptionToEventDataMetricEventTest {
  companion object {
    const val EXPECTED_TIMESTAMP = 19998L
    val expectedLabelsForOtherCases = mapOf("tag" to "android")
    val expectedLabelsForTimeoutCase = mapOf("tag" to "android", "timeout" to "5.1")
    val expectedLabelsForRedirectRequestException = mapOf("tag" to "android", "response_code" to "302")
    val expectedLabelsForUnknownServerException =
      mapOf("tag" to "android", "response_code" to "499", "error_message" to "UnknownServerException")
    val expectedLabelsForUnknownException =
      mapOf("tag" to "android", "error_message" to "UnknownException")
    val expectedApiId = ApiId.GET_EVALUATIONS
    val expectedMetadata = newMetadata("1.0.0")
    val expectedSourceId = SourceId.OPEN_FEATURE_KOTLIN
    const val EXPECTED_SDK_VERSION = "0.0.8"
  }

  @Suppress("unused")
  enum class ErrorTestCase(
    val code: BKTException,
    val expected: EventData.MetricsEvent,
  ) {
    BAD_REQUEST(
      BKTException.BadRequestException(message = ""),
      EventData.MetricsEvent(
        timestamp = EXPECTED_TIMESTAMP,
        type = MetricsEventType.BAD_REQUEST_ERROR,
        event =
          MetricsEventData.BadRequestErrorMetricsEvent(
            apiId = expectedApiId,
            labels = expectedLabelsForOtherCases,
          ),
        sourceId = expectedSourceId,
        sdkVersion = EXPECTED_SDK_VERSION,
        metadata = expectedMetadata,
      ),
    ),
    UNAUTHORIZED(
      BKTException.UnauthorizedException(message = ""),
      EventData.MetricsEvent(
        timestamp = EXPECTED_TIMESTAMP,
        type = MetricsEventType.UNAUTHORIZED_ERROR,
        event =
          MetricsEventData.UnauthorizedErrorMetricsEvent(
            apiId = expectedApiId,
            labels = expectedLabelsForOtherCases,
          ),
        sourceId = expectedSourceId,
        sdkVersion = EXPECTED_SDK_VERSION,
        metadata = expectedMetadata,
      ),
    ),
    FORBIDDEN(
      BKTException.ForbiddenException(message = ""),
      EventData.MetricsEvent(
        timestamp = EXPECTED_TIMESTAMP,
        type = MetricsEventType.FORBIDDEN_ERROR,
        event =
          MetricsEventData.ForbiddenErrorMetricsEvent(
            apiId = expectedApiId,
            labels = expectedLabelsForOtherCases,
          ),
        sourceId = expectedSourceId,
        sdkVersion = EXPECTED_SDK_VERSION,
        metadata = expectedMetadata,
      ),
    ),
    NOT_FOUND(
      BKTException.FeatureNotFoundException(message = ""),
      EventData.MetricsEvent(
        timestamp = EXPECTED_TIMESTAMP,
        type = MetricsEventType.NOT_FOUND_ERROR,
        event =
          MetricsEventData.NotFoundErrorMetricsEvent(
            apiId = expectedApiId,
            labels = expectedLabelsForOtherCases,
          ),
        sourceId = expectedSourceId,
        sdkVersion = EXPECTED_SDK_VERSION,
        metadata = expectedMetadata,
      ),
    ),
    METHOD_NOT_ALLOWED(
      BKTException.InvalidHttpMethodException(message = ""),
      EventData.MetricsEvent(
        timestamp = EXPECTED_TIMESTAMP,
        type = MetricsEventType.INTERNAL_SDK_ERROR,
        event =
          MetricsEventData.InternalSdkErrorMetricsEvent(
            apiId = expectedApiId,
            labels = expectedLabelsForOtherCases,
          ),
        sourceId = expectedSourceId,
        sdkVersion = EXPECTED_SDK_VERSION,
        metadata = expectedMetadata,
      ),
    ),
    ILLEGAL_ARGUMENT(
      BKTException.IllegalArgumentException(message = ""),
      EventData.MetricsEvent(
        timestamp = EXPECTED_TIMESTAMP,
        type = MetricsEventType.INTERNAL_SDK_ERROR,
        event =
          MetricsEventData.InternalSdkErrorMetricsEvent(
            apiId = expectedApiId,
            labels = expectedLabelsForOtherCases,
          ),
        sourceId = expectedSourceId,
        sdkVersion = EXPECTED_SDK_VERSION,
        metadata = expectedMetadata,
      ),
    ),
    ILLEGAL_STATE(
      BKTException.IllegalStateException(message = ""),
      EventData.MetricsEvent(
        timestamp = EXPECTED_TIMESTAMP,
        type = MetricsEventType.INTERNAL_SDK_ERROR,
        event =
          MetricsEventData.InternalSdkErrorMetricsEvent(
            apiId = expectedApiId,
            labels = expectedLabelsForOtherCases,
          ),
        sourceId = expectedSourceId,
        sdkVersion = EXPECTED_SDK_VERSION,
        metadata = expectedMetadata,
      ),
    ),
    CLIENT_CLOSED_REQUEST(
      BKTException.ClientClosedRequestException(message = ""),
      EventData.MetricsEvent(
        timestamp = EXPECTED_TIMESTAMP,
        type = MetricsEventType.CLIENT_CLOSED_REQUEST_ERROR,
        event =
          MetricsEventData.ClientClosedRequestErrorMetricsEvent(
            apiId = expectedApiId,
            labels = expectedLabelsForOtherCases,
          ),
        sourceId = expectedSourceId,
        sdkVersion = EXPECTED_SDK_VERSION,
        metadata = expectedMetadata,
      ),
    ),
    INTERNAL_SERVER_ERROR(
      BKTException.InternalServerErrorException(message = ""),
      EventData.MetricsEvent(
        timestamp = EXPECTED_TIMESTAMP,
        type = MetricsEventType.INTERNAL_SERVER_ERROR,
        event =
          MetricsEventData.InternalServerErrorMetricsEvent(
            apiId = expectedApiId,
            labels = expectedLabelsForOtherCases,
          ),
        sourceId = expectedSourceId,
        sdkVersion = EXPECTED_SDK_VERSION,
        metadata = expectedMetadata,
      ),
    ),
    SERVICE_UNAVAILABLE(
      BKTException.ServiceUnavailableException(message = ""),
      EventData.MetricsEvent(
        timestamp = EXPECTED_TIMESTAMP,
        type = MetricsEventType.SERVICE_UNAVAILABLE_ERROR,
        event =
          MetricsEventData.ServiceUnavailableErrorMetricsEvent(
            apiId = expectedApiId,
            labels = expectedLabelsForOtherCases,
          ),
        sourceId = expectedSourceId,
        sdkVersion = EXPECTED_SDK_VERSION,
        metadata = expectedMetadata,
      ),
    ),
    TIMEOUT(
      BKTException.TimeoutException(message = "", cause = Exception(), timeoutMillis = 5100),
      EventData.MetricsEvent(
        timestamp = EXPECTED_TIMESTAMP,
        type = MetricsEventType.TIMEOUT_ERROR,
        event =
          MetricsEventData.TimeoutErrorMetricsEvent(
            apiId = expectedApiId,
            labels = expectedLabelsForTimeoutCase,
          ),
        sourceId = expectedSourceId,
        sdkVersion = EXPECTED_SDK_VERSION,
        metadata = expectedMetadata,
      ),
    ),
    NETWORK_ERROR(
      BKTException.NetworkException(message = "", cause = Exception()),
      EventData.MetricsEvent(
        timestamp = EXPECTED_TIMESTAMP,
        type = MetricsEventType.NETWORK_ERROR,
        event =
          MetricsEventData.NetworkErrorMetricsEvent(
            apiId = expectedApiId,
            labels = expectedLabelsForOtherCases,
          ),
        sourceId = expectedSourceId,
        sdkVersion = EXPECTED_SDK_VERSION,
        metadata = expectedMetadata,
      ),
    ),
    UNKNOWN(
      BKTException.UnknownException(message = "UnknownException"),
      EventData.MetricsEvent(
        timestamp = EXPECTED_TIMESTAMP,
        type = MetricsEventType.UNKNOWN,
        event =
          MetricsEventData.UnknownErrorMetricsEvent(
            apiId = expectedApiId,
            labels = expectedLabelsForUnknownException,
          ),
        sourceId = expectedSourceId,
        sdkVersion = EXPECTED_SDK_VERSION,
        metadata = expectedMetadata,
      ),
    ),
    REDIRECT_REQUEST(
      BKTException.RedirectRequestException(message = "", statusCode = 302),
      EventData.MetricsEvent(
        timestamp = EXPECTED_TIMESTAMP,
        type = MetricsEventType.REDIRECT_REQUEST,
        event =
          MetricsEventData.RedirectionRequestErrorMetricsEvent(
            apiId = expectedApiId,
            labels = expectedLabelsForRedirectRequestException,
          ),
        sourceId = expectedSourceId,
        sdkVersion = EXPECTED_SDK_VERSION,
        metadata = expectedMetadata,
      ),
    ),
    PAYLOAD_TOO_LARGE(
      BKTException.PayloadTooLargeException(message = ""),
      EventData.MetricsEvent(
        timestamp = EXPECTED_TIMESTAMP,
        type = MetricsEventType.PAYLOAD_TOO_LARGE,
        event =
          MetricsEventData.PayloadTooLargeErrorMetricsEvent(
            apiId = expectedApiId,
            labels = expectedLabelsForOtherCases,
          ),
        sourceId = expectedSourceId,
        sdkVersion = EXPECTED_SDK_VERSION,
        metadata = expectedMetadata,
      ),
    ),
    UNKNOWN_SERVER(
      BKTException.UnknownServerException(message = "UnknownServerException", statusCode = 499),
      EventData.MetricsEvent(
        timestamp = EXPECTED_TIMESTAMP,
        type = MetricsEventType.UNKNOWN,
        event =
          MetricsEventData.UnknownErrorMetricsEvent(
            apiId = expectedApiId,
            labels = expectedLabelsForUnknownServerException,
          ),
        sourceId = expectedSourceId,
        sdkVersion = EXPECTED_SDK_VERSION,
        metadata = expectedMetadata,
      ),
    ),
  }

  @Test
  fun toMetricEventType(
    @TestParameter case: ErrorTestCase,
  ) {
    val result =
      newEventDataMetricEvent(
        case.code,
        timestamp = 19998L,
        featureTag = "android",
        appVersion = "1.0.0",
        apiId = ApiId.GET_EVALUATIONS,
        sourceId = SourceId.OPEN_FEATURE_KOTLIN,
        sdkVersion = "0.0.8",
      )
    Truth.assertThat(result).isEqualTo(case.expected)
  }
}
