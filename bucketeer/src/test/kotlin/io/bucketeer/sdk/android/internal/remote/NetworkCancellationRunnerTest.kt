package io.bucketeer.sdk.android.internal.remote

import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.internal.IdGenerator
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NetworkCancellationRunnerTest {

  private lateinit var runner: NetworkCancellationRunner
  private lateinit var idGenerator: FakeIdGenerator
  private lateinit var executor: FakeScheduledExecutorService

  @Before
  fun setup() {
    idGenerator = FakeIdGenerator()
    executor = FakeScheduledExecutorService()
    runner = NetworkCancellationRunner(executor, idGenerator)
  }

  @Test
  fun `scheduleTask - success on first attempt`() {
    val result = runner.scheduleTask(
      block = { "success" },
      retryPredicate = { false }
    ).get()

    assertThat(result).isEqualTo("success")
  }

  @Test
  fun `scheduleTask - retry and succeed`() {
    val attempts = AtomicInteger(0)
    val result = runner.scheduleTask(
      block = {
        if (attempts.incrementAndGet() < 2) {
          throw IllegalStateException("fail")
        }
        "success"
      },
      retryPredicate = { it is IllegalStateException }
    ).get()

    assertThat(result).isEqualTo("success")
    assertThat(attempts.get()).isEqualTo(2)
  }

  @Test
  fun `scheduleTask - fail after max retries`() {
    val attempts = AtomicInteger(0)
    try {
      runner.scheduleTask(
        block = {
          attempts.incrementAndGet()
          throw IllegalStateException("fail")
        },
        retryPredicate = { it is IllegalStateException }
      ).get()
    } catch (e: Exception) {
      // Expected
    }

    // Initial attempt (0) + 3 retries = 4 attempts total
    assertThat(attempts.get()).isEqualTo(4)
  }

  @Test
  fun `scheduleTaskWithCallback - callback invoked on success`() {
    val callbackResult = java.util.concurrent.atomic.AtomicReference<String>()
    val future = runner.scheduleTaskWithCallback(
      block = { "success" },
      retryPredicate = { false },
      completionCallback = { callbackResult.set(it) }
    )
    future.get()

    assertThat(callbackResult.get()).isEqualTo("success")
  }
}

class FakeIdGenerator : IdGenerator {
  private var count = 0
  override fun newId(): String {
    return "id_${count++}"
  }
}

// Minimal implementation of ScheduledExecutorService for testing
class FakeScheduledExecutorService : java.util.concurrent.ScheduledThreadPoolExecutor(1) {
  override fun execute(command: Runnable) {
    command.run()
  }

  override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): java.util.concurrent.ScheduledFuture<*> {
    command.run()
    return super.schedule({}, 0, TimeUnit.MILLISECONDS) // Return dummy future
  }
}
