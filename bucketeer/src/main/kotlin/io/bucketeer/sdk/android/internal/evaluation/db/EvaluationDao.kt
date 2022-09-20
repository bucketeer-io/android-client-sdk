package io.bucketeer.sdk.android.internal.evaluation.db

import io.bucketeer.sdk.android.internal.model.Evaluation

internal interface EvaluationDao {
  fun put(userId: String, list: List<Evaluation>)
  fun get(userId: String): List<Evaluation>
  fun deleteAllAndInsert(userId: String, list: List<Evaluation>): Boolean
}
