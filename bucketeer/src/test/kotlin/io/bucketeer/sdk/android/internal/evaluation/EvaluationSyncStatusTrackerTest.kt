package io.bucketeer.sdk.android.internal.evaluation

import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.BKTEvaluationSyncError
import io.bucketeer.sdk.android.BKTEvaluationSyncStatus
import io.bucketeer.sdk.android.BKTEvaluationSyncStatus.Source.POLLING
import io.bucketeer.sdk.android.BKTEvaluationSyncStatus.State.DESTROYED
import io.bucketeer.sdk.android.BKTEvaluationSyncStatus.State.FAILING
import io.bucketeer.sdk.android.BKTEvaluationSyncStatus.State.INITIALIZING
import io.bucketeer.sdk.android.BKTEvaluationSyncStatus.State.SYNCED
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.Clock
import io.bucketeer.sdk.android.internal.IdGenerator
import org.junit.Before
import org.junit.Test

/**
 * Rules under test:
 * - The status starts as INITIALIZING. During init, a failure updates lastError only; a success
 *   sets SYNCED at once. An init that ends with an error while still INITIALIZING sets FAILING.
 * - After init, a success sets SYNCED and a failure sets FAILING with a new lastError.
 * - Listeners are called on every state change and on every failure. A change to lastSyncedAt
 *   alone calls no listener.
 * - Destroy sets DESTROYED, calls listeners once, then removes them. Later reports do nothing.
 *
 * Listener calls go through a recorded queue instead of the main thread: a test runs them with
 * [MainQueue.drain], so the order of queued calls is visible without threads or timing.
 */
class EvaluationSyncStatusTrackerTest {
  private lateinit var clock: ManualClock
  private lateinit var mainQueue: MainQueue
  private lateinit var tracker: EvaluationSyncStatusTrackerImpl

  private val error503 = BKTException.ServiceUnavailableException("503")
  private val error500 = BKTException.InternalServerErrorException("500")

  @Before
  fun setup() {
    clock = ManualClock(1_000L)
    mainQueue = MainQueue()
    tracker =
      EvaluationSyncStatusTrackerImpl(
        clock = clock,
        idGenerator = SequenceIdGenerator(),
        postToMain = mainQueue::post,
      )
  }

  @Test
  fun `starts as INITIALIZING with no sync time and no error`() {
    assertThat(tracker.getStatus()).isEqualTo(
      BKTEvaluationSyncStatus(
        state = INITIALIZING,
        changedAt = 1_000L,
        lastSyncedAt = null,
        lastError = null,
      ),
    )
  }

  @Test
  fun `a failure during init updates lastError and calls listeners, but the state stays INITIALIZING`() {
    val listener = RecordingListener().also { tracker.addListener(it) }

    clock.now = 2_000L
    tracker.reportFailure(POLLING, error503)
    mainQueue.drain()

    val expected =
      BKTEvaluationSyncStatus(
        state = INITIALIZING,
        changedAt = 1_000L,
        lastSyncedAt = null,
        lastError = BKTEvaluationSyncError(error503, occurredAt = 2_000L, source = POLLING),
      )
    assertThat(tracker.getStatus()).isEqualTo(expected)
    assertThat(listener.calls).containsExactly(expected)
  }

  @Test
  fun `a success during init sets SYNCED at once and sets lastSyncedAt`() {
    val listener = RecordingListener().also { tracker.addListener(it) }

    clock.now = 2_000L
    tracker.reportSuccess(POLLING)
    mainQueue.drain()

    val expected =
      BKTEvaluationSyncStatus(state = SYNCED, changedAt = 2_000L, lastSyncedAt = 2_000L, lastError = null)
    assertThat(tracker.getStatus()).isEqualTo(expected)
    assertThat(listener.calls).containsExactly(expected)
  }

  @Test
  fun `a failed init sets FAILING while the state is INITIALIZING`() {
    tracker.reportFailure(POLLING, error503)
    val listener = RecordingListener().also { tracker.addListener(it) }

    clock.now = 3_000L
    tracker.finishInitializing(succeeded = false)
    mainQueue.drain()

    val status = tracker.getStatus()
    assertThat(status.state).isEqualTo(FAILING)
    assertThat(status.changedAt).isEqualTo(3_000L)
    assertThat(status.lastError?.cause).isSameInstanceAs(error503)
    assertThat(listener.calls).containsExactly(status)
  }

  @Test
  fun `a failed init changes nothing when a success already set SYNCED`() {
    tracker.reportSuccess(POLLING)
    mainQueue.drain()
    val before = tracker.getStatus()
    val listener = RecordingListener().also { tracker.addListener(it) }

    clock.now = 3_000L
    tracker.finishInitializing(succeeded = false)
    mainQueue.drain()

    assertThat(tracker.getStatus()).isEqualTo(before)
    assertThat(listener.calls).isEmpty()
  }

