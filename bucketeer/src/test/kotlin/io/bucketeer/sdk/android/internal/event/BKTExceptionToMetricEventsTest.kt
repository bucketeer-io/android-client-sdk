package io.bucketeer.sdk.android.internal.event

import com.google.common.truth.Truth
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.model.MetricsEventType
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class BKTExceptionToMetricEventsTest {

  @Suppress("unused")
  enum class ErrorTestCase(
    val code: BKTException,
    val expected: MetricsEventType,
  ) {
    BAD_REQUEST(BKTException.BadRequestException(message = ""), MetricsEventType.BAD_REQUEST_ERROR),
    UNAUTHORIZED(BKTException.UnauthorizedException(message = ""), MetricsEventType.UNAUTHORIZED_ERROR),
    FORBIDDEN(BKTException.ForbiddenException(message = ""), MetricsEventType.FORBIDDEN_ERROR),
    NOT_FOUND(BKTException.FeatureNotFoundException(message = ""), MetricsEventType.NOT_FOUND_ERROR),
    METHOD_NOT_ALLOWED(BKTException.InvalidHttpMethodException(message = ""), MetricsEventType.INTERNAL_SDK_ERROR),
    CLIENT_CLOSED_REQUEST(BKTException.ClientClosedRequestException(message = ""), MetricsEventType.CLIENT_CLOSED_REQUEST_ERROR),
    INTERNAL_SERVER_ERROR(BKTException.InternalServerErrorException(message = ""), MetricsEventType.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(BKTException.ServiceUnavailableException(message = ""), MetricsEventType.SERVICE_UNAVAILABLE_ERROR),
  }

  @Test
  fun toMetricEventType(@TestParameter case: ErrorTestCase) {
    val result = case.code.toMetricEventType()
    Truth.assertThat(result).isEqualTo(case.expected)
  }
}
