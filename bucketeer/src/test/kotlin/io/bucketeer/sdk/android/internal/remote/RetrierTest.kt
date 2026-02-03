package io.bucketeer.sdk.android.internal.remote

import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.BKTException
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RetrierTest {
  private lateinit var executor: ScheduledExecutorService
  private lateinit var retrier: Retrier

  @Before
  fun setup() {
    executor = Executors.newSingleThreadScheduledExecutor()
    retrier = Retrier(executor)
  }

  @After
  fun tearDown() {
    executor.shutdown()
  }

  @Test
  fun `attempt - success on first try`() {
    var attempts = 0
    val latch = CountDownLatch(1)
    var result: Result<String>? = null

    retrier.attempt(
      task = { completion ->
        attempts++
        completion(Result.success("success"))
      },
      condition = { true },
      completion = {
        result = it
        latch.countDown()
      },
    )

    latch.await(5, TimeUnit.SECONDS)
    assertThat(result?.isSuccess).isTrue()
    assertThat(result?.getOrNull()).isEqualTo("success")
    assertThat(attempts).isEqualTo(1)
  }

  @Test
  fun `attempt - success after retries`() {
    var attempts = 0
    val latch = CountDownLatch(1)
    var result: Result<String>? = null

    retrier.attempt(
      task = { completion ->
        attempts++
        if (attempts < 3) {
          completion(Result.failure(BKTException.ClientClosedRequestException("retry")))
        } else {
          completion(Result.success("success"))
        }
      },
      condition = { it is BKTException.ClientClosedRequestException },
      maxAttempts = 4,
      baseDelayMillis = 10, // Short delay for testing
      completion = {
        result = it
        latch.countDown()
      },
    )

    latch.await(5, TimeUnit.SECONDS)
    assertThat(result?.isSuccess).isTrue()
    assertThat(result?.getOrNull()).isEqualTo("success")
    assertThat(attempts).isEqualTo(3)
  }

  @Test
  fun `attempt - failure after max retries`() {
    var attempts = 0
    val latch = CountDownLatch(1)
    var result: Result<String>? = null

    retrier.attempt(
      task = { completion ->
        attempts++
        completion(Result.failure(BKTException.ClientClosedRequestException("fail")))
      },
      condition = { it is BKTException.ClientClosedRequestException },
      maxAttempts = 3,
      baseDelayMillis = 10, // Short delay for testing
      completion = {
        result = it
        latch.countDown()
      },
    )

    latch.await(5, TimeUnit.SECONDS)
    assertThat(result?.isFailure).isTrue()
    assertThat(result?.exceptionOrNull()).isInstanceOf(BKTException.ClientClosedRequestException::class.java)
    assertThat(attempts).isEqualTo(3)
  }

  @Test
  fun `attempt - no retry on non-retriable exception`() {
    var attempts = 0
    val latch = CountDownLatch(1)
    var result: Result<String>? = null

    retrier.attempt(
      task = { completion ->
        attempts++
        completion(Result.failure(BKTException.BadRequestException("bad request")))
      },
      condition = { it is BKTException.ClientClosedRequestException },
      maxAttempts = 4,
      completion = {
        result = it
        latch.countDown()
      },
    )

    latch.await(5, TimeUnit.SECONDS)
    assertThat(result?.isFailure).isTrue()
    assertThat(result?.exceptionOrNull()).isInstanceOf(BKTException.BadRequestException::class.java)
    assertThat(attempts).isEqualTo(1)
  }

  @Test
  fun `attempt - exponential backoff timing`() {
    val startTime = System.currentTimeMillis()
    var attempts = 0
    val attemptTimes = mutableListOf<Long>()
    val latch = CountDownLatch(1)

    retrier.attempt(
      task = { completion ->
        attempts++
        attemptTimes.add(System.currentTimeMillis() - startTime)
        if (attempts < 4) {
          completion(Result.failure(BKTException.ClientClosedRequestException("retry")))
        } else {
          completion(Result.success("success"))
        }
      },
      condition = { it is BKTException.ClientClosedRequestException },
      maxAttempts = 4,
      baseDelayMillis = 100, // 100ms base for testing
      multiplier = 2.0,
      completion = {
        latch.countDown()
      },
    )

    latch.await(10, TimeUnit.SECONDS)
    assertThat(attempts).isEqualTo(4)

    // Verify exponential backoff: 0ms, ~200ms, ~400ms, ~800ms
    // Allow 50ms tolerance for timing variations
    assertThat(attemptTimes[0]).isLessThan(50L)
    assertThat(attemptTimes[1]).isAtLeast(150L)  // 2^1 * 100 = 200ms
    assertThat(attemptTimes[1]).isAtMost(250L)
    assertThat(attemptTimes[2]).isAtLeast(350L)  // 2^2 * 100 = 400ms
    assertThat(attemptTimes[2]).isAtMost(450L)
    assertThat(attemptTimes[3]).isAtLeast(750L)  // 2^3 * 100 = 800ms
    assertThat(attemptTimes[3]).isAtMost(900L)
  }
}