  @Test
  fun `a successful init changes nothing when a success already set SYNCED`() {
    tracker.reportSuccess(POLLING)
    mainQueue.drain()
    val before = tracker.getStatus()
    val listener = RecordingListener().also { tracker.addListener(it) }

    clock.now = 3_000L
    tracker.finishInitializing(succeeded = true)
    mainQueue.drain()

    assertThat(tracker.getStatus()).isEqualTo(before)
    assertThat(listener.calls).isEmpty()
  }

  @Test
  fun `SYNCED to FAILING sets the state, changedAt and lastError, and calls the listener`() {
    clock.now = 2_000L
    tracker.reportSuccess(POLLING)
    tracker.finishInitializing(succeeded = true)
    mainQueue.drain()
    val listener = RecordingListener().also { tracker.addListener(it) }

    clock.now = 5_000L
    tracker.reportFailure(POLLING, error500)
    mainQueue.drain()

    val expected =
      BKTEvaluationSyncStatus(
        state = FAILING,
        changedAt = 5_000L,
        lastSyncedAt = 2_000L,
        lastError = BKTEvaluationSyncError(error500, occurredAt = 5_000L, source = POLLING),
      )
    assertThat(tracker.getStatus()).isEqualTo(expected)
    assertThat(listener.calls).containsExactly(expected)
  }

  @Test
  fun `FAILING to FAILING calls the listener with the new error and keeps changedAt`() {
    moveToFailing(at = 5_000L, error = error500)
    val listener = RecordingListener().also { tracker.addListener(it) }

    clock.now = 6_000L
    tracker.reportFailure(POLLING, error503)
    mainQueue.drain()

    val status = tracker.getStatus()
    assertThat(status.state).isEqualTo(FAILING)
    assertThat(status.changedAt).isEqualTo(5_000L)
    assertThat(status.lastError).isEqualTo(BKTEvaluationSyncError(error503, occurredAt = 6_000L, source = POLLING))
    assertThat(listener.calls).containsExactly(status)
  }

  @Test
  fun `FAILING to SYNCED calls the listener, keeps lastError and updates lastSyncedAt`() {
    moveToFailing(at = 5_000L, error = error500)
    val listener = RecordingListener().also { tracker.addListener(it) }

    clock.now = 7_000L
    tracker.reportSuccess(POLLING)
    mainQueue.drain()

    val expected =
      BKTEvaluationSyncStatus(
        state = SYNCED,
        changedAt = 7_000L,
        lastSyncedAt = 7_000L,
        lastError = BKTEvaluationSyncError(error500, occurredAt = 5_000L, source = POLLING),
      )
    assertThat(tracker.getStatus()).isEqualTo(expected)
    assertThat(listener.calls).containsExactly(expected)
  }

  @Test
  fun `SYNCED to SYNCED updates lastSyncedAt only and does not call the listener`() {
    clock.now = 2_000L
    tracker.reportSuccess(POLLING)
    mainQueue.drain()
    val listener = RecordingListener().also { tracker.addListener(it) }

    clock.now = 8_000L
    tracker.reportSuccess(POLLING)
    mainQueue.drain()

    assertThat(tracker.getStatus()).isEqualTo(
      BKTEvaluationSyncStatus(state = SYNCED, changedAt = 2_000L, lastSyncedAt = 8_000L, lastError = null),
    )
    assertThat(listener.calls).isEmpty()
  }

  @Test
  fun `destroy sets DESTROYED, calls the listener once, removes listeners, and later reports do nothing`() {
    tracker.reportSuccess(POLLING)
    mainQueue.drain()
    val listener = RecordingListener().also { tracker.addListener(it) }

    clock.now = 9_000L
    tracker.markDestroyed()
    mainQueue.drain()

    val destroyed = tracker.getStatus()
    assertThat(destroyed.state).isEqualTo(DESTROYED)
    assertThat(destroyed.changedAt).isEqualTo(9_000L)
    assertThat(listener.calls).containsExactly(destroyed)
    // Removed after they got DESTROYED, so the app's listeners are not kept alive.
    assertThat(tracker.listeners).isEmpty()

    clock.now = 10_000L
    tracker.reportFailure(POLLING, error500)
    tracker.reportSuccess(POLLING)
    tracker.finishInitializing(succeeded = false)
    tracker.markDestroyed()
    mainQueue.drain()

    assertThat(tracker.getStatus()).isEqualTo(destroyed)
    assertThat(listener.calls).containsExactly(destroyed)
  }

  @Test
  fun `a removed listener is not called`() {
    val removed = RecordingListener()
    val kept = RecordingListener()
    val key = tracker.addListener(removed)
    tracker.addListener(kept)

    tracker.removeListener(key)
    tracker.reportSuccess(POLLING)
    mainQueue.drain()

    assertThat(removed.calls).isEmpty()
    assertThat(kept.calls).hasSize(1)
  }

