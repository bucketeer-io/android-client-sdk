package io.bucketeer.sdk.android.internal.evaluation

import androidx.annotation.VisibleForTesting
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.BKTEvaluationSyncError
import io.bucketeer.sdk.android.BKTEvaluationSyncStatus
import io.bucketeer.sdk.android.BKTEvaluationSyncStatus.State
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.Clock
import io.bucketeer.sdk.android.internal.IdGenerator
import io.bucketeer.sdk.android.internal.loge

/** What BKTClient needs: the public API and the lifecycle. It cannot report results. */
internal interface EvaluationSyncStatusTracker {
  fun getStatus(): BKTEvaluationSyncStatus

  fun addListener(listener: BKTClient.EvaluationSyncStatusListener): String

  fun removeListener(key: String)

  fun clearListeners()

  /** Does nothing unless the state is still INITIALIZING. */
  fun finishInitializing(succeeded: Boolean)

  fun markDestroyed()
}

/**
 * Holds the one sync status of the client and calls its listeners on the main thread.
 *
 * - INITIALIZING lasts until initialize finishes. Errors during init update lastError only. A
 *   success sets SYNCED at once.
 * - After init, a success sets SYNCED and a failure sets FAILING.
 * - Listeners are called on every state change and on every failure. A change to lastSyncedAt
 *   alone calls no listener.
 * - After DESTROYED, every report is ignored.
 *
 * Threads: results are reported on the SDK executor, destroy runs on the app's thread, and the
 * status is read from any thread. All state is guarded by [lock]. Each change posts one task to
 * the main thread while still holding the lock, so tasks run in the order the changes happened.
 * No lock is held while app code runs.
 *
 * Only ComponentImpl sees this class. Each caller gets only the interface it uses.
 */
internal class EvaluationSyncStatusTrackerImpl(
  private val clock: Clock,
  private val idGenerator: IdGenerator,
  private val postToMain: (Runnable) -> Unit,
) : EvaluationSyncStatusTracker,
  EvaluationSyncReporter {
  private val lock = Any()

  // Guarded by lock.
  private var status =
    BKTEvaluationSyncStatus(
      state = State.INITIALIZING,
      changedAt = clock.currentTimeMillis(),
      lastSyncedAt = null,
      lastError = null,
    )

  // Guarded by lock.
  @VisibleForTesting
  internal val listeners = LinkedHashMap<String, BKTClient.EvaluationSyncStatusListener>()

  // The status is an immutable data class, so it can be handed out as is.
  override fun getStatus(): BKTEvaluationSyncStatus = synchronized(lock) { status }

  override fun addListener(listener: BKTClient.EvaluationSyncStatusListener): String {
    val key = idGenerator.newId()
    synchronized(lock) { listeners[key] = listener }
    return key
  }

  override fun removeListener(key: String) {
    synchronized(lock) { listeners.remove(key) }
  }

  override fun clearListeners() {
    synchronized(lock) { listeners.clear() }
  }

  override fun finishInitializing(succeeded: Boolean) {
    synchronized(lock) {
      if (succeeded || status.state != State.INITIALIZING) return
      setState(State.FAILING)
      postNotification()
    }
  }

  override fun markDestroyed() {
    synchronized(lock) {
      if (isDestroyed()) return
      setState(State.DESTROYED)
      // The listeners are removed by the posted task, after they get DESTROYED.
      postNotification()
    }
  }

  override fun reportSuccess(source: BKTEvaluationSyncStatus.Source) {
    synchronized(lock) {
      if (isDestroyed()) return
      // Already SYNCED: only lastSyncedAt moves, with no listener call.
      val changed = status.state != State.SYNCED
      if (changed) setState(State.SYNCED)
      status = status.copy(lastSyncedAt = clock.currentTimeMillis())
      if (changed) postNotification()
    }
  }

  override fun reportFailure(
    source: BKTEvaluationSyncStatus.Source,
    error: BKTException,
  ) {
    synchronized(lock) {
      if (isDestroyed()) return
      // INITIALIZING keeps its state: finishInitializing() decides it. FAILING stays FAILING, but
      // listeners still get the new error.
      if (status.state == State.SYNCED) setState(State.FAILING)
      status =
        status.copy(
          lastError =
            BKTEvaluationSyncError(
              cause = error,
              occurredAt = clock.currentTimeMillis(),
              source = source,
            ),
        )
      postNotification()
    }
  }

  // Call with lock held.
  private fun isDestroyed(): Boolean = status.state == State.DESTROYED

  // Call with lock held.
  private fun setState(state: State) {
    status = status.copy(state = state, changedAt = clock.currentTimeMillis())
  }

  /**
   * Call with lock held. Takes the status and the listeners as they are now, so a listener added
   * later is not called with an older status.
   */
  private fun postNotification() {
    val snapshot = status
    val targets = listeners.toList()
    postToMain(Runnable { deliver(snapshot, targets) })
  }

  // Main thread.
  private fun deliver(
    snapshot: BKTEvaluationSyncStatus,
    targets: List<Pair<String, BKTClient.EvaluationSyncStatusListener>>,
  ) {
    for ((key, listener) in targets) {
      // A listener can remove another listener, or destroy the client. A removed listener is not
      // called, and after DESTROYED only the DESTROYED task calls listeners.
      val shouldCall =
        synchronized(lock) {
          listeners[key] === listener && (snapshot.state == State.DESTROYED || !isDestroyed())
        }
      if (!shouldCall) continue
      try {
        listener.onStatusChanged(snapshot)
      } catch (e: Throwable) {
        // App code must not break polling, the stream, or the other listeners. Throwable, not only
        // Exception: a listener left as TODO() throws an Error.
        loge(e) { "EvaluationSyncStatusListener threw: ${e.message}" }
      }
    }
    if (snapshot.state == State.DESTROYED) {
      synchronized(lock) { listeners.clear() }
    }
  }
}
