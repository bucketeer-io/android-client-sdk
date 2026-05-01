package io.bucketeer.sdk.android.instrumented

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.internal.remote.measureTimeSecondsWithResult
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for the "duration is nil and latencySecond is 0"
 * backend warning.
 * That test will run on real Android devices to ensure that the timer behaves as expected in a real environment.
 */
@RunWith(AndroidJUnit4::class)
internal class MeasureTimeSecondsWithResultTest {
  @Test
  fun measureTimeSecondsWithResultYieldsStrictlyPositiveSecondsForNonTrivialWork() {
    // Even cheap-but-real work (a few iterations of a method call) takes
    // dozens of nanoseconds at minimum; with the new System.nanoTime()
    // backed timer this must always measure > 0.
    repeat(5_000) { iter ->
      val (seconds, _) =
        measureTimeSecondsWithResult {
          // Perform a small amount of real work to ensure that at least one
          // nanosecond elapses between the two nanoTime() reads.
          // Keep the loop at 20 iterations so the test stays fast while still
          // making it very likely that measurable time has elapsed.
          // The production work covered by this regression test, such as
          // network requests, is much more expensive than this synthetic loop.
          var acc = 0L
          for (i in 0 until 20) {
            acc += i.toLong()
          }
          acc
        }
      assertThat(seconds).isGreaterThan(0.0)
      // sanity: anything sub-second should be considered "fast"
      assertThat(seconds).isLessThan(1.0)
      assertThat(iter).isAtLeast(0) // keep `iter` referenced in failure messages
    }
  }
}
