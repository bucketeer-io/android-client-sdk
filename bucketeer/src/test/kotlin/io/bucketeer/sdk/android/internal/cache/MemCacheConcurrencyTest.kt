package io.bucketeer.sdk.android.internal.cache

import io.bucketeer.sdk.android.internal.cache.MemCache
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
class MemCacheConcurrencyTest {
  @Test
  fun `concurrent set operations should be thread-safe`() {
    val cache: MemCache<String, String> = MemCache.Builder<String, String>().build()
    val operationCount = 100
    val startLatch = CountDownLatch(1)
    val doneLatch = CountDownLatch(operationCount)

    // Launch multiple threads to set values concurrently
    repeat(operationCount) { index ->
      thread {
        startLatch.await()
        cache.set("key_$index", "value_$index")
        doneLatch.countDown()
      }
    }

    // Start all operations simultaneously
    startLatch.countDown()

    // Wait for all threads to complete
    assert(doneLatch.await(10, TimeUnit.SECONDS)) { "Timeout waiting for threads to complete" }

    // Verify all values were set correctly
    repeat(operationCount) { index ->
      val result = cache.get("key_$index")
      assert(result == "value_$index") { "Expected value_$index but got $result" }
    }
  }

  @Test
  fun `concurrent get and set on same key should be thread-safe`() {
    val cache: MemCache<String, Int> = MemCache.Builder<String, Int>().build()
    val operationCount = 100
    val startLatch = CountDownLatch(1)
    val doneLatch = CountDownLatch(operationCount)

    // Initialize with a value
    cache.set("counter", 0)

    // Launch concurrent operations that read and write the same key
    repeat(operationCount) { index ->
      thread {
        startLatch.await()
        // Read current value
        val current = cache.get("counter") ?: 0
        // Write new value
        cache.set("counter", index)
        doneLatch.countDown()
      }
    }

    // Start all operations simultaneously
    startLatch.countDown()

    // Wait for all threads to complete
    assert(doneLatch.await(10, TimeUnit.SECONDS)) { "Timeout waiting for threads to complete" }

    // The final value should be one of the written values (0 to operationCount-1)
    val finalValue = cache.get("counter")
    assert(finalValue != null) { "Final value should not be null" }
    assert(finalValue in 0 until operationCount) {
      "Final value $finalValue should be in range 0 to ${operationCount - 1}"
    }
  }

  @Test
  fun `concurrent operations on different keys should not interfere`() {
    val cache: MemCache<String, String> = MemCache.Builder<String, String>().build()
    val keysPerThread = 50
    val threadCount = 4
    val startLatch = CountDownLatch(1)
    val doneLatch = CountDownLatch(threadCount)

    // Each thread works on its own set of keys
    repeat(threadCount) { threadIndex ->
      thread {
        startLatch.await()
        repeat(keysPerThread) { keyIndex ->
          val key = "thread_${threadIndex}_key_$keyIndex"
          val value = "thread_${threadIndex}_value_$keyIndex"
          cache.set(key, value)
        }
        doneLatch.countDown()
      }
    }

    // Start all operations simultaneously
    startLatch.countDown()

    // Wait for all threads to complete
    assert(doneLatch.await(10, TimeUnit.SECONDS)) { "Timeout waiting for threads to complete" }

    // Verify all values are correct
    repeat(threadCount) { threadIndex ->
      repeat(keysPerThread) { keyIndex ->
        val key = "thread_${threadIndex}_key_$keyIndex"
        val expectedValue = "thread_${threadIndex}_value_$keyIndex"
        val actualValue = cache.get(key)
        assert(actualValue == expectedValue) {
          "Expected $expectedValue for $key but got $actualValue"
        }
      }
    }
  }

  @Test
  fun `get on non-existent key should return null safely`() {
    val cache: MemCache<String, String> = MemCache.Builder<String, String>().build()
    val operationCount = 100
    val startLatch = CountDownLatch(1)
    val doneLatch = CountDownLatch(operationCount)

    // Launch concurrent get operations on non-existent keys
    repeat(operationCount) { index ->
      thread {
        startLatch.await()
        val result = cache.get("non_existent_key_$index")
        assert(result == null) { "Expected null but got $result" }
        doneLatch.countDown()
      }
    }

    // Start all operations simultaneously
    startLatch.countDown()

    // Wait for all threads to complete
    assert(doneLatch.await(10, TimeUnit.SECONDS)) { "Timeout waiting for threads to complete" }
  }
}
