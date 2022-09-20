package io.bucketeer.sdk.android.internal.remote

import com.squareup.moshi.JsonAdapter
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse
import okhttp3.Response
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
    500 -> {
      // InternalServerError
      // - gateway: internal
      BKTException.ApiServerException(
        message = errorBody?.error?.message ?: "InternalServer error",
      )
    }
    else -> BKTException.UnknownException("Unknown error: '$errorBody'")
  }
}

internal fun Throwable.toBKTException(): BKTException {
  return when (this) {
    is BKTException -> this
    is SocketTimeoutException,
    is InterruptedIOException,
    ->
      BKTException.TimeoutException("Request timeout error: ${this.message}", this)
    is UnknownHostException ->
      BKTException.NetworkException("Network connection error: ${this.message}", this)
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
