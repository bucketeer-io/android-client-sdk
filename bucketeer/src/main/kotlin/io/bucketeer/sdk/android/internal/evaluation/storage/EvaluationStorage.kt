package io.bucketeer.sdk.android.internal.evaluation.storage

import io.bucketeer.sdk.android.internal.model.Evaluation

internal interface EvaluationStorage {
  val userId: String
  var currentEvaluationsId: String
  var featureTag: String
  var userAttributesUpdated: Boolean

  // expected set evaluatedAt from `deleteAllAndInsert` only
  val evaluatedAt: String

  fun getBy(featureId: String): Evaluation?
  fun get(): List<Evaluation>
  fun deleteAllAndInsert(evaluations: List<Evaluation>, evaluatedAt: String)
  fun update(evaluations: List<Evaluation>, archivedFeatureIds: List<String>, evaluatedAt: String): Boolean
  fun refreshCache()
}
