package io.bucketeer.sdk.android
import io.bucketeer.sdk.android.internal.LoggerHolder
import io.bucketeer.sdk.android.internal.logd
import junit.framework.Assert.fail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

internal class LoggerHolderTest {
  @Test
  fun testConcurrentModification(): Unit =
    runBlocking {
      class TestLogger : BKTLogger {
        override fun log(
          priority: Int,
          messageCreator: (() -> String?)?,
          throwable: Throwable?,
        ) {
        }
      }

      // Add initial logger
      LoggerHolder.addLogger(TestLogger())

      // Launch concurrent coroutines to add loggers and log messages
      val job1 =
        launch(Dispatchers.Default) {
          repeat(1000) {
            try {
              logd { "Test message" }
              LoggerHolder.addLogger(TestLogger())
              logd { "Test message" }
            } catch (e: ConcurrentModificationException) {
              fail("ConcurrentModificationException occurred")
            }
          }
        }

      val job2 =
        launch(Dispatchers.IO) {
          repeat(1000) {
            try {
              logd { "Test message" }
              LoggerHolder.addLogger(TestLogger())
              LoggerHolder.addLogger(TestLogger())
            } catch (e: ConcurrentModificationException) {
              fail("ConcurrentModificationException occurred")
            }
          }
        }

      // Wait for both coroutines to finish
      joinAll(job1, job2)
    }
}
