package io.bucketeer.sdk.android.internal.scheduler

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.createTestBKTConfig
import io.bucketeer.sdk.android.internal.di.ComponentImpl
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.di.InteractorModule
import io.bucketeer.sdk.android.internal.di.StreamingTaskDependenciesImpl
import io.bucketeer.sdk.android.internal.streaming.ExecutorStreamScheduler
import io.bucketeer.sdk.android.internal.streaming.OkHttpEventSource
import io.bucketeer.sdk.android.internal.streaming.fakes.FakeStreamScheduler
import io.bucketeer.sdk.android.internal.streaming.fakes.FakeStreamingTaskDependencies
import io.bucketeer.sdk.android.internal.streaming.fakes.StreamingTaskHarness.Companion.body
import io.bucketeer.sdk.android.mocks.user1
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class TaskSchedulerStreamingTest {
  private lateinit var executor: ScheduledExecutorService
  private lateinit var fakeScheduler: FakeStreamScheduler
  private lateinit var dependencies: FakeStreamingTaskDependencies
  private val components = mutableListOf<ComponentImpl>()

  private val owner =
    object : LifecycleOwner {
      override val lifecycle = LifecycleRegistry(this)
    }

  @Before
  fun setup() {
    executor = Executors.newSingleThreadScheduledExecutor()
    fakeScheduler = FakeStreamScheduler()
    dependencies = FakeStreamingTaskDependencies(fakeScheduler, fakeScheduler)
  }

  @After
  fun tearDown() {
    executor.submit { components.forEach { it.dataModule.destroy() } }.get(5, TimeUnit.SECONDS)
    executor.shutdownNow()
  }

  private fun component(enableStreaming: Boolean): ComponentImpl =
    ComponentImpl(
      dataModule =
        DataModule(
          application = ApplicationProvider.getApplicationContext(),
          config =
            createTestBKTConfig(
              apiKey = "key",
              apiEndpoint = "https://example.com",
              featureTag = "android",
              enableStreaming = enableStreaming,
            ),
          user = user1,
          inMemoryDB = true,
        ),
      interactorModule = InteractorModule(mainHandler = Handler(Looper.getMainLooper())),
    ).also { components.add(it) }

  private fun scheduler(enableStreaming: Boolean = true): TaskScheduler = TaskScheduler(component(enableStreaming), executor, dependencies)

  /** onStart, enable, and the first stream created. */
  private fun streaming(): TaskScheduler =
    scheduler().also {
      it.onStart(owner)
      it.enableEvaluationTask()
      fakeScheduler.runCurrent()
    }

  @Test
  fun `streaming config uses StreamingTask`() {
    streaming()

    assertThat(dependencies.sources).hasSize(1)
  }

  @Test
  fun `polling config keeps EvaluationForegroundTask`() {
    val scheduler = scheduler(enableStreaming = false)
    scheduler.onStart(owner)
    scheduler.enableEvaluationTask()
    fakeScheduler.runCurrent()
    scheduler.stop()

    assertThat(dependencies.sources).isEmpty()
    assertThat(dependencies.fallbacks).isEmpty()
  }

  @Test
  fun `default dependencies use the SDK executor`() {
    val impl = StreamingTaskDependenciesImpl(component(enableStreaming = true), executor)

    assertThat(impl.executor).isSameInstanceAs(executor)
    assertThat(impl.scheduler).isInstanceOf(ExecutorStreamScheduler::class.java)
    assertThat(impl.makeEventSource()).isInstanceOf(OkHttpEventSource::class.java)
    assertThat(impl.makeFallbackTask()).isInstanceOf(EvaluationForegroundTask::class.java)
    assertThat(impl.makeBackoff()).isNotNull()
  }

  @Test
  fun `enableEvaluationTask opens the stream`() {
    val scheduler = scheduler()
    scheduler.onStart(owner)
    fakeScheduler.runCurrent()
    assertThat(dependencies.sources).isEmpty()

    scheduler.enableEvaluationTask()
    fakeScheduler.runCurrent()

    assertThat(dependencies.sources).hasSize(1)
  }

  @Test
  fun `attribute updates are forwarded to the stream`() {
    val scheduler = streaming()
    dependencies.sources.last().simulateOpen()
    fakeScheduler.runCurrent()

    scheduler.onUserAttributesUpdated()
    fakeScheduler.advanceBy(199)
    assertThat(dependencies.sources).hasSize(1)
    fakeScheduler.advanceBy(1)

    assertThat(dependencies.sources).hasSize(2)
  }

  @Test
  fun `attribute update is a no-op when polling`() {
    val scheduler = scheduler(enableStreaming = false)
    scheduler.onStart(owner)

    scheduler.onUserAttributesUpdated()
    fakeScheduler.advanceBy(1_000)
    scheduler.stop()

    assertThat(dependencies.sources).isEmpty()
    assertThat(dependencies.fallbacks).isEmpty()
  }

  @Test
  fun `onStop closes the stream and onStart opens a new one`() {
    val scheduler = streaming()
    val first = dependencies.sources.single()

    scheduler.onStop(owner)
    assertThat(first.closed.get()).isTrue()

    scheduler.onStart(owner)
    fakeScheduler.runCurrent()

    assertThat(dependencies.sources).hasSize(2)
    assertThat(dependencies.sources[1].closed.get()).isFalse()
  }

  @Test
  fun `attributes updated while in the background are sent on foreground`() {
    val component = component(enableStreaming = true)
    val scheduler = TaskScheduler(component, executor, dependencies)
    scheduler.onStart(owner)
    scheduler.enableEvaluationTask()
    fakeScheduler.runCurrent()
    scheduler.onStop(owner)

    component.userHolder.updateAttributes { mapOf("age" to "40") }
    scheduler.onUserAttributesUpdated()
    scheduler.onStart(owner)
    fakeScheduler.advanceBy(200)

    assertThat(dependencies.sources).hasSize(2)
    val user =
      body(
        dependencies.sources
          .last()
          .openedRequests
          .single(),
      ).getJSONObject("user")
    assertThat(user.getJSONObject("data").getString("age")).isEqualTo("40")
  }

  @Test
  fun `destroyStreaming closes the stream for good`() {
    val scheduler = streaming()
    val source = dependencies.sources.single()

    scheduler.destroyStreaming()
    assertThat(source.closed.get()).isTrue()

    source.deliverEvenIfClosed = true
    source.simulateError(400)
    fakeScheduler.runCurrent()
    assertThat(dependencies.fallbacks).isEmpty()

    scheduler.onStart(owner)
    fakeScheduler.runCurrent()
    assertThat(dependencies.sources).hasSize(1)
  }

  @Test
  fun `background launch enables first and streams only after onStart`() {
    val scheduler = scheduler()
    owner.lifecycle.currentState = Lifecycle.State.CREATED

    scheduler.enableEvaluationTask()
    fakeScheduler.runCurrent()
    assertThat(dependencies.sources).isEmpty()

    scheduler.onStart(owner)
    fakeScheduler.runCurrent()

    assertThat(dependencies.sources).hasSize(1)
  }
}
