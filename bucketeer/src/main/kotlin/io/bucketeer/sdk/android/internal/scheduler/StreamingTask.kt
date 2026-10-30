package io.bucketeer.sdk.android.internal.scheduler

import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.di.StreamingTaskDependencies
import io.bucketeer.sdk.android.internal.evaluation.StreamingEvaluationInteractor
import io.bucketeer.sdk.android.internal.evaluation.storage.UserAttributesState
import io.bucketeer.sdk.android.internal.logd
import io.bucketeer.sdk.android.internal.loge
import io.bucketeer.sdk.android.internal.logw
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.model.request.StreamEvaluationsRequest
import io.bucketeer.sdk.android.internal.streaming.Cancellable
import io.bucketeer.sdk.android.internal.streaming.EventSource
import io.bucketeer.sdk.android.internal.streaming.StreamConnection
import io.bucketeer.sdk.android.internal.streaming.StreamConnectionCallbacks
import io.bucketeer.sdk.android.internal.streaming.StreamConnectionErrorInfo
import io.bucketeer.sdk.android.internal.streaming.parseStreamError
import io.bucketeer.sdk.android.internal.streaming.parseStreamedEvaluations
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Keeps evaluations up to date over the SSE stream, falls back to polling while the stream is
 * down, and tries the stream again after 5 minutes. Port of the iOS and JS StreamingTask.
 * TaskScheduler uses it in place of EvaluationForegroundTask when streaming is enabled.
 *
 * Threading: every field except the flags and [liveSource] is used only on the SDK executor. The
 * entry points are called from the main thread (TaskScheduler) or the app's thread (BKTClient);
 * they set their flag first, then post the work. They never wait for the executor, which could
 * freeze the app behind a slow poll. So once stop() returns, no listener fires and queued stream
 * data is dropped.
 *
 * Relies on polls blocking the executor until their reply is written: stream writes and poll
 * writes then never interleave. Making polls asynchronous would break that without a test failing.
 */