  @Test
  fun `clearListeners removes every listener`() {
    val first = RecordingListener().also { tracker.addListener(it) }
    val second = RecordingListener().also { tracker.addListener(it) }

    tracker.clearListeners()
    tracker.reportSuccess(POLLING)
    mainQueue.drain()

    assertThat(first.calls).isEmpty()
    assertThat(second.calls).isEmpty()
  }

  @Test
  fun `when a listener destroys the client, the other listeners get DESTROYED once and nothing after it`() {
    tracker.reportSuccess(POLLING)
    tracker.finishInitializing(succeeded = true)
    mainQueue.drain()
    val destroyer =
      RecordingListener { status ->
        if (status.state == FAILING) tracker.markDestroyed()
      }
    val other = RecordingListener()
    tracker.addListener(destroyer)
    tracker.addListener(other)

    tracker.reportFailure(POLLING, error500)
    mainQueue.drain()

    assertThat(destroyer.calls.map { it.state }).containsExactly(FAILING, DESTROYED).inOrder()
    assertThat(other.calls.map { it.state }).containsExactly(DESTROYED)
  }

  @Test
  fun `a listener removed by another listener during a call is not called`() {
    var otherKey = ""
    val remover = RecordingListener { tracker.removeListener(otherKey) }
    val other = RecordingListener()
    tracker.addListener(remover)
    otherKey = tracker.addListener(other)

    tracker.reportSuccess(POLLING)
    mainQueue.drain()

    assertThat(remover.calls).hasSize(1)
    assertThat(other.calls).isEmpty()
  }

  @Test
  fun `a listener that throws does not stop other listeners and does not throw to the reporter`() {
    tracker.addListener { throw IllegalStateException("app bug") }
    val other = RecordingListener().also { tracker.addListener(it) }

    tracker.reportFailure(POLLING, error500)
    mainQueue.drain()

    assertThat(other.calls).hasSize(1)
  }

  // Listener calls are queued to the main thread, one task per change.
  @Test
  fun `each change queues one main-thread task with its own status, in report order`() {
    val listener = RecordingListener().also { tracker.addListener(it) }

    clock.now = 2_000L
    tracker.reportSuccess(POLLING)
    tracker.finishInitializing(succeeded = true)
    clock.now = 3_000L
    tracker.reportFailure(POLLING, error500)
    clock.now = 4_000L
    tracker.reportSuccess(POLLING)

    assertThat(listener.calls).isEmpty()
    assertThat(mainQueue.size).isEqualTo(3)

    mainQueue.drain()

    assertThat(listener.calls.map { it.state to it.changedAt })
      .containsExactly(SYNCED to 2_000L, FAILING to 3_000L, SYNCED to 4_000L)
      .inOrder()
  }

  @Test
  fun `a change queued before destroy calls no listener, and DESTROYED is the only call`() {
    val listener = RecordingListener().also { tracker.addListener(it) }

    tracker.reportFailure(POLLING, error500)
    tracker.markDestroyed()
    mainQueue.drain()

    assertThat(listener.calls.map { it.state }).containsExactly(DESTROYED)
  }

  @Test
  fun `a listener added after a change is not called with that change`() {
    val early = RecordingListener().also { tracker.addListener(it) }

    tracker.reportSuccess(POLLING)
    val late = RecordingListener().also { tracker.addListener(it) }
    mainQueue.drain()

    assertThat(early.calls).hasSize(1)
    assertThat(late.calls).isEmpty()
  }

  private fun moveToFailing(
    at: Long,
    error: BKTException,
  ) {
    clock.now = at - 1_000L
    tracker.reportSuccess(POLLING)
    tracker.finishInitializing(succeeded = true)
    clock.now = at
    tracker.reportFailure(POLLING, error)
    mainQueue.drain()
    check(tracker.getStatus().state == FAILING)
  }
}

private class ManualClock(
  var now: Long,
) : Clock {
  override fun currentTimeMillis(): Long = now

  override fun currentTimeSeconds(): Long = now / 1000
}

private class SequenceIdGenerator : IdGenerator {
  private var next = 0

  override fun newId(): String = "listener-${++next}"
}

/** Stands in for the main thread: holds posted tasks until [drain] runs them in order. */
private class MainQueue {
  private val tasks = ArrayDeque<Runnable>()

  val size: Int get() = tasks.size

  fun post(task: Runnable) {
    tasks.addLast(task)
  }

  fun drain() {
    while (tasks.isNotEmpty()) tasks.removeFirst().run()
  }
}

private class RecordingListener(
  private val onCall: (BKTEvaluationSyncStatus) -> Unit = {},
) : BKTClient.EvaluationSyncStatusListener {
  val calls = mutableListOf<BKTEvaluationSyncStatus>()

  override fun onStatusChanged(status: BKTEvaluationSyncStatus) {
    calls.add(status)
    onCall(status)
  }
}
