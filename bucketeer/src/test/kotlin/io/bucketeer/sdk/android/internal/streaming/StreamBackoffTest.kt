package io.bucketeer.sdk.android.internal.streaming

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreamBackoffTest {
  private fun StreamBackoff.take(count: Int): List<Long> = List(count) { nextDelayMillis() }

  @Test
  fun `starts at the initial delay and doubles until the max, then caps`() {
    val backoff = StreamBackoff(random = { 0.0 })

    assertThat(backoff.take(7))
      .containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L)
      .inOrder()
  }

  @Test
  fun `initial delay above the max returns the max on the first call`() {
    val backoff = StreamBackoff(initialDelayMillis = 40_000, maxDelayMillis = 30_000, random = { 0.0 })

    assertThat(backoff.nextDelayMillis()).isEqualTo(30_000L)
  }

  @Test
  fun `jitter subtracts up to half of the base delay`() {
    val backoff = StreamBackoff(random = { 1.0 })

    assertThat(backoff.take(2)).containsExactly(500L, 1_000L).inOrder()
  }

  @Test
  fun `reset starts over from the initial delay`() {
    val backoff = StreamBackoff(random = { 0.0 })

    assertThat(backoff.take(2)).containsExactly(1_000L, 2_000L).inOrder()
    backoff.reset()
    assertThat(backoff.nextDelayMillis()).isEqualTo(1_000L)
  }

  @Test
  fun `many attempts never overflow and stay at the max`() {
    val delays = StreamBackoff(random = { 0.0 }).take(10_000)

    assertThat(delays.all { it > 0 }).isTrue()
    assertThat(delays.last()).isEqualTo(30_000L)
  }

  @Test
  fun `real random delays stay between half and all of the base`() {
    val backoff = StreamBackoff()
    val bases = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L)

    repeat(1_000) { attempt ->
      val base = bases.getOrElse(attempt) { 30_000L }
      assertThat(backoff.nextDelayMillis()).isIn(Range.closed(base / 2, base))
    }
  }

  @Test
  fun `zero initial delay is raised to one`() {
    val backoff = StreamBackoff(initialDelayMillis = 0, random = { 0.0 })

    assertThat(backoff.take(3)).containsExactly(1L, 2L, 4L).inOrder()
    assertThat(backoff.take(10_000).all { it > 0 }).isTrue()
  }

  @Test
  fun `negative and zero inputs are raised to one`() {
    val backoff = StreamBackoff(initialDelayMillis = -5, maxDelayMillis = 0, random = { 0.0 })

    assertThat(backoff.take(100).toSet()).containsExactly(1L)
  }
}
