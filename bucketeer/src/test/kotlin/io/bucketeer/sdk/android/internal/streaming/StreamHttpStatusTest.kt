package io.bucketeer.sdk.android.internal.streaming

import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class StreamHttpStatusTest {
  @Test
  fun `no response is recoverable`() {
    assertThat(StreamHttpStatus.isRecoverable(null)).isTrue()
  }

  @Test
  fun `5xx is recoverable`(
    @TestParameter("500", "502", "503", "504") status: Int,
  ) {
    assertThat(StreamHttpStatus.isRecoverable(status)).isTrue()
  }

  @Test
  fun `408 429 and 499 are recoverable`(
    @TestParameter("408", "429", "499") status: Int,
  ) {
    assertThat(StreamHttpStatus.isRecoverable(status)).isTrue()
  }

  @Test
  fun `other 4xx are not recoverable`(
    @TestParameter("400", "401", "403", "404", "405", "413", "422") status: Int,
  ) {
    assertThat(StreamHttpStatus.isRecoverable(status)).isFalse()
  }

  @Test
  fun `terminal statuses`(
    @TestParameter("401", "403", "404", "405", "406", "410", "414", "415", "431", "451") status: Int,
  ) {
    assertThat(StreamHttpStatus.isTerminal(status)).isTrue()
  }

  @Test
  fun `non-terminal statuses`(
    @TestParameter("null", "400", "402", "408", "409", "413", "422", "428", "429", "500", "503") status: Int?,
  ) {
    assertThat(StreamHttpStatus.isTerminal(status)).isFalse()
  }

  @Test
  fun `body-dependent statuses are neither recoverable nor terminal`(
    @TestParameter("400", "413", "422") status: Int,
  ) {
    assertThat(StreamHttpStatus.isRecoverable(status)).isFalse()
    assertThat(StreamHttpStatus.isTerminal(status)).isFalse()
  }
}
