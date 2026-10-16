package io.bucketeer.sdk.android.internal.streaming.fakes

import io.bucketeer.sdk.android.internal.streaming.EventSource
import io.bucketeer.sdk.android.internal.streaming.SSEEvent
import okhttp3.Request
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scripted event source. Like the real one, every callback is posted through [executor]: nothing
 * runs until the test drains it, so a test can queue a callback, change state, then run it.
 *
 * [closed] is checked when posting, not when the posted block runs. A block queued before
 * [close] still reaches its callback, so the guards of the code under test are what decide.
 */
internal class FakeEventSource(
  private val executor: Executor,
) : EventSource {
  override var onOpen: (() -> Unit)? = null
  override var onMessage: ((SSEEvent) -> Unit)? = null
  override var onBytesReceived: (() -> Unit)? = null
  override var onError: ((status: Int?) -> Unit)? = null

  val openedRequests = mutableListOf<Request>()
  val listeners = mutableMapOf<String, (SSEEvent) -> Unit>()

  // close() may come from another thread, as in production.
  val closed = AtomicBoolean(false)

  /**
   * Also posts after [close]. Stands in for a block the real source posted just before
   * [close], which still runs afterwards.
   */
  var deliverEvenIfClosed = false

  override fun addEventListener(
    type: String,
    listener: (SSEEvent) -> Unit,
  ) {
    listeners[type] = listener
  }

  override fun open(request: Request) {
    openedRequests.add(request)
  }

  override fun close() {
    closed.set(true)
  }

  fun simulateOpen() = post { onOpen?.invoke() }

  fun simulateBytes() = post { onBytesReceived?.invoke() }

  /**
   * A named event: bytes first, then its listener (dropped if none), as the real source does.
   * [withBytes] = false delivers the event alone, to test that the event itself counts as
   * proof of life.
   */
  fun simulateEvent(
    name: String,
    data: String,
    withBytes: Boolean = true,
  ) = post {
    if (withBytes) onBytesReceived?.invoke()
    listeners[name]?.invoke(SSEEvent(name = name, data = data))
  }

  /** An unnamed event: bytes first (unless [withBytes] is false), then [onMessage]. */
  fun simulateMessage(
    data: String,
    withBytes: Boolean = true,
  ) = post {
    if (withBytes) onBytesReceived?.invoke()
    onMessage?.invoke(SSEEvent(name = "message", data = data))
  }

  fun simulateError(status: Int?) = post { onError?.invoke(status) }

  private fun post(block: () -> Unit) {
    if (closed.get() && !deliverEvenIfClosed) return
    executor.execute { block() }
  }
}
