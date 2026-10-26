package io.bucketeer.sdk.android.internal.evaluation

import io.bucketeer.sdk.android.internal.evaluation.storage.EvaluationStorage
import io.bucketeer.sdk.android.internal.evaluation.storage.EvaluationWriteResult
import io.bucketeer.sdk.android.internal.model.Evaluation

/**
 * Real storage whose two writes throw. Everything else, including the user attributes flag,
 * goes to the real storage, so a test can check that a failed write left the flag set.
 */
internal class ThrowingWritesEvaluationStorage(
  private val real: EvaluationStorage,
  val error: Exception = IllegalStateException("storage write failed"),
) : EvaluationStorage by real {
  override fun deleteAllAndInsert(
    evaluationsId: String,
    evaluations: List<Evaluation>,
    evaluatedAt: String,
  ): EvaluationWriteResult = throw error

  override fun update(
    evaluationsId: String,
    evaluations: List<Evaluation>,
    archivedFeatureIds: List<String>,
    evaluatedAt: String,
  ): EvaluationWriteResult = throw error
}
