package io.bucketeer.sdk.android.internal.streaming

import androidx.annotation.VisibleForTesting
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.logd
import io.bucketeer.sdk.android.internal.loge
import io.bucketeer.sdk.android.internal.remote.FixJsonContentTypeInterceptor
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The real event source: one OkHttp call per attempt.
 *
 * The response is read on OkHttp's thread, never on the SDK executor, so a stream that stays open
 * doesn't block polling or events. Every callback is posted to [executor] (the SDK executor), so
 * the rest of streaming runs there without locks.
 */
internal class OkHttpEventSource(
  private val client: OkHttpClient,
  private val executor: Executor,
) : EventSource {
  override var onOpen: (() -> Unit)? = null
  override var onMessage: ((SSEEvent) -> Unit)? = null
  override var onBytesReceived: (() -> Unit)? = null
  override var onError: ((status: Int?) -> Unit)? = null

  // Set before open(); read only by the posted blocks on the executor.
  private val listeners = mutableMapOf<String, (SSEEvent) -> Unit>()

  // Used only inside the posted blocks, so only on the executor.
  private val parser = SSEParser()

  // close() may run on any thread, for example the main thread when the app goes to background.
  private val closed = AtomicBoolean(false)

  @Volatile private var call: Call? = null

  @get:VisibleForTesting
  internal val isCallCanceled: Boolean
    get() = call?.isCanceled() == true

  override fun addEventListener(
    type: String,
    listener: (SSEEvent) -> Unit,
  ) {
    listeners[type] = listener
  }

  override fun open(request: Request) {
    // Caller headers win. Content-Type comes from the body; the client's network interceptor
    // turns it into exactly "application/json".
    val prepared =
      if (request.header(HEADER_ACCEPT) == null) {
        request.newBuilder().header(HEADER_ACCEPT, CONTENT_TYPE_EVENT_STREAM).build()
      } else {
        request
      }
    val newCall = client.newCall(prepared)
    // Set the call before checking `closed`. A close() before the check stops here; a close()
    // after it cancels this call, and OkHttp fails a cancelled call before any network work,
    // even when it is enqueued after the cancel.
    call = newCall
    if (closed.get()) return
    newCall.enqueue(
      object : Callback {
        override fun onFailure(
          call: Call,
          e: IOException,
        ) {
          // After close(), this is the cancellation: post() drops it.
          reportError(null)
        }

        override fun onResponse(
          call: Call,
          response: Response,
        ) {
          // OkHttp calls either onResponse or onFailure, never both, and every path below ends
          // right after it reports. So onError is reported at most once.
          response.use { readResponse(it) }
        }
      },
    )
  }

  /** Safe from any thread, and takes effect at once even while the executor is busy. */
  override fun close() {
    closed.set(true)
    // Only closes the socket; it doesn't block.
    call?.cancel()
  }

  // OkHttp's thread.
  private fun readResponse(response: Response) {
    if (!response.isSuccessful) {
      logd { "Stream request failed with HTTP ${response.code}" }
      reportError(response.code)
      return
    }
    post { onOpen?.invoke() }
    try {
      val input = response.body!!.byteStream()
      val buffer = ByteArray(READ_BUFFER_SIZE)
      // After close(), the cancelled call makes the next read fail anyway. Checking `closed`
      // also skips bytes that were already received when close() ran.
      while (!closed.get()) {
        val count = input.read(buffer)
        if (count == -1) {
          // The server ended the stream. Reported as a network error, so the caller retries.
          reportError(null)
          return
        }
        // The buffer is reused by the next read while this chunk waits in the executor queue.
        val chunk = buffer.copyOf(count)
        post { deliver(chunk) }
      }
    } catch (e: IOException) {
      reportError(null)
    }
  }

  // SDK executor.
  private fun deliver(chunk: ByteArray) {
    onBytesReceived?.invoke()
    for (event in parser.append(chunk)) {
      // close() can run on another thread, or in a handler, while this chunk is being delivered.
      if (closed.get()) return
      // One failing handler must not drop the other events of the same chunk.
      runCatching { dispatch(event) }.onFailure { loge(it) { "Stream event handler failed" } }
    }
  }

  private fun dispatch(event: SSEEvent) {
    val listener = listeners[event.name]
    when {
      listener != null -> listener(event)
      event.name == SSEParser.DEFAULT_EVENT_NAME || event.name.isEmpty() -> onMessage?.invoke(event)
      else -> logd { "Dropped stream event with no listener: ${event.name}" }
    }
  }

  private fun reportError(status: Int?) = post { onError?.invoke(status) }

  /**
   * Every callback goes through here: it runs on the executor, never after close(), and an
   * exception is logged instead of being lost in the executor.
   */
  private fun post(block: () -> Unit) {
    executor.execute {
      if (!closed.get()) {
        runCatching(block).onFailure { loge(it) { "Stream callback failed" } }
      }
    }
  }

  private companion object {
    const val HEADER_ACCEPT = "Accept"
    const val CONTENT_TYPE_EVENT_STREAM = "text/event-stream"
    const val READ_BUFFER_SIZE = 8_192
  }
}

/** The client every stream connection uses. */
internal fun createStreamHttpClient(): OkHttpClient =
  OkHttpClient
    .Builder()
    // A stream never "finishes", so no limit on the whole call.
    .callTimeout(0, TimeUnit.MILLISECONDS)
    // Above the 70 s silence timer, so our own timer fires first.
    .readTimeout(Constants.Streaming.READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
    // Our backoff decides when to retry, not OkHttp.
    .retryOnConnectionFailure(false)
    // A network interceptor, as in ApiClientImpl: as a normal interceptor, OkHttp would add
    // "; charset=utf-8" back to Content-Type after it ran.
    .addNetworkInterceptor(FixJsonContentTypeInterceptor())
    .build()
