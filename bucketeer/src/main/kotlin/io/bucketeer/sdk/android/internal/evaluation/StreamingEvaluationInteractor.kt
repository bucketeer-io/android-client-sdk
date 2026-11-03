package io.bucketeer.sdk.android.internal.evaluation

import io.bucketeer.sdk.android.internal.evaluation.storage.UserAttributesState
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse

/**
 * What StreamingTask needs from EvaluationInteractor. An interface so StreamingTask tests run on
 * the plain JVM with a fake, without SQLite or a main looper.
 */
internal interface StreamingEvaluationInteractor {
  val cachedEvaluationsState: CachedEvaluationsState
  val userAttributesState: UserAttributesState

  fun clearUserAttributesUpdated(state: UserAttributesState)

  fun applyStreamedEvaluations(
    response: GetEvaluationsResponse,
    shouldNotify: () -> Boolean = { true },
  )
}
