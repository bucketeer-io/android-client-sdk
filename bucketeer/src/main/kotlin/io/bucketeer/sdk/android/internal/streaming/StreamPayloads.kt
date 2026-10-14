package io.bucketeer.sdk.android.internal.streaming

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.model.response.StreamErrorEvent
import java.io.IOException

/**
 * Reads a put/patch payload. Same wire shape as the polling response, so it decodes as
 * GetEvaluationsResponse. Strict on purpose: the backend sends every field, even empty ones, so
 * a missing field means a malformed payload.
 *
 * Returns null for anything that is not a valid payload: bad JSON, wrong shape, missing fields,
 * or the literal `null` (valid JSON, which Moshi returns as null without throwing). The caller
 * logs and drops it.
 */
internal fun parseStreamedEvaluations(
  moshi: Moshi,
  data: String,
): GetEvaluationsResponse? = decodeOrNull { moshi.adapter(GetEvaluationsResponse::class.java).fromJson(data) }

/** Reads an error payload for logging. Returns null when it does not decode. */
internal fun parseStreamError(
  moshi: Moshi,
  data: String,
): StreamErrorEvent? = decodeOrNull { moshi.adapter(StreamErrorEvent::class.java).fromJson(data) }

private inline fun <T> decodeOrNull(decode: () -> T?): T? =
  try {
    decode()
  } catch (e: JsonDataException) {
    null
  } catch (e: JsonEncodingException) {
    null
  } catch (e: IOException) {
    null
  }
