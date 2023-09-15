package io.bucketeer.sdk.android.internal.evaluation.storage

import io.bucketeer.sdk.android.internal.model.Evaluation

// EvaluationSQLDao interface with each method is a single purpose.
// If you want compose multiple query or need using transaction
// Please use the `startTransaction` method
internal interface EvaluationSQLDao {
  fun put(userId: String, list: List<Evaluation>)
  fun get(userId: String): List<Evaluation>
  fun deleteBy(userId: String, featureIds: List<String>)
  fun deleteAll(userId: String)
  fun startTransaction(block: () -> Unit)
}
