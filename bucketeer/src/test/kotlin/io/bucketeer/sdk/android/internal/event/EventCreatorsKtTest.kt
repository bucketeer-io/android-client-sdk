package io.bucketeer.sdk.android.internal.event

import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.ClockImpl
import io.bucketeer.sdk.android.internal.IdGeneratorImpl
import io.bucketeer.sdk.android.internal.model.ApiId
import io.bucketeer.sdk.android.internal.model.SourceID
import org.junit.Test

class EventCreatorsKtTest {
  @Test
  fun testLabelTimeoutValueShouldInDoubleFormat() {
    assert(15000L.toStringInDoubleFormat() == "15.0")
    assert(1512334557L.toStringInDoubleFormat() == "1512334.557")
    assert(500L.toStringInDoubleFormat() == "0.5")
    assert(432L.toStringInDoubleFormat() == "0.432")
    assert(51L.toStringInDoubleFormat() == "0.051")
  }

  @Test
  fun testShouldReturnNullWhenUnauthorizedOrForbidden() {
    val clock = ClockImpl()
    val idGenerator = IdGeneratorImpl()
    val featureTag = "testFeatureTag"
    val appVersion = "1.0.0"
    val apiId = ApiId.GET_EVALUATIONS
    val sourceId = SourceID.OPEN_FEATURE_KOTLIN
    val sdkVersion = "0.0.9"

    val unauthorizedException = BKTException.UnauthorizedException("401 error")
    val forbiddenException = BKTException.ForbiddenException("403 error")
    val badRequestException = BKTException.BadRequestException("Bad request error")
    val clientClosedRequestException =
      BKTException.ClientClosedRequestException("Client closed request error")
    val internalServerErrorException =
      BKTException.InternalServerErrorException("Internal server error")
    val notFoundException = BKTException.FeatureNotFoundException("404 error")

    assert(
      newErrorMetricsEvent(
        clock,
        idGenerator,
        featureTag,
        appVersion,
        unauthorizedException,
        apiId,
        sourceId,
        sdkVersion,
      ) == null,
    )
    assert(
      newErrorMetricsEvent(
        clock,
        idGenerator,
        featureTag,
        appVersion,
        forbiddenException,
        apiId,
        sourceId,
        sdkVersion,
      ) == null,
    )

    assert(
      newErrorMetricsEvent(
        clock,
        idGenerator,
        featureTag,
        appVersion,
        badRequestException,
        apiId,
        sourceId,
        sdkVersion,
      ) != null,
    )
    assert(
      newErrorMetricsEvent(
        clock,
        idGenerator,
        featureTag,
        appVersion,
        clientClosedRequestException,
        apiId,
        sourceId,
        sdkVersion,
      ) != null,
    )
    assert(
      newErrorMetricsEvent(
        clock,
        idGenerator,
        featureTag,
        appVersion,
        internalServerErrorException,
        apiId,
        sourceId,
        sdkVersion,
      ) != null,
    )
    assert(
      newErrorMetricsEvent(
        clock,
        idGenerator,
        featureTag,
        appVersion,
        notFoundException,
        apiId,
        sourceId,
        sdkVersion,
      ) != null,
    )
  }
}
