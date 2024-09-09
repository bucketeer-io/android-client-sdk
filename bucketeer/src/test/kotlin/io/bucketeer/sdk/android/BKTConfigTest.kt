package io.bucketeer.sdk.android

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class BKTConfigTest {
  @Test
  fun build() {
    val actual =
      BKTConfig
        .builder()
        .apiKey("api-key")
        .apiEndpoint("https://example.com")
        .featureTag("feature-tag")
        .appVersion("1.2.3")
        .build()

    assertThat(actual).isEqualTo(
      BKTConfig(
        apiKey = "api-key",
        apiEndpoint = "https://example.com",
        featureTag = "feature-tag",
        eventsFlushInterval = DEFAULT_FLUSH_INTERVAL_MILLIS,
        eventsMaxBatchQueueCount = DEFAULT_MAX_QUEUE_SIZE,
        pollingInterval = DEFAULT_POLLING_INTERVAL_MILLIS,
        backgroundPollingInterval = DEFAULT_BACKGROUND_POLLING_INTERVAL_MILLIS,
        appVersion = "1.2.3",
        logger = DefaultLogger("Bucketeer"),
      ),
    )
  }

  @Test
  fun `apiKey - unset`() {
    val error =
      assertThrows(BKTException.IllegalArgumentException::class.java) {
        BKTConfig
          .builder()
          .apiEndpoint("https://example.com")
          .featureTag("feature-tag")
          .appVersion("1.2.3")
          .build()
      }

    assertThat(error).hasMessageThat().isEqualTo("apiKey is required")
  }

  @Test
  fun `apiKey - empty`() {
    val error =
      assertThrows(BKTException.IllegalArgumentException::class.java) {
        BKTConfig
          .builder()
          .apiKey("")
          .apiEndpoint("https://example.com")
          .featureTag("feature-tag")
          .appVersion("1.2.3")
          .build()
      }

    assertThat(error).hasMessageThat().isEqualTo("apiKey is required")
  }

  @Test
  fun `apiEndpoint - unset`() {
    val error =
      assertThrows(BKTException.IllegalArgumentException::class.java) {
        BKTConfig
          .builder()
          .apiKey("api-key")
          .featureTag("feature-tag")
          .appVersion("1.2.3")
          .build()
      }

    assertThat(error).hasMessageThat().isEqualTo("apiEndpoint is invalid")
  }

  @Test
  fun `apiEndpoint - invalid`() {
    val error =
      assertThrows(BKTException.IllegalArgumentException::class.java) {
        BKTConfig
          .builder()
          .apiKey("api-key")
          .apiEndpoint("some invalid value")
          .featureTag("feature-tag")
          .appVersion("1.2.3")
          .build()
      }

    assertThat(error).hasMessageThat().isEqualTo("apiEndpoint is invalid")
  }

  @Test
  fun `featureTag - optional`() {
    assertThat(
      runCatching {
        BKTConfig
          .builder()
          .apiKey("api-key")
          .apiEndpoint("https://example.com")
          .appVersion("1.2.3")
          .build()
      }.isSuccess,
    ).isEqualTo(true)

    assertThat(
      runCatching {
        BKTConfig
          .builder()
          .apiKey("api-key")
          .apiEndpoint("https://example.com")
          .featureTag("")
          .appVersion("1.2.3")
          .build()
      }.isSuccess,
    ).isEqualTo(true)
  }

  @Test
  fun eventsFlushInterval() {
    val actual =
      BKTConfig
        .builder()
        .apiKey("api-key")
        .apiEndpoint("https://example.com")
        .featureTag("feature-tag")
        .eventsFlushInterval(70_000)
        .appVersion("1.2.3")
        .build()

    assertThat(actual.eventsFlushInterval).isEqualTo(70_000)
  }

  @Test
  fun `eventsFlushInterval - sooner than min value`() {
    val actual =
      BKTConfig
        .builder()
        .apiKey("api-key")
        .apiEndpoint("https://example.com")
        .featureTag("feature-tag")
        .eventsFlushInterval(10)
        .appVersion("1.2.3")
        .build()

    assertThat(actual.eventsFlushInterval).isEqualTo(MINIMUM_FLUSH_INTERVAL_MILLIS)
  }

  @Test
  fun pollingInterval() {
    val actual =
      BKTConfig
        .builder()
        .apiKey("api-key")
        .apiEndpoint("https://example.com")
        .featureTag("feature-tag")
        .pollingInterval(70_000)
        .appVersion("1.2.3")
        .build()

    assertThat(actual.pollingInterval).isEqualTo(70_000)
  }

  @Test
  fun `pollingInterval - sooner than min value`() {
    val actual =
      BKTConfig
        .builder()
        .apiKey("api-key")
        .apiEndpoint("https://example.com")
        .featureTag("feature-tag")
        .pollingInterval(10)
        .appVersion("1.2.3")
        .build()

    assertThat(actual.pollingInterval).isEqualTo(MINIMUM_POLLING_INTERVAL_MILLIS)
  }

  @Test
  fun backgroundPollingInterval() {
    val actual =
      BKTConfig
        .builder()
        .apiKey("api-key")
        .apiEndpoint("https://example.com")
        .featureTag("feature-tag")
        .backgroundPollingInterval(1_300_000)
        .appVersion("1.2.3")
        .build()

    assertThat(actual.backgroundPollingInterval)
      .isEqualTo(1_300_000)
  }

  @Test
  fun `backgroundPollingInterval - sooner than min value`() {
    val actual =
      BKTConfig
        .builder()
        .apiKey("api-key")
        .apiEndpoint("https://example.com")
        .featureTag("feature-tag")
        .backgroundPollingInterval(10)
        .appVersion("1.2.3")
        .build()

    assertThat(actual.backgroundPollingInterval)
      .isEqualTo(MINIMUM_BACKGROUND_POLLING_INTERVAL_MILLIS)
  }

  @Test
  fun `logger - can be null`() {
    val actual =
      BKTConfig
        .builder()
        .apiKey("api-key")
        .apiEndpoint("https://example.com")
        .featureTag("feature-tag")
        .logger(null)
        .appVersion("1.2.3")
        .build()

    assertThat(actual).isEqualTo(
      BKTConfig(
        apiKey = "api-key",
        apiEndpoint = "https://example.com",
        featureTag = "feature-tag",
        eventsFlushInterval = DEFAULT_FLUSH_INTERVAL_MILLIS,
        eventsMaxBatchQueueCount = DEFAULT_MAX_QUEUE_SIZE,
        pollingInterval = DEFAULT_POLLING_INTERVAL_MILLIS,
        backgroundPollingInterval = DEFAULT_BACKGROUND_POLLING_INTERVAL_MILLIS,
        appVersion = "1.2.3",
        logger = null,
      ),
    )
  }

  @Test
  fun `appVersion - unset`() {
    val error =
      assertThrows(BKTException.IllegalArgumentException::class.java) {
        BKTConfig
          .builder()
          .apiKey("api-key")
          .apiEndpoint("https://example.com")
          .featureTag("feature-tag")
          .build()
      }

    assertThat(error).hasMessageThat().isEqualTo("appVersion is required")
  }

  @Test
  fun `appVersion - empty`() {
    val error =
      assertThrows(BKTException.IllegalArgumentException::class.java) {
        BKTConfig
          .builder()
          .apiKey("api-key")
          .apiEndpoint("https://example.com")
          .featureTag("feature-tag")
          .appVersion("")
          .build()
      }

    assertThat(error).hasMessageThat().isEqualTo("appVersion is required")
  }
}
