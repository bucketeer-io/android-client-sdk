package io.bucketeer.sdk.android.internal.streaming.fakes

import com.google.common.truth.Truth.assertWithMessage
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.BKTLogger
import io.bucketeer.sdk.android.createTestBKTConfig
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.di.StreamingTaskDependencies
import io.bucketeer.sdk.android.internal.evaluation.CachedEvaluationsState
import io.bucketeer.sdk.android.internal.evaluation.StreamingEvaluationInteractor
import io.bucketeer.sdk.android.internal.evaluation.storage.UserAttributesState
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.scheduler.StreamingFallbackTask
import io.bucketeer.sdk.android.internal.scheduler.StreamingTask
import io.bucketeer.sdk.android.internal.streaming.EventSource
import io.bucketeer.sdk.android.internal.streaming.StreamBackoff
import io.bucketeer.sdk.android.internal.streaming.StreamScheduler
import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/** Records what the task asked of its polling fallback. */
internal class FakeFallbackTask : StreamingFallbackTask {
  /** The `immediately` argument of every start() call. */
  val startCalls = CopyOnWriteArrayList<Boolean>()

  @Volatile var stopCount = 0
    private set

  @Volatile var isRunning = false
    private set

  override fun start(immediately: Boolean) {
    startCalls.add(immediately)
    isRunning = true
  }

  override fun stop() {
    stopCount++
    isRunning = false
  }
}

/** Settable state, recorded calls, and an access log for ordering checks. */
internal class FakeStreamingEvaluationInteractor : StreamingEvaluationInteractor {
  /** "attributesState", "cachedState", and "user" (appended by the harness's user provider). */
  val accessLog = CopyOnWriteArrayList<String>()

  @Volatile var cachedState = CachedEvaluationsState(userEvaluationsId = "", evaluatedAt = "0")

  @Volatile var attributesState = UserAttributesState(userAttributesUpdated = true, updateSequence = 1)

  /** When set, the next userAttributesState read throws it, once. */
  @Volatile var attributesStateThrowsOnce: RuntimeException? = null

  /** When set, the next applyStreamedEvaluations call throws it, once. */
  @Volatile var applyThrowsOnce: RuntimeException? = null

  val applied = CopyOnWriteArrayList<Pair<GetEvaluationsResponse, () -> Boolean>>()
  val cleared = CopyOnWriteArrayList<UserAttributesState>()

  override val cachedEvaluationsState: CachedEvaluationsState
    get() {
      accessLog.add("cachedState")
      return cachedState
    }

  override val userAttributesState: UserAttributesState
    get() {
      accessLog.add("attributesState")
      attributesStateThrowsOnce?.let {
        attributesStateThrowsOnce = null
        throw it
      }
      return attributesState
    }

  override fun clearUserAttributesUpdated(state: UserAttributesState) {
    cleared.add(state)
  }

  override fun applyStreamedEvaluations(
    response: GetEvaluationsResponse,
    shouldNotify: () -> Boolean,
  ) {
    applyThrowsOnce?.let {
      applyThrowsOnce = null
      throw it
    }
    applied.add(response to shouldNotify)
  }
}

/**
 * Records every event source, fallback and backoff the task creates. Event sources post to
 * [sourceExecutor], the same executor as the task by default.
 */
internal class FakeStreamingTaskDependencies(
  override val executor: Executor,
  override val scheduler: StreamScheduler,
  private val sourceExecutor: Executor = executor,
  /** Shared across tasks when a test needs one list for two clients. */
  val sources: MutableList<FakeEventSource> = CopyOnWriteArrayList(),
) : StreamingTaskDependencies {
  val fallbacks = CopyOnWriteArrayList<FakeFallbackTask>()
  val backoffs = CopyOnWriteArrayList<StreamBackoff>()

  /** When set, the next makeEventSource call throws it, once. */
  @Volatile var makeEventSourceThrowsOnce: RuntimeException? = null

  override fun makeEventSource(): EventSource {
    makeEventSourceThrowsOnce?.let {
      makeEventSourceThrowsOnce = null
      throw it
    }
    return FakeEventSource(sourceExecutor).also { sources.add(it) }
  }

  override fun makeFallbackTask(): StreamingFallbackTask = FakeFallbackTask().also { fallbacks.add(it) }

  override fun makeBackoff(): StreamBackoff = StreamBackoff(random = { 0.0 }).also { backoffs.add(it) }
}

