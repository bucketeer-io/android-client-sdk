package io.bucketeer.sdk.android

/**
 * Whether updating flag values from the server works right now.
 *
 * All times are milliseconds since 1970-01-01 (UTC).
 *
 * @property state one of the 4 states.
 * @property changedAt when [state] last changed.
 * @property lastSyncedAt the last time the SDK confirmed its flag values are current. null if it
 * never worked yet.
 * @property lastError the last error from updating flag values. Kept after recovery. null if no
 * update failed yet. Can be null in [State.FAILING] when initialize failed before reaching the
 * server.
 */
data class BKTEvaluationSyncStatus(
  val state: State,
  val changedAt: Long,
  val lastSyncedAt: Long?,
  val lastError: BKTEvaluationSyncError?,
) {
  enum class State {
    /** The SDK is starting. Errors are shown in [lastError], but the state stays here until initialize finishes. */
    INITIALIZING,

    /** The last update from the server worked. In polling mode, values can be up to one polling interval old. */
    SYNCED,

    /** The last update failed. The SDK keeps the last known values and keeps retrying. */
    FAILING,

    /** The client was destroyed. This is the last status a listener gets. */
    DESTROYED,
  }

  enum class Source { POLLING, STREAMING }
}

/**
 * @property cause the error, using the same type that [BKTClient.fetchEvaluations] returns.
 * @property occurredAt when the error happened.
 * @property source where the error came from.
 */
data class BKTEvaluationSyncError(
  val cause: BKTException,
  val occurredAt: Long,
  val source: BKTEvaluationSyncStatus.Source,
)
