package io.bucketeer.sdk.android.internal.evaluation

import io.bucketeer.sdk.android.BKTEvaluationSyncStatus
import io.bucketeer.sdk.android.BKTException

/** What EvaluationInteractor needs: report the result of an update it already handles. */
internal interface EvaluationSyncReporter {
  fun reportSuccess(source: BKTEvaluationSyncStatus.Source)

  fun reportFailure(
    source: BKTEvaluationSyncStatus.Source,
    error: BKTException,
  )
}
