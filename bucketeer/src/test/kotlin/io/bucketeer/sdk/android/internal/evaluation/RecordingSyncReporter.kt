package io.bucketeer.sdk.android.internal.evaluation

import io.bucketeer.sdk.android.BKTEvaluationSyncStatus
import io.bucketeer.sdk.android.BKTException

/** Records each report, in order. */
internal class RecordingSyncReporter : EvaluationSyncReporter {
  sealed class Report {
    data class Success(
      val source: BKTEvaluationSyncStatus.Source,
    ) : Report()

    // Compares the error by identity: the report must carry the same object fetch() returns.
    class Failure(
      val source: BKTEvaluationSyncStatus.Source,
      val error: BKTException,
    ) : Report()
  }

  val reports = mutableListOf<Report>()

  override fun reportSuccess(source: BKTEvaluationSyncStatus.Source) {
    reports.add(Report.Success(source))
  }

  override fun reportFailure(
    source: BKTEvaluationSyncStatus.Source,
    error: BKTException,
  ) {
    reports.add(Report.Failure(source, error))
  }
}
