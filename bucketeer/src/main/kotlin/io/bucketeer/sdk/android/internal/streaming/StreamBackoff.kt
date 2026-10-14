package io.bucketeer.sdk.android.internal.streaming

import kotlin.math.pow
import kotlin.random.Random

/**
 * Exponential backoff with jitter for stream reconnects.
 *
 * Not thread-safe: owned by one StreamConnection on the SDK executor.
 *
 * Unlike retryOnException (used for the polling API's 499 retries), it has no attempt limit, has
 * jitter, and can be reset once a connection proves stable. A reconnect loop that can run for
 * hours needs those, so it is a separate, small class.
 *
 * @param initialDelayMillis delay for the first call to [nextDelayMillis]. Values below 1 are
 * raised to 1 (a 0 delay would mean reconnecting in a tight loop).
 * @param maxDelayMillis upper bound the delay never exceeds. Values below 1 are raised to 1.
 * @param random source of randomness for jitter, in [0, 1). Injectable so tests can pin it.
 */
internal class StreamBackoff(
  initialDelayMillis: Long = DEFAULT_INITIAL_DELAY_MILLIS,
  maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS,
  private val random: () -> Double = { Random.nextDouble() },
) {
  private val initialDelayMillis = maxOf(1L, initialDelayMillis)
  private val maxDelayMillis = maxOf(1L, maxDelayMillis)
  private var attempt = 0

  /**
   * Delay before the next reconnect attempt, in milliseconds. Each call counts as one more
   * attempt: the base delay doubles (capped at the max) and is then reduced by a random amount
   * up to [JITTER_RATIO] of the base, so many clients don't reconnect at the same moment.
   *
   * Computed in Double: as a Long, `initial * 2^attempt` would overflow to a negative delay after
   * about 53 attempts. In Double it becomes Infinity instead, which the max caps.
   */
  fun nextDelayMillis(): Long {
    val exponent = minOf(attempt, MAX_EXPONENT)
    val base = minOf(initialDelayMillis * 2.0.pow(exponent), maxDelayMillis.toDouble()).toLong()
    attempt++
    return base - (random() * JITTER_RATIO * base).toLong()
  }

  /**
   * Starts over from the initial delay. Call once a connection has proven stable, so the next
   * drop backs off from the start instead of continuing to grow.
   */
  fun reset() {
    attempt = 0
  }

  companion object {
    const val DEFAULT_INITIAL_DELAY_MILLIS = 1_000L
    const val DEFAULT_MAX_DELAY_MILLIS = 30_000L
    const val JITTER_RATIO = 0.5

    // 2^30 ms is about 12 days, far above any real max delay, so this cap never changes a
    // reachable delay. It is a second guard: the floor of 1 in the constructor is what keeps
    // `0 * Infinity` (NaN, whose toLong() is 0, a reconnect loop with no wait) from happening.
    // The cap keeps the value finite even if that floor is ever removed.
    const val MAX_EXPONENT = 30
  }
}
