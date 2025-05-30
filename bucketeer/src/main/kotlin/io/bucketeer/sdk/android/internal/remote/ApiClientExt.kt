package io.bucketeer.sdk.android.internal.remote

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.model.jsonadapter.ErrorResponseParser
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse
import okhttp3.Response
import java.io.EOFException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.contracts.ExperimentalContracts

internal fun Response.toErrorResponse(parser: ErrorResponseParser): ErrorResponse {
  val bodyString = body?.string() ?: ""
  return try {
    parser.parse(bodyString)
  } catch (_: Exception) {
    ErrorResponse(
      error = ErrorResponse.ErrorDetail(
        code = 0,
        message = bodyString
      )
    )
  }
}

internal fun ErrorResponse.toBKTException(code: Int): BKTException {
  return when (code) {
    in 300..399 -> {
      BKTException.RedirectRequestException(
        message = error.message,
        statusCode = code,
      )
    }
    400 -> {
      // BadRequest
      // - gateway: context canceled
      // - gateway: tag is required
      // - gateway: user is required
      // - gateway: user id is required
      // - gateway: feature id is required
      // - gateway: missing event id
      // - gateway: missing events
      // - gateway: body is required
      BKTException.BadRequestException(
        message = error.message,
      )
    }
    401 -> {
      // Unauthorized
      // - gateway: missing APIKey
      // - gateway: invalid APIKey
      // - gateway: bad role
      // - gateway: disabled APIKey
      BKTException.UnauthorizedException(
        message = error.message,
      )
    }
    403 -> {
      // Forbidden
      BKTException.ForbiddenException(
        message = error.message,
      )
    }
    404 -> {
      // NotFound
      // - feature not found
      BKTException.FeatureNotFoundException(
        message = error.message,
      )
    }
    405 -> {
      // MethodNotAllowed
      // - gateway: invalid http method
      BKTException.InvalidHttpMethodException(
        message = error.message,
      )
    }
    408 -> {
      BKTException.TimeoutException(
        message = "${error.message}. Request timeout error: 408",
        cause = null,
        timeoutMillis = 0,
      )
    }
    413 -> {
      BKTException.PayloadTooLargeException(
        message = error.message,
      )
    }
    499 -> {
      // ClientClosedRequest
      BKTException.ClientClosedRequestException(
        message = error.message,
      )
    }
    500 -> {
      // InternalServerError
      // - gateway: internal
      BKTException.InternalServerErrorException(
        message = error.message,
      )
    }
    502, 503, 504 -> {
      // ServiceUnavailable
      // - gateway: internal
      BKTException.ServiceUnavailableException(
        message = error.message,
      )
    }
    else ->
      BKTException.UnknownServerException(
        message = "Unknown error: '${error.message}'",
        statusCode = code,
      )
  }
}

internal fun Throwable.toBKTException(
  requestTimeoutMillis: Long,
  statusCode: Int = 0,
): BKTException =
  when (this) {
    is BKTException -> this
    is SocketTimeoutException,
    is InterruptedIOException,
    ->
      BKTException.TimeoutException("Request timeout error: ${this.message}", this, timeoutMillis = requestTimeoutMillis)
    is UnknownHostException ->
      BKTException.NetworkException("Network connection error: ${this.message}", this)
    is JsonDataException,
    is JsonEncodingException,
    is EOFException,
    ->
      BKTException.UnknownServerException("Unknown server error: ${this.message}", this, statusCode = statusCode)
    else -> BKTException.UnknownException("Unknown error: ${this.message}", this)
  }

@OptIn(ExperimentalContracts::class)
inline fun <T> measureTimeMillisWithResult(block: () -> T): Pair<Long, T> {
  kotlin.contracts.contract {
    callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
  }
  val start = System.currentTimeMillis()
  val result = block()
  return (System.currentTimeMillis() - start) to result
}
