package io.bucketeer.sdk.android

import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.internal.remote.retryOnException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

@RunWith(RobolectricTestRunner::class)
class RetryOnExceptionTest {

  private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

  @Test
  fun `success on first attempt`() {
    var attempts = 0
    val result = retryOnException(
      executor = executor,
      maxRetries = 3,
      delayMillis = 10,
      exceptionCheck = { true },
    ) {
      attempts++
      "success"
    }

    assertThat(result).isEqualTo("success")
    assertThat(attempts).isEqualTo(1)
  }

  @Test
  fun `success after retries`() {
    var attempts = 0
    val result = retryOnException(
      executor = executor,
      maxRetries = 3,
      delayMillis = 10,
      exceptionCheck = { true },
    ) {
      attempts++
      if (attempts < 3) throw RuntimeException("fail") else "success"
    }

    assertThat(result).isEqualTo("success")
    assertThat(attempts).isEqualTo(3)
  }

  @Test
  fun `failure after max retries`() {
    var attempts = 0
    val exception = assertThrows(RuntimeException::class.java) {
      retryOnException(
        executor = executor,
        maxRetries = 2,
        delayMillis = 10,
        exceptionCheck = { true },
      ) {
        attempts++
        throw RuntimeException("fail")
      }
    }

    assertThat(exception.message).isEqualTo("fail")
    assertThat(attempts).isEqualTo(3) // 1 initial + 2 retries
  }

  @Test
  fun `no retry on non-retriable exception`() {
    var attempts = 0
    val exception = assertThrows(IllegalArgumentException::class.java) {
      retryOnException(
        executor = executor,
        maxRetries = 3,
        delayMillis = 10,
        exceptionCheck = { it !is IllegalArgumentException },
      ) {
        attempts++
        throw IllegalArgumentException("non-retriable")
      }
    }

    assertThat(exception.message).isEqualTo("non-retriable")
    assertThat(attempts).isEqualTo(1)
  }

  @Test
  fun `retry stops on first success after failures`() {
    var attempts = 0
    val result = retryOnException(
      executor = executor,
      maxRetries = 5,
      delayMillis = 10,
      exceptionCheck = { true },
    ) {
      attempts++
      if (attempts < 2) throw RuntimeException("fail") else "success"
    }

    assertThat(result).isEqualTo("success")
    assertThat(attempts).isEqualTo(2)
  }

  @Test
  fun `returns correct value type`() {
    val intResult = retryOnException(
      executor = executor,
      maxRetries = 1,
      delayMillis = 10,
      exceptionCheck = { true },
    ) {
      42
    }

    assertThat(intResult).isEqualTo(42)

    val listResult = retryOnException(
      executor = executor,
      maxRetries = 1,
      delayMillis = 10,
      exceptionCheck = { true },
    ) {
      listOf("a", "b", "c")
    }

    assertThat(listResult).containsExactly("a", "b", "c")
  }

  @Test
  fun `exception check with specific exception types`() {
    var attempts = 0
    val exception = assertThrows(IllegalStateException::class.java) {
      retryOnException(
        executor = executor,
        maxRetries = 3,
        delayMillis = 10,
        exceptionCheck = { it is RuntimeException && it !is IllegalStateException },
      ) {
        attempts++
        throw IllegalStateException("not retriable")
      }
    }

    assertThat(exception.message).isEqualTo("not retriable")
    assertThat(attempts).isEqualTo(1)
  }

  @Test
  fun `mixed exceptions - retry on retriable then fail on non-retriable`() {
    var attempts = 0
    val exception = assertThrows(IllegalArgumentException::class.java) {
      retryOnException(
        executor = executor,
        maxRetries = 5,
        delayMillis = 10,
        exceptionCheck = { it is RuntimeException && it !is IllegalArgumentException },
      ) {
        attempts++
        if (attempts < 3) {
          throw RuntimeException("retriable")
        } else {
          throw IllegalArgumentException("non-retriable")
        }
      }
    }

    assertThat(exception.message).isEqualTo("non-retriable")
    assertThat(attempts).isEqualTo(3)
  }

  @Test
  fun `zero maxRetries means one attempt only`() {
    var attempts = 0
    val exception = assertThrows(RuntimeException::class.java) {
      retryOnException(
        executor = executor,
        maxRetries = 0,
        delayMillis = 10,
        exceptionCheck = { true },
      ) {
        attempts++
        throw RuntimeException("fail")
      }
    }

    assertThat(exception.message).isEqualTo("fail")
    assertThat(attempts).isEqualTo(1)
  }
}

