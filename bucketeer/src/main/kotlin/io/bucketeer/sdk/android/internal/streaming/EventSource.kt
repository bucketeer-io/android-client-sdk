package io.bucketeer.sdk.android.internal.streaming

import okhttp3.Request

/**
 * One SSE connection attempt. Create a new one per attempt, never reuse it. Set the callbacks
 * and listeners first, then call [open]. Callbacks run on the SDK executor.
 */
internal interface EventSource {
  /** The server answered 2xx. */
  var onOpen: (() -> Unit)?

  /** Events with no name (the SSE default type "message"). */
  var onMessage: ((SSEEvent) -> Unit)?

  /** Some bytes arrived (an event, part of one, or a heartbeat comment). Proof of life. */
  var onBytesReceived: (() -> Unit)?

  /**
   * The attempt ended: an HTTP status, or null for a network error or the server closing the
   * stream. Called at most once. There is no "terminal" flag: the HTTP status decides.
   */
  var onError: ((status: Int?) -> Unit)?

  /** Events with this name go to [listener]. A named event with no listener is dropped. */
  fun addEventListener(
    type: String,
    listener: (SSEEvent) -> Unit,
  )

  fun open(request: Request)

  /**
   * Safe from any thread. After it returns, no new callback starts. A callback that already
   * passed its closed check may still run, so callers must ignore callbacks after they stop.
   */
  fun close()
}