internal class StreamingTask(
  private val config: BKTConfig,
  private val userProvider: () -> User,
  private val interactor: StreamingEvaluationInteractor,
  private val moshi: Moshi,
  private val dependencies: StreamingTaskDependencies,
) : ScheduledTask {
  // Any thread. Written by the entry points, read on the executor and, for isRunning, on the main
  // thread (shouldNotify). Connection callbacks and timers check isRunning on the executor, as iOS
  // does: work queued before a quick stop() + start() still runs.
  private val isRunning = AtomicBoolean(false)
  private val isEnabled = AtomicBoolean(false)

  // Any thread, one way. Once set, nothing opens again, so an onStart that reaches the old
  // TaskScheduler can't reopen a stream on a destroyed client.
  private val destroyed = AtomicBoolean(false)

  // Any thread. stop() closes it on the calling thread, so the socket closes at once even while a
  // poll or an event send keeps the executor busy. Android keeps the process running in the
  // background, so a socket left open there would stay open.
  private val liveSource = AtomicReference<EventSource?>(null)

  // Executor only.
  private var connection: StreamConnection? = null
  private var fallbackTask: StreamingFallbackTask? = null
  private var recovery: Cancellable? = null
  private var pendingReconnect: Cancellable? = null
  private var lastRequestAttributesState: UserAttributesState? = null

  // Never reset: only destroy and a new client bring streaming back.
  private var terminalFailure = false

  override fun start() {
    if (destroyed.get()) return
    isRunning.set(true)
    post { openIfReady() }
  }

  /** Called once the initial fetch has finished. */
  fun enable() {
    if (destroyed.get()) return
    isEnabled.set(true)
    post { openIfReady() }
  }

  override fun stop() {
    isRunning.set(false)
    liveSource.getAndSet(null)?.close()
    post {
      connection?.stop()
      connection = null
      stopFallback()
      pendingReconnect?.cancel()
      pendingReconnect = null
    }
  }

  /** One way: the task never starts again. */
  fun destroy() {
    destroyed.set(true)
    stop()
  }

  /** Merges a burst of updates into one reconnect, 200 ms after the last one. */
  fun onUserAttributesUpdated() {
    post {
      if (!isRunning.get()) return@post
      pendingReconnect?.cancel()
      pendingReconnect =
        dependencies.scheduler.schedule(Constants.Streaming.RECONNECT_DEBOUNCE_MILLIS) {
          pendingReconnect = null
          logd { "[StreamingTask] reconnecting after a user attribute change" }
          reconnectNow()
        }
    }
  }

  fun reconnect() {
    post { reconnectNow() }
  }

  private fun reconnectNow() {
    if (!canOpen() || terminalFailure) return
    val current = connection
    if (current != null) {
      current.reconnect()
    } else {
      // The fallback keeps polling until onOpen.
      openStream()
    }
  }

  private fun canOpen(): Boolean = isRunning.get() && isEnabled.get() && !destroyed.get()

  private fun openIfReady() {
    if (!canOpen() || connection != null || fallbackTask != null) return
    if (terminalFailure) {
      startFallback(immediately = false)
    } else {
      openStream()
    }
  }

  private fun openStream() {
    cancelRecovery()
    // The new request reads the latest user, so a merged reconnect still pending is not needed.
    pendingReconnect?.cancel()
    pendingReconnect = null
    val newConnection =
      StreamConnection(
        makeEventSource = { dependencies.makeEventSource().also { liveSource.set(it) } },
        requestBuilder = ::buildRequest,
        events =
          mapOf(
            "put" to ::handleData,
            "patch" to ::handleData,
            "error" to ::logServerError,
          ),
        onUnhandledMessage = ::handleData,
        callbacks =
          StreamConnectionCallbacks(
            onOpen = ::handleOpen,
            onError = ::handleError,
          ),
        scheduler = dependencies.scheduler,
        backoff = dependencies.makeBackoff(),
      )
    // Set before start(): start() calls onError before it returns when the request builder
    // throws, and handleError must find this connection to stop and clear it.
    connection = newConnection
    newConnection.start()
  }

  private fun buildRequest(): Request {
    // Read first: an update between here and reading the user can only leave the flag set, never
    // clear it for attributes this request doesn't carry.
    val attributesState = interactor.userAttributesState
    lastRequestAttributesState = attributesState
    val cached = interactor.cachedEvaluationsState
    val body =
      StreamEvaluationsRequest(
        tag = config.featureTag,
        user = userProvider(),
        sourceId = config.sourceId,
        sdkVersion = config.sdkVersion,
        userEvaluationsId = cached.userEvaluationsId,
        evaluatedAt = cached.evaluatedAt,
      )
    return Request
      .Builder()
      .url(
        config.apiEndpoint
          .toHttpUrl()
          .newBuilder()
          .addPathSegments(Constants.Streaming.STREAM_EVALUATIONS_PATH)
          .build(),
      ).header("Authorization", config.apiKey)
      .header("Accept", "text/event-stream")
      .post(
        moshi
          .adapter(StreamEvaluationsRequest::class.java)
          .toJson(body)
          .toRequestBody("application/json".toMediaType()),
      ).build()
  }

  private fun handleOpen() {
    if (!isRunning.get()) return
    stopFallback()
    lastRequestAttributesState?.let { interactor.clearUserAttributesUpdated(it) }
    logd { "[StreamingTask] stream opened" }
  }

  private fun handleData(data: String) {
    if (!isRunning.get()) return
    val response = parseStreamedEvaluations(moshi, data)
    if (response == null) {
      logd { "[StreamingTask] dropped a payload that is not valid evaluations" }
      return
    }
    try {
      interactor.applyStreamedEvaluations(response) { isRunning.get() }
    } catch (e: Exception) {
      loge(e) { "[StreamingTask] failed to apply streamed evaluations" }
    }
  }

  private fun logServerError(data: String) {
    val error = parseStreamError(moshi, data)
    logw {
      if (error != null) {
        "[StreamingTask] server reported a stream error: code=${error.code}, message=${error.message}"
      } else {
        "[StreamingTask] server reported a stream error: $data"
      }
    }
  }

  private fun handleError(info: StreamConnectionErrorInfo) {
    if (!isRunning.get()) return
    connection?.stop()
    connection = null
    startFallback(immediately = true)
    logd { "[StreamingTask] falling back to polling (terminal=${info.terminal})" }
    if (info.terminal) {
      terminalFailure = true
      logw { "[StreamingTask] streaming has stopped permanently; polling until the client is destroyed and initialized again" }
    } else {
      scheduleRecovery()
    }
  }

  private fun scheduleRecovery() {
    cancelRecovery()
    recovery =
      dependencies.scheduler.schedule(Constants.Streaming.RECOVERY_INTERVAL_MILLIS) {
        recovery = null
        if (isRunning.get()) openStream()
      }
    logd { "[StreamingTask] stream recovery scheduled" }
  }

  private fun cancelRecovery() {
    recovery?.cancel()
    recovery = null
  }

  private fun startFallback(immediately: Boolean) {
    if (fallbackTask != null) return
    fallbackTask = dependencies.makeFallbackTask().also { it.start(immediately) }
  }

  private fun stopFallback() {
    cancelRecovery()
    fallbackTask?.stop()
    fallbackTask = null
  }

  /** Every hop onto the executor goes through here: the executor would drop an exception silently. */
  private fun post(block: () -> Unit) {
    dependencies.executor.execute {
      runCatching(block).onFailure { loge(it) { "[StreamingTask] failed" } }
    }
  }
}
