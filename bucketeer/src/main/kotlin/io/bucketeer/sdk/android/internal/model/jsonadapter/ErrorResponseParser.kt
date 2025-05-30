package io.bucketeer.sdk.android.internal.model.jsonadapter

import androidx.annotation.VisibleForTesting
import com.squareup.moshi.JsonAdapter
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse

internal class ErrorResponseParser(
  private val errorResponseAdapter: JsonAdapter<ErrorResponse>,
  private val errorDetailAdapter: JsonAdapter<ErrorResponse.ErrorDetail>
) {

  fun parse(json: String?): ErrorResponse {
    return parseErrorResponse(json ?: "") ?: parseErrorDetail(json ?: "") ?: createUnknownError(json)
  }

  @VisibleForTesting
  internal fun parseErrorResponse(json: String): ErrorResponse? {
    return try {
      errorResponseAdapter.fromJson(json)
    } catch (_: Exception) { // Catch all exceptions
      null
    }
  }

  @VisibleForTesting
  internal fun parseErrorDetail(json: String): ErrorResponse? {
    return try {
      val errorDetail = errorDetailAdapter.fromJson(json)
      errorDetail?.let { ErrorResponse(error = it) }
    } catch (_: Exception) { // Catch all exceptions
      null
    }
  }

  private fun createUnknownError(json: String?): ErrorResponse {
    val jsonMessage = json ?: "null"
    return ErrorResponse(
      error = ErrorResponse.ErrorDetail(
        code = 0,
        message = jsonMessage
      )
    )
  }
}

