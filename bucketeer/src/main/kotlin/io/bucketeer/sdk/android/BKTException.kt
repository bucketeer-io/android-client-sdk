package io.bucketeer.sdk.android

sealed class BKTException(
  message: String,
  cause: Throwable? = null,
) : Exception(
  message,
  cause,
) {
  // server errors
  class BadRequestException(message: String) : BKTException(message)
  class UnauthorizedException(message: String) : BKTException(message)
  class FeatureNotFoundException(message: String) : BKTException(message)
  class InvalidHttpMethodException(message: String) : BKTException(message)
  class ApiServerException(message: String) : BKTException(message)

  // network errors
  class TimeoutException(message: String, cause: Throwable) : BKTException(message, cause)
  class NetworkException(message: String, cause: Throwable) : BKTException(message, cause)

  // sdk errors
  class IllegalArgumentException(message: String) : BKTException(message)
  class IllegalStateException(message: String) : BKTException(message)

  // unknown errors
  class UnknownException(message: String, cause: Throwable? = null) : BKTException(message, cause)
}
