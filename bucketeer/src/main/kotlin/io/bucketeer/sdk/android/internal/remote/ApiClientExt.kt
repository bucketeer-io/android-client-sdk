package io.bucketeer.sdk.android.internal.remote

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonEncodingException
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse
import okhttp3.Response
import java.io.EOFException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.contracts.ExperimentalContracts

fun Response.toBKTException(adapter: JsonAdapter<ErrorResponse>): BKTException {
  val bodyString = body?.string() ?: ""
  val errorBody: ErrorResponse? = kotlin.runCatching {
    adapter.fromJson(bodyString)
  }.getOrNull()

  return when (code) {
    in 300..399 -> {
      BKTException.RedirectRequestException(
        message = errorBody?.error?.message ?: "RedirectRequest error",
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
        message = errorBody?.error?.message ?: "BadRequest error",
      )
    }
    401 -> {
      // Unauthorized
      // - gateway: missing APIKey
      // - gateway: invalid APIKey
      // - gateway: bad role
      // - gateway: disabled APIKey
      BKTException.UnauthorizedException(
        message = errorBody?.error?.message ?: "Unauthorized error",
      )
    }
    403 -> {
      // Forbidden
      BKTException.ForbiddenException(
        message = errorBody?.error?.message ?: "Forbidden error",
      )
    }
    404 -> {
      // NotFound
      // - feature not found
      BKTException.FeatureNotFoundException(
        message = errorBody?.error?.message ?: "NotFound error",
      )
    }
    405 -> {
      // MethodNotAllowed
      // - gateway: invalid http method
      BKTException.InvalidHttpMethodException(
        message = errorBody?.error?.message ?: "MethodNotAllowed error",
      )
    }
    408 -> {
      BKTException.TimeoutException(
        message = errorBody?.error?.message ?: "Request timeout error: 408",
        cause = null,
        timeoutMillis = 0,
      )
    }
    413 -> {
      BKTException.PayloadTooLargeException(
        message = errorBody?.error?.message ?: "PayloadTooLarge error",
      )
    }
    499 -> {
      // ClientClosedRequest
      BKTException.ClientClosedRequestException(
        message = errorBody?.error?.message ?: "ClientClosedRequest error",
      )
    }
    500 -> {
      // InternalServerError
      // - gateway: internal
      BKTException.InternalServerErrorException(
        message = errorBody?.error?.message ?: "InternalServer error",
      )
    }
    502, 503, 504 -> {
      // ServiceUnavailable
      // - gateway: internal
      BKTException.ServiceUnavailableException(
        message = errorBody?.error?.message ?: "ServiceUnavailable error",
      )
    }
    else -> BKTException.UnknownServerException(
      message = "Unknown error: '$errorBody'",
      statusCode = code,
    )
  }
}

internal fun Throwable.toBKTException(requestTimeoutMillis: Long, statusCode: Int = 0): BKTException {
  return when (this) {
    is BKTException -> this
    is SocketTimeoutException,
    is InterruptedIOException,
    ->
      BKTException.TimeoutException("Request timeout error: ${this.message}", this, timeoutMillis = requestTimeoutMillis)
    is UnknownHostException ->
      BKTException.NetworkException("Network connection error: ${this.message}", this)
    is JsonEncodingException,
    is EOFException ->
      BKTException.UnknownServerException("Unknown server error: ${this.message}", this, statusCode = statusCode)
    else -> BKTException.UnknownException("Unknown error: ${this.message}", this)
  }
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
