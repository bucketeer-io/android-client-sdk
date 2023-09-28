package io.bucketeer.sdk.android.internal.evaluation.storage

import io.bucketeer.sdk.android.internal.model.Evaluation

internal interface EvaluationStorage {
  var currentEvaluationsId: String
  var featureTag: String
  var userAttributesUpdated: Boolean

  // expected set evaluatedAt from `deleteAllAndInsert` or `update` only
  val evaluatedAt: String

  fun getBy(userId: String, featureId: String): Evaluation?
  fun get(userId: String): List<Evaluation>
  fun deleteAllAndInsert(userId: String, evaluations: List<Evaluation>, evaluatedAt: String)
  fun refreshCache()
}