/** Keeps every log line. Add with LoggerHolder.addLogger in @Before, clear in @After. */
internal class RecordingLogger : BKTLogger {
  private val entries = CopyOnWriteArrayList<Triple<Int, String?, Throwable?>>()

  override fun log(
    priority: Int,
    messageCreator: (() -> String?)?,
    throwable: Throwable?,
  ) {
    entries.add(Triple(priority, messageCreator?.invoke(), throwable))
  }

  fun messages(priority: Int): List<String> = entries.filter { it.first == priority }.mapNotNull { it.second }

  fun throwables(priority: Int): List<Throwable> = entries.filter { it.first == priority }.mapNotNull { it.third }
}

/**
 * A [StreamingTask] on the fakes. The fake scheduler is both the executor and the timer queue, so
 * nothing the task posts runs until [scheduler] is driven.
 */
internal class StreamingTaskHarness(
  config: BKTConfig = defaultConfig(),
) {
  val scheduler = FakeStreamScheduler()
  val dependencies = FakeStreamingTaskDependencies(scheduler, scheduler)
  val interactor = FakeStreamingEvaluationInteractor()

  @Volatile var user = User(id = "user-1", data = mapOf("age" to "28"))

  val task =
    StreamingTask(
      config = config,
      userProvider = {
        interactor.accessLog.add("user")
        user
      },
      interactor = interactor,
      moshi = DataModule.createMoshi(),
      dependencies = dependencies,
    )

  val sources: List<FakeEventSource> get() = dependencies.sources
  val latest: FakeEventSource
    get() {
      assertWithMessage("no event source was created").that(sources).isNotEmpty()
      return sources.last()
    }

  val fallbacks: List<FakeFallbackTask> get() = dependencies.fallbacks
  val latestFallback: FakeFallbackTask
    get() {
      assertWithMessage("no fallback was created").that(fallbacks).isNotEmpty()
      return fallbacks.last()
    }

  /** Enabled and started, with the first stream created (not opened yet). */
  fun startStreaming() {
    task.enable()
    task.start()
    scheduler.runCurrent()
  }

  /** [startStreaming], then the stream opens. */
  fun startOpenStream() {
    startStreaming()
    open()
  }

  fun open() {
    latest.simulateOpen()
    scheduler.runCurrent()
  }

  fun event(
    name: String,
    data: String,
  ) {
    latest.simulateEvent(name, data)
    scheduler.runCurrent()
  }

  fun message(data: String) {
    latest.simulateMessage(data)
    scheduler.runCurrent()
  }

  fun error(status: Int?) {
    latest.simulateError(status)
    scheduler.runCurrent()
  }

  /** A body-dependent status: StreamConnection gives up at once, non-terminal. */
  fun giveUpNonTerminal() = error(400)

  /** A bad API key: StreamConnection gives up at once, terminal. */
  fun giveUpTerminal() = error(401)

  fun reconnect() {
    task.reconnect()
    scheduler.runCurrent()
  }

  companion object {
    fun defaultConfig(): BKTConfig =
      createTestBKTConfig(
        apiKey = "key",
        apiEndpoint = "https://example.com",
        featureTag = "android",
        enableStreaming = true,
      )

    fun body(request: Request): JSONObject {
      val buffer = Buffer()
      request.body!!.writeTo(buffer)
      return JSONObject(buffer.readUtf8())
    }

    /** A valid put/patch payload. */
    fun payload(
      userEvaluationsId: String = "ueid-1",
      createdAt: String = "1700000000",
    ): String =
      """{"userEvaluationsId":"$userEvaluationsId","evaluations":{"id":"","evaluations":[],""" +
        """"createdAt":"$createdAt","forceUpdate":false,"archivedFeatureIds":[]}}"""
  }
}
