package io.bucketeer.sdk.android.internal.evaluation

/** The cached evaluations' id and evaluatedAt, as sent with a request. "" and "0" when empty. */
internal data class CachedEvaluationsState(
  val userEvaluationsId: String,
  val evaluatedAt: String,
)
