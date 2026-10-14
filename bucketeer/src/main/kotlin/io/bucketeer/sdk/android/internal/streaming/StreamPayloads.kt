package io.bucketeer.sdk.android.internal.streaming

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.model.response.StreamErrorEvent
import java.io.IOException

/**
 * Reads a put/patch payload. Same wire shape as the polling response, so it decodes as
 * GetEvaluationsResponse.
 *
 * Strict about the ids, `createdAt` and `forceUpdate`: a payload missing one of them returns null.
 * The two lists (`evaluations` and `archivedFeatureIds`) are not checked: when missing they become
 * empty, because the models shared with polling give them defaults. The iOS SDK rejects such a
 * payload instead. The difference can't show up in practice:
 * - the backend writes every field, including empty lists (protojson with EmitUnpopulated);
 * - SSEParser only emits an event once its closing blank line arrives, so a stream cut off in the
 *   middle of an event emits nothing;
 * - a cut-off JSON body is not valid JSON, so it returns null here.
 * A valid payload without the lists would mean the backend changed what it sends.
 *
 * Returns null for anything that is not a valid payload: bad JSON, wrong shape, a missing field above,
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
