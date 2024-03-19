package io.bucketeer.sdk.android.internal.evaluation.db

import io.bucketeer.sdk.android.internal.Closeable
import io.bucketeer.sdk.android.internal.model.Evaluation

// EvaluationSQLDao interface with each method for a single purpose.
// If you want compose multiple query or need using transaction
// Please use the `startTransaction` method
internal interface EvaluationSQLDao : Closeable {
  fun put(userId: String, list: List<Evaluation>)
  fun get(userId: String): List<Evaluation>
  fun deleteAll(userId: String)
  fun startTransaction(block: () -> Unit)
}
