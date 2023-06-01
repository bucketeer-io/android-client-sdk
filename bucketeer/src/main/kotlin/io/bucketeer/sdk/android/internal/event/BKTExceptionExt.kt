package io.bucketeer.sdk.android.internal.event

import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.model.MetricsEventType

fun BKTException.toMetricEventType(): MetricsEventType {
  return when (this) {
    is BKTException.BadRequestException -> MetricsEventType.BAD_REQUEST_ERROR
    is BKTException.ClientClosedRequestException -> MetricsEventType.CLIENT_CLOSED_REQUEST_ERROR
    is BKTException.FeatureNotFoundException -> MetricsEventType.NOT_FOUND_ERROR
    is BKTException.ForbiddenException -> MetricsEventType.FORBIDDEN_ERROR
    is BKTException.IllegalArgumentException -> MetricsEventType.INTERNAL_ERROR
    is BKTException.IllegalStateException -> MetricsEventType.INTERNAL_ERROR
    is BKTException.InternalServerErrorException -> MetricsEventType.INTERNAL_SERVER_ERROR
    is BKTException.InvalidHttpMethodException -> MetricsEventType.INTERNAL_ERROR
    is BKTException.NetworkException -> MetricsEventType.NETWORK_ERROR
    is BKTException.ServiceUnavailableException -> MetricsEventType.SERVICE_UNAVAILABLE_ERROR
    is BKTException.TimeoutException -> MetricsEventType.TIMEOUT_ERROR
    is BKTException.UnauthorizedException -> MetricsEventType.UNAUTHORIZED_ERROR
    is BKTException.UnknownException -> MetricsEventType.UNKNOWN
  }
}
