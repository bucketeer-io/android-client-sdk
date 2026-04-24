package io.bucketeer.sdk.android.internal.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

class SettableFutureTest {

  @Test
  fun `set value - success`() {
    val future = SettableFuture<String>()
    
    // Set value on a separate thread
    thread {
      Thread.sleep(50)
      future.set("result")
    }

    // Blocking get
    val result = future.get()
    assertThat(result).isEqualTo("result")
    assertThat(future.isDone).isTrue()
  }

  @Test
  fun `set exception - throws ExecutionException`() {
    val future = SettableFuture<String>()
    val exception = IllegalStateException("error")

    thread {
      Thread.sleep(50)
      future.setException(exception)
    }

    try {
      future.get()
    } catch (e: ExecutionException) {
      assertThat(e.cause).isEqualTo(exception)
    }
    assertThat(future.isDone).isTrue()
  }

  @Test
  fun `cancel - throws CancellationException`() {
    val future = SettableFuture<String>()

    thread {
      Thread.sleep(50)
      future.cancel(true)
    }

    try {
      future.get()
    } catch (e: java.util.concurrent.CancellationException) {
      // Expected
    }
    assertThat(future.isCancelled).isTrue()
    assertThat(future.isDone).isTrue()
  }

  @Test
  fun `get with timeout - throws TimeoutException`() {
    val future = SettableFuture<String>()

    try {
      future.get(10, TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
      // Expected
    }
  }

  @Test
  fun `get with timeout - returns value`() {
    val future = SettableFuture<String>()

    thread {
      Thread.sleep(10)
      future.set("result")
    }

    val result = future.get(100, TimeUnit.MILLISECONDS)
    assertThat(result).isEqualTo("result")
  }
}
