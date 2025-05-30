package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErrorResponseParserTest {

  private val moshi: Moshi = Moshi.Builder().build()
  private val errorResponseAdapter: JsonAdapter<ErrorResponse> = moshi.adapter(ErrorResponse::class.java)
  private val errorDetailAdapter: JsonAdapter<ErrorResponse.ErrorDetail> = moshi.adapter(ErrorResponse.ErrorDetail::class.java)
  private val parser = ErrorResponseParser(errorResponseAdapter, errorDetailAdapter)

  @Test
  fun `parse should return ErrorResponse when valid ErrorResponse json is provided`() {
    val json = """{"error":{"code":123,"message":"Test error"}}"""
    val result = parser.parse(json)

    assertEquals(123, result.error.code)
    assertEquals("Test error", result.error.message)
  }

  @Test
  fun `parse should return ErrorResponse when valid ErrorDetail json is provided`() {
    val json = """{"code":456,"message":"Detail error"}"""
    val result = parser.parse(json)

    assertEquals(456, result.error.code)
    assertEquals("Detail error", result.error.message)
  }

  @Test
  fun `parse should return unknown ErrorResponse when invalid json is provided`() {
    val json = """{"invalid":"data"}"""
    val result = parser.parse(json)

    assertEquals(0, result.error.code)
    assertEquals(json, result.error.message)
  }

  @Test
  fun `parse should return unknown ErrorResponse when empty string is provided`() {
    val json = """"""
    val result = parser.parse(json)

    assertEquals(0, result.error.code)
    assertEquals(json, result.error.message)
  }

  @Test
  fun `parse should return unknown ErrorResponse when null json is provided`() {
    val json: String? = null
    val result = parser.parse(json)

    assertEquals(0, result.error.code)
    assertEquals("null", result.error.message)
  }

  @Test
  fun `parse should return unknown ErrorResponse when malformed json is provided`() {
    val json = """{"error": "missing closing brace"""
    val result = parser.parse(json)

    assertEquals(0, result.error.code)
    assertEquals(json, result.error.message)
  }

  @Test
  fun `parse should ignore unexpected fields in valid ErrorResponse json`() {
    val json = """{"error":{"code":789,"message":"Unexpected fields"},"extra":"data"}"""
    val result = parser.parse(json)

    assertEquals(789, result.error.code)
    assertEquals("Unexpected fields", result.error.message)
  }

  @Test
  fun `parseErrorResponse should return null for invalid ErrorResponse json`() {
    val json = """{"invalid":"data"}"""
    val result = parser.parseErrorResponse(json)

    assertNull(result)
  }

  @Test
  fun `parseErrorDetail should return null for invalid ErrorDetail json`() {
    val json = """{"invalid":"data"}"""
    val result = parser.parseErrorDetail(json)

    assertNull(result)
  }
}
