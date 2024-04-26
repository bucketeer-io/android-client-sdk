package io.bucketeer.sdk.android.internal.evaluation.storage

import io.bucketeer.sdk.android.internal.model.Evaluation

// Why we choose to use method for get & set instead of property
// https://github.com/bucketeer-io/android-client-sdk/pull/89#discussion_r1342254163
internal interface EvaluationStorage {
  val userId: String

  fun getCurrentEvaluationId(): String

  fun clearCurrentEvaluationId()

  fun setUserAttributesUpdated()

  fun clearUserAttributesUpdated()

  fun getUserAttributesUpdated(): Boolean

  fun getEvaluatedAt(): String

  fun getFeatureTag(): String

  fun setFeatureTag(tag: String)

  fun getBy(featureId: String): Evaluation?

  fun get(): List<Evaluation>

  fun deleteAllAndInsert(
    evaluationsId: String,
    evaluations: List<Evaluation>,
    evaluatedAt: String,
  )

  fun update(
    evaluationsId: String,
    evaluations: List<Evaluation>,
    archivedFeatureIds: List<String>,
    evaluatedAt: String,
  ): Boolean

  fun refreshCache()
}
