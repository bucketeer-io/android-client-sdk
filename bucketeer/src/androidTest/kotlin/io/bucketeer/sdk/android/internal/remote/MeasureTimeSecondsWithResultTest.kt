package io.bucketeer.sdk.android.internal.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for [measureTimeSecondsWithResult] — the fix for the
 * "duration is nil and latencySecond is 0" backend warning.
 *
 * Before the fix, [measureTimeMillisWithResult] used
 * `System.currentTimeMillis()`, which has 1ms resolution and is wall-clock.
 * Sub-millisecond network responses (cached responses, very fast LANs,
 * AndroidTV clock-tick collisions) measured 0ms; the SDK then sent
 * `latencySecond = 0.0` which the backend rejected.
 *
 * The fix adds [measureTimeSecondsWithResult] which uses
 * `SystemClock.elapsedRealtimeNanos()` (monotonic, sub-millisecond resolution,
 * and crucially counts time during Android deep sleep unlike `System.nanoTime()`)
 * and returns seconds as a Double. Any work that actually executed must measure > 0.
 *
 * **Why two test files exist:**
 * The unit test counterpart (`test/.../MeasureTimeSecondsWithResultTest`) runs
 * on the JVM using a Robolectric shadow that substitutes
 * `SystemClock.elapsedRealtimeNanos()` with `System.nanoTime()`. That shadow
 * does *not* advance during Android deep sleep, so it cannot fully validate the
 * production clock. This instrumentation test runs against the real
 * `SystemClock` on an actual Android device, verifying that the sleep-aware
 * clock path works correctly in production.
 */
@RunWith(AndroidJUnit4::class)
internal class MeasureTimeSecondsWithResultTest {
  @Test
  fun measureTimeSecondsWithResultReturnsTheBlockResult() {
    val (_, result) = measureTimeSecondsWithResult { 42 }
    assertThat(result).isEqualTo(42)
  }

  @Test
  fun measureTimeSecondsWithResultReturnsAnonNegativeFiniteDouble() {
    val (seconds, _) = measureTimeSecondsWithResult { Unit }
    assertThat(seconds).isAtLeast(0.0)
    assertThat(seconds.isFinite()).isTrue()
  }

  @Test
  fun measureTimeSecondsWithResultYieldsStrictlyPositiveSecondsForNonTrivialWork() {
    // Even cheap-but-real work (a few iterations of a method call) takes
    // dozens of nanoseconds at minimum; with the new SystemClock.elapsedRealtimeNanos()
    // backed timer this must always measure > 0.
    repeat(100_000) { iter ->
      val (seconds, _) =
        measureTimeSecondsWithResult {
          // small amount of real work to ensure at least one nanosecond
          // elapses between the two nanoTime() reads.
          var acc = 0L
          for (i in 0 until 50) {
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

  @Test
  fun measureTimeSecondsWithResultHasSubMillisecondResolution() {
    // The pre-fix `System.currentTimeMillis()` timer rounded to whole
    // milliseconds, so a < 1ms measurement was impossible. With
    // `SystemClock.elapsedRealtimeNanos()` we should easily measure a sub-ms interval.
    var sawSubMs = false
    for (i in 0 until 50) {
      val (seconds, _) = measureTimeSecondsWithResult { Unit }
      if (seconds > 0.0 && seconds < 0.001) {
        sawSubMs = true
        break
      }
    }
    assertThat(sawSubMs).isTrue()
  }
}
