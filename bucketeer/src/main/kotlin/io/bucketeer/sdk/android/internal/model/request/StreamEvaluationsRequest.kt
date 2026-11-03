package io.bucketeer.sdk.android.internal.model.request

import com.squareup.moshi.JsonClass
import io.bucketeer.sdk.android.internal.model.SourceId
import io.bucketeer.sdk.android.internal.model.User

/**
 * Body sent to open the evaluations stream.
 *
 * Unlike GetEvaluationsRequest there is no userEvaluationCondition: when user attributes
 * change, the stream reconnects with the new user data instead of sending a flag.
 */
@JsonClass(generateAdapter = true)
internal data class StreamEvaluationsRequest(
  val tag: String,
  val user: User,
  val sourceId: SourceId,
  val sdkVersion: String,
  /** Last known id, "" before anything is cached. Lets the server send a patch instead of a full put. */
  val userEvaluationsId: String,
  /** Last known evaluatedAt, "0" before anything is cached. A string, as stored and as protojson accepts int64. */
  val evaluatedAt: String,
)
