package io.bucketeer.sdk.android.internal.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
 * This unit test uses [ShadowRealTimeSystemClock] — a Robolectric shadow that
 * replaces `SystemClock.elapsedRealtimeNanos()` with `System.nanoTime()` so
 * the suite runs on the JVM without a device. `System.nanoTime()` does *not*
 * advance during Android deep sleep, so this variant only validates the
 * arithmetic and real-elapsed-time capture. The instrumentation counterpart
 * (`androidTest/.../MeasureTimeSecondsWithResultTest`) runs the same cases
 * against the real `SystemClock` on an actual Android device, exercising
 * the sleep-aware clock path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRealTimeSystemClock::class])
internal class MeasureTimeSecondsWithResultTest {
  @Test
  fun `measureTimeSecondsWithResult returns the block's result`() {
    val (_, result) = measureTimeSecondsWithResult { 42 }
    assertThat(result).isEqualTo(42)
  }

  @Test
  fun `measureTimeSecondsWithResult returns a non-negative finite Double`() {
    val (seconds, _) = measureTimeSecondsWithResult { Unit }
    assertThat(seconds).isAtLeast(0.0)
    assertThat(seconds.isFinite()).isTrue()
  }

  @Test
  fun `measureTimeSecondsWithResult yields strictly positive seconds for non-trivial work (regression for latencySecond=0)`() {
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
  fun `measureTimeSecondsWithResult has sub-millisecond resolution`() {
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
