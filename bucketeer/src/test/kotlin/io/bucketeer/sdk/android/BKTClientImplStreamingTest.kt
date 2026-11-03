package io.bucketeer.sdk.android

import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.streaming.fakes.FakeEventSource
import io.bucketeer.sdk.android.internal.streaming.fakes.FakeStreamScheduler
import io.bucketeer.sdk.android.internal.streaming.fakes.FakeStreamingTaskDependencies
import io.bucketeer.sdk.android.internal.streaming.fakes.StreamingTaskHarness.Companion.body
import io.bucketeer.sdk.android.internal.user.toBKTUser
import io.bucketeer.sdk.android.mocks.user1
import io.bucketeer.sdk.android.mocks.user1Evaluations
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class BKTClientImplStreamingTest {
  private lateinit var server: MockWebServer
  private lateinit var moshi: Moshi
  private val clients = mutableListOf<BKTClientImpl>()

  // The client observes the real process lifecycle, as in production. It is shared by every
  // test in the JVM, so each test starts and ends in the background.
  private fun moveAppTo(state: Lifecycle.State) {
    (ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry).currentState = state
  }

  @Before
  fun setup() {
    moveAppTo(Lifecycle.State.CREATED)
    server = MockWebServer()
    moshi = DataModule.createMoshi()
  }

  @After
  fun tearDown() {
    clients.forEach {
      it.resetTasks()
      it.destroy()
    }
    shadowOf(Looper.getMainLooper()).idle()
    clients.forEach { it.executor.submit {}.get(5, TimeUnit.SECONDS) }
    server.shutdown()
    deleteDatabase(ApplicationProvider.getApplicationContext())
    deleteSharedPreferences(ApplicationProvider.getApplicationContext())
    moveAppTo(Lifecycle.State.CREATED)
  }

  private fun enqueueEvaluations(bodyDelayMillis: Long = 0) {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBodyDelay(bodyDelayMillis, TimeUnit.MILLISECONDS)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java).toJson(
            GetEvaluationsResponse(
              evaluations = user1Evaluations,
              userEvaluationsId = "user_evaluations_id_value",
            ),
          ),
        ),
    )
  }

  private fun client(dependencies: FakeStreamingTaskDependencies): BKTClientImpl =
    BKTClientImpl(
      context = ApplicationProvider.getApplicationContext(),
      config =
        createTestBKTConfig(
          apiKey = "api_key_value",
          apiEndpoint = server.url("").toString(),
          featureTag = "feature_tag_value",
          enableStreaming = true,
        ),
      user = user1.toBKTUser(),
      streamingTaskDependencies = dependencies,
    ).also { clients.add(it) }

  /** Initializes, waits for the initial fetch, runs the posted enable call, then starts. */
  private fun initializeAndStart(
    client: BKTClientImpl,
    fakeScheduler: FakeStreamScheduler,
  ) {
    enqueueEvaluations()
    client.initializeInternal(1_000).get(5, TimeUnit.SECONDS)
    shadowOf(Looper.getMainLooper()).idle()
    moveAppTo(Lifecycle.State.STARTED)
    fakeScheduler.runCurrent()
  }

  /** What BKTClient.destroy() does, on the calling thread. */
  private fun destroyClient(client: BKTClientImpl) {
    client.resetTasks()
    client.destroy()
  }

  @Test
  fun `stream opens only after the initial fetch finishes`() {
    val fakeScheduler = FakeStreamScheduler()
    val dependencies = FakeStreamingTaskDependencies(fakeScheduler, fakeScheduler)
    val client = client(dependencies)
    enqueueEvaluations(bodyDelayMillis = 500)

    val future = client.initializeInternal(2_000)
    // The fetch is in flight (its reply body is delayed): an enable posted too early is queued by now.
    assertThat(server.takeRequest(5, TimeUnit.SECONDS)).isNotNull()
    shadowOf(Looper.getMainLooper()).idle()
    moveAppTo(Lifecycle.State.STARTED)
    fakeScheduler.runCurrent()
    assertThat(dependencies.sources).isEmpty()

    future.get(5, TimeUnit.SECONDS)
    shadowOf(Looper.getMainLooper()).idle()
    fakeScheduler.runCurrent()

    assertThat(dependencies.sources).hasSize(1)
  }

  @Test
  fun `updateUserAttributes reconnects the stream`() {
    val fakeScheduler = FakeStreamScheduler()
    val dependencies = FakeStreamingTaskDependencies(fakeScheduler, fakeScheduler)
    val client = client(dependencies)
    initializeAndStart(client, fakeScheduler)
    dependencies.sources.single().simulateOpen()
    fakeScheduler.runCurrent()

    client.updateUserAttributes(mapOf("age" to "50"))
    fakeScheduler.advanceBy(200)

    assertThat(dependencies.sources).hasSize(2)
    val user =
      body(
        dependencies.sources
          .last()
          .openedRequests
          .single(),
      ).getJSONObject("user")
    assertThat(user.getJSONObject("data").getString("age")).isEqualTo("50")
  }

  @Test
  fun `destroy off the main thread closes the stream and starts no fallback`() {
    val fakeScheduler = FakeStreamScheduler()
    val dependencies = FakeStreamingTaskDependencies(fakeScheduler, fakeScheduler)
    val client = client(dependencies)
    initializeAndStart(client, fakeScheduler)
    val source = dependencies.sources.single()
    source.simulateOpen()
    fakeScheduler.runCurrent()
    val requestsBefore = server.requestCount

    val thread = Thread { destroyClient(client) }
    thread.start()
    thread.join(5_000)
    clients.remove(client)

    assertThat(source.closed.get()).isTrue()
    source.deliverEvenIfClosed = true
    source.simulateError(400)
    fakeScheduler.runCurrent()
    // The observer is removed by a block posted to the main thread, not run yet: a foreground
    // now still reaches this TaskScheduler and must open nothing.
    moveAppTo(Lifecycle.State.CREATED)
    moveAppTo(Lifecycle.State.STARTED)
    fakeScheduler.runCurrent()
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(dependencies.sources).hasSize(1)
    assertThat(dependencies.fallbacks).isEmpty()
    assertThat(server.requestCount).isEqualTo(requestsBefore)
  }

  @Test
  fun `destroy before the main thread creates the tasks opens no stream`() {
    val fakeScheduler = FakeStreamScheduler()
    val dependencies = FakeStreamingTaskDependencies(fakeScheduler, fakeScheduler)
    val client = client(dependencies)
    enqueueEvaluations()
    moveAppTo(Lifecycle.State.STARTED)

    // Both off the main thread: initialize posts the block that creates the tasks, the finished
    // fetch posts the enable call, then destroy posts its cleanup. None of them has run yet.
    val init = Thread { client.initializeInternal(1_000).get(5, TimeUnit.SECONDS) }
    init.start()
    init.join(5_000)
    val destroy = Thread { destroyClient(client) }
    destroy.start()
    destroy.join(5_000)
    clients.remove(client)

    // The executor runs between the enable call and the cleanup, as it can in production. Without
    // the fix, the start (from onStart) and the enable call each post one block to the executor.
    val mainLooper = shadowOf(Looper.getMainLooper())
    repeat(10) {
      if (fakeScheduler.pendingCount < 2 && !mainLooper.isIdle) mainLooper.runOneTask()
    }
    fakeScheduler.runCurrent()
    mainLooper.idle()
    fakeScheduler.advanceBy(1_000)

    assertThat(dependencies.sources).isEmpty()
  }

  @Test
  fun `updateUserAttributes racing destroy on another thread neither crashes nor opens a stream`() {
    val fakeScheduler = FakeStreamScheduler()
    val dependencies = FakeStreamingTaskDependencies(fakeScheduler, fakeScheduler)
    val client = client(dependencies)
    initializeAndStart(client, fakeScheduler)
    assertThat(dependencies.sources).hasSize(1)
    val failures = CopyOnWriteArrayList<Throwable>()

    val a =
      Thread {
        try {
          repeat(200) { client.updateUserAttributes(mapOf("round" to "$it")) }
        } catch (t: Throwable) {
          failures.add(t)
        }
      }
    val b =
      Thread {
        try {
          destroyClient(client)
        } catch (t: Throwable) {
          failures.add(t)
        }
      }
    a.start()
    b.start()
    a.join(5_000)
    b.join(5_000)
    clients.remove(client)
    shadowOf(Looper.getMainLooper()).idle()
    fakeScheduler.advanceBy(1_000)

    assertThat(failures).isEmpty()
    assertThat(dependencies.sources).hasSize(1)
  }

  @Test
  fun `destroy then initialize right away leaves exactly one stream`() {
    val shared = CopyOnWriteArrayList<FakeEventSource>()
    val firstScheduler = FakeStreamScheduler()
    val first = client(FakeStreamingTaskDependencies(firstScheduler, firstScheduler, sources = shared))
    initializeAndStart(first, firstScheduler)
    assertThat(shared).hasSize(1)

    destroyClient(first)
    clients.remove(first)
    val secondScheduler = FakeStreamScheduler()
    val second = client(FakeStreamingTaskDependencies(secondScheduler, secondScheduler, sources = shared))
    initializeAndStart(second, secondScheduler)
    shadowOf(Looper.getMainLooper()).idle()
    firstScheduler.advanceBy(1_000)
    secondScheduler.advanceBy(1_000)

    assertThat(shared).hasSize(2)
    assertThat(shared[0].closed.get()).isTrue()
    assertThat(shared.count { !it.closed.get() }).isEqualTo(1)
    assertThat(shared[1].closed.get()).isFalse()
  }
}
