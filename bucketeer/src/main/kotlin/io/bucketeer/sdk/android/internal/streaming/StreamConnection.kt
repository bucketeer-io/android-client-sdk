package io.bucketeer.sdk.android.internal.streaming

import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.loge
import okhttp3.Request

internal data class StreamConnectionErrorInfo(
  /**
   * true: retrying can never work (for example, a bad API key). The caller must not schedule
   * streaming recovery.
   */
  val terminal: Boolean,
)

internal class StreamConnectionCallbacks(
  val onOpen: () -> Unit,
  /**
   * Called only when this connection gives up: a terminal status, a non-recoverable status, or
   * unhealthy for more than 120 s. Short drops heal on their own and do NOT call this.
   */
  val onError: (StreamConnectionErrorInfo) -> Unit,
)

/**
 * Keeps exactly one healthy SSE connection open, reconnecting with backoff when it breaks.
 *
 * Not thread-safe: call it, and deliver its event sources' callbacks, on the SDK executor. It
 * never posts work by itself.
 *
 * Create a new one each time a stream opens. Don't [start] a stopped one again: its unhealthy
 * window and backoff carry over.
 *
 * @param makeEventSource creates a new event source for each connection attempt.
 * @param requestBuilder runs on every (re)connect, so each attempt sends the latest user
 * attributes and cache state. An exception is reported as a non-terminal error.
 * @param events handlers for named events (for example `put`, `patch`, `error`). A `message` key
 * is ignored: unnamed events go to [onUnhandledMessage].
 * @param onUnhandledMessage receives the data of unnamed events. Liveness tracking does not
 * depend on it being set.
 */
internal class StreamConnection(
  private val makeEventSource: () -> EventSource,
  private val requestBuilder: () -> Request,
  private val events: Map<String, (String) -> Unit>,
  private val onUnhandledMessage: ((String) -> Unit)? = null,
  private val callbacks: StreamConnectionCallbacks,
  private val scheduler: StreamScheduler,
  private val backoff: StreamBackoff = StreamBackoff(),
) {
  private var eventSource: EventSource? = null
  private var watchdog: Cancellable? = null
  private var reconnectTimer: Cancellable? = null
  private var backoffResetTimer: Cancellable? = null

  /** Scheduler time when the current unhealthy period started. null while healthy. */
  private var unhealthySince: Long? = null
  private var isActive = false

  fun start() {
    isActive = true
    openConnection()
  }

  /**
   * External reconnect (for example after the user attributes change): a fresh request and a
   * reset backoff.
   *
   * Deliberately does NOT clear [unhealthySince]: an already unhealthy connection keeps its 120 s
   * give-up deadline. Otherwise an app updating user attributes more often than that would put
   * off the polling fallback forever while the stream endpoint stays down.
   */
  fun reconnect() {
    if (!isActive) return
    backoff.reset()
    openConnection()
  }

  fun stop() {
    isActive = false
    clearReconnectTimer()
    closeEventSource()
  }

  private fun openConnection() {
    // Only one connection: cancel any pending retry and close the live event source before
    // opening a new one, so a reconnect() racing a scheduled retry can't produce two.
    clearReconnectTimer()
    closeEventSource()

    val request =
      try {
        requestBuilder()
      } catch (e: Exception) {
        // openConnection() also runs from a timer, where a throw has nowhere to go. Same give-up
        // path as an error status: the caller decides what happens next. An Error is not caught:
        // it is a bug or the VM failing, not a reason to fall back to polling quietly.
        loge(e) { "Failed to build the stream request" }
        callbacks.onError(StreamConnectionErrorInfo(terminal = false))
        return
      }

    val es = makeEventSource()
    eventSource = es

    // Every callback checks that `es` is still the current source: a replaced or closed source
    // can still deliver blocks it posted to the executor before it was closed.
    es.onOpen = {
      if (isCurrent(es)) {
        armBackoffReset()
        resetWatchdog()
        callbacks.onOpen()
      }
    }
    // Every chunk, even a heartbeat comment alone, proves the connection is delivering bytes.
    es.onBytesReceived = {
      if (isCurrent(es)) markHealthy()
    }
    // Wired whether or not onUnhandledMessage is set, so liveness tracking doesn't depend on it.
    es.onMessage = { event ->
      if (isCurrent(es)) {
        markHealthy()
        onUnhandledMessage?.invoke(event.data)
      }
    }
    events
      .filterKeys { it != SSEParser.DEFAULT_EVENT_NAME }
      .forEach { (name, handler) ->
        es.addEventListener(name) { event ->
          if (isCurrent(es)) {
            markHealthy()
            handler(event.data)
          }
        }
      }
    es.onError = { status ->
      if (isCurrent(es)) handleError(status)
    }

    es.open(request)
    // Also the connect timeout: if the request hangs without opening, the watchdog fires and
    // scheduleReconnect() bounds the retries. Armed after open(), which never calls back before
    // it returns.
    resetWatchdog()
  }

  private fun isCurrent(es: EventSource): Boolean = eventSource === es

  private fun handleError(status: Int?) {
    when {
      StreamHttpStatus.isTerminal(status) -> {
        closeEventSource()
        callbacks.onError(StreamConnectionErrorInfo(terminal = true))
      }
      StreamHttpStatus.isRecoverable(status) -> scheduleReconnect()
      else -> {
        // Depends on the request body, which can change later: give up for now and let the
        // caller decide.
        closeEventSource()
        callbacks.onError(StreamConnectionErrorInfo(terminal = false))
      }
    }
  }

  /**
   * Retries with backoff, bounded by the unhealthy window, whether or not the stream ever opened.
   * After giving up, nothing is scheduled and [isActive] stays true.
   */
  private fun scheduleReconnect() {
    closeEventSource()
    if (!isActive) return
    val now = scheduler.nowMillis
    val since = unhealthySince ?: now
    unhealthySince = since
    if (now - since > Constants.Streaming.UNHEALTHY_FALLBACK_TIMEOUT_MILLIS) {
      callbacks.onError(StreamConnectionErrorInfo(terminal = false))
      return
    }
    clearReconnectTimer()
    reconnectTimer = scheduler.schedule(backoff.nextDelayMillis()) { openConnection() }
  }

  private fun markHealthy() {
    unhealthySince = null
    resetWatchdog()
  }

  /**
   * Once the connection has stayed open for 60 s, the next drop backs off from the start.
   * closeEventSource() cancels this if the connection drops first, so a flapping connection keeps
   * escalating.
   */
  private fun armBackoffReset() {
    backoffResetTimer?.cancel()
    backoffResetTimer = scheduler.schedule(Constants.Streaming.RESET_INTERVAL_MILLIS) { backoff.reset() }
  }

  private fun resetWatchdog() {
    watchdog?.cancel()
    watchdog = scheduler.schedule(Constants.Streaming.WATCHDOG_TIMEOUT_MILLIS) { scheduleReconnect() }
  }

  private fun clearReconnectTimer() {
    reconnectTimer?.cancel()
    reconnectTimer = null
  }

  private fun closeEventSource() {
    watchdog?.cancel()
    watchdog = null
    backoffResetTimer?.cancel()
    backoffResetTimer = null
    eventSource?.close()
    eventSource = null
  }
}
