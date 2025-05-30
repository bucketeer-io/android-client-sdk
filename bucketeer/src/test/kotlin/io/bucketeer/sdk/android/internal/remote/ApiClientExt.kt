package io.bucketeer.sdk.android.internal.remote

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.internal.model.jsonadapter.ErrorResponseParser
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class ApiClientExtTest {

  private val moshi: Moshi = Moshi.Builder().build()
  private val errorResponseAdapter: JsonAdapter<ErrorResponse> =
    moshi.adapter(ErrorResponse::class.java)
  private val errorDetailAdapter: JsonAdapter<ErrorResponse.ErrorDetail> =
    moshi.adapter(ErrorResponse.ErrorDetail::class.java)
  private val parser = ErrorResponseParser(errorResponseAdapter, errorDetailAdapter)

  @Test
  fun `toErrorResponse - valid error response`() {
    val errorJson = """
      {
        "error": {
          "code": 400,
          "message": "Bad Request"
        }
      }
    """.trimIndent()

    val response = createMockResponse(400, errorJson)
    val errorResponse = response.toErrorResponse(parser)

    assertThat(errorResponse.error.code).isEqualTo(400)
    assertThat(errorResponse.error.message).isEqualTo("Bad Request")
  }

  @Test
  fun `toErrorResponse - invalid error response`() {
    val invalidJson = "invalid json"
    val response = createMockResponse(400, invalidJson)
    val errorResponse = response.toErrorResponse(parser)

    assertThat(errorResponse.error.code).isEqualTo(0)
    assertThat(errorResponse.error.message).contains("invalid json")
  }

  @Test
  fun `toErrorResponse - empty body`() {
    val response = createMockResponse(400, null)
    val errorResponse = response.toErrorResponse(parser)

    assertThat(errorResponse.error.code).isEqualTo(0)
    assertThat(errorResponse.error.message).contains("")
  }

  private fun createMockResponse(code: Int, body: String?): Response {
    return Response.Builder()
      .code(code)
      .message("Mock Response")
      .protocol(okhttp3.Protocol.HTTP_1_1)
      .request(okhttp3.Request.Builder().url("http://localhost").build())
      .body(body?.toResponseBody("application/json".toMediaTypeOrNull()))
      .build()
  }
}
