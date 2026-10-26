package io.bucketeer.sdk.android.internal.evaluation.storage

import io.bucketeer.sdk.android.internal.model.Evaluation

// Why we choose to use method for get & set instead of property
// https://github.com/bucketeer-io/android-client-sdk/pull/89#discussion_r1342254163
internal interface EvaluationStorage {
  val userId: String

  fun getCurrentEvaluationId(): String

  fun clearCurrentEvaluationId()

  fun setUserAttributesUpdated()

  fun clearUserAttributesUpdated(state: UserAttributesState)

  fun getUserAttributesState(): UserAttributesState

  fun getEvaluatedAt(): String

  fun getFeatureTag(): String

  fun setFeatureTag(tag: String)

  fun getBy(featureId: String): Evaluation?

  fun get(): List<Evaluation>

  fun deleteAllAndInsert(
    evaluationsId: String,
    evaluations: List<Evaluation>,
    evaluatedAt: String,
  ): EvaluationWriteResult

  fun update(
    evaluationsId: String,
    evaluations: List<Evaluation>,
    archivedFeatureIds: List<String>,
    evaluatedAt: String,
  ): EvaluationWriteResult

  fun refreshCache()
}

/**
 * What a storage write did. Callers must not notify listeners, or clear the
 * user-attributes-updated flag, on [SkippedStale].
 */
internal sealed class EvaluationWriteResult {
  /** Nothing changed: the incoming evaluatedAt is older than the stored one. */
  data object SkippedStale : EvaluationWriteResult()

  /** Written. [shouldNotify] means "worth telling listeners", not "stored values changed". */
  data class Landed(
    val shouldNotify: Boolean,
  ) : EvaluationWriteResult()
}
