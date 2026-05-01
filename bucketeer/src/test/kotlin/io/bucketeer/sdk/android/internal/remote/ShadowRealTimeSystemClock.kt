package io.bucketeer.sdk.android.internal.remote

import android.os.SystemClock
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Robolectric shadow for [SystemClock] that backs [SystemClock.elapsedRealtimeNanos]
 * with [System.nanoTime] so that timing assertions in unit tests see real elapsed time
 * instead of a frozen fake clock.
 *
 * Note: in production, [SystemClock.elapsedRealtimeNanos] is preferred over
 * [System.nanoTime] on Android because it continues to count time while the
 * device is in deep sleep, whereas [System.nanoTime] only advances while the
 * device is active.
 */
@Implements(SystemClock::class)
internal object ShadowRealTimeSystemClock {
  @Implementation
  @JvmStatic
  fun elapsedRealtimeNanos(): Long = System.nanoTime()
}
