package io.bucketeer.sdk.android

import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.internal.model.SourceID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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
    val expected =
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
        sourceIdValue = SourceID.ANDROID.value,
        sdkVersion = BuildConfig.SDK_VERSION,
      )
    assertThat(actual).isEqualTo(
      expected,
    )
    assertThat(actual.sourceId).isEqualTo(SourceID.ANDROID)
  }

  @Test
  fun buildForWrapperSDK() {
    val actual =
      BKTConfig
        .builder()
        .apiKey("api-key")
        .apiEndpoint("https://example.com")
        .featureTag("feature-tag")
        .appVersion("1.2.3")
        .wrapperSdkSourceId(SourceID.FLUTTER.value)
        .wrapperSdkVersion("0.0.1-beta-op-ft-kt")
        .build()
    val expected =
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
        sourceIdValue = SourceID.FLUTTER.value,
        sdkVersion = "0.0.1-beta-op-ft-kt",
      )
    assertThat(actual).isEqualTo(
      expected,
    )
    assertThat(actual.sourceId).isEqualTo(SourceID.FLUTTER)
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
        sourceIdValue = SourceID.ANDROID.value,
        sdkVersion = BuildConfig.SDK_VERSION,
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

  @Test
  fun `wrapperSdkSourceId - available`() {
    val availableSourceId =
      listOf(
        SourceID.FLUTTER,
        SourceID.OPEN_FEATURE_KOTLIN,
      )
    for (sourceId in availableSourceId) {
      val actual =
        BKTConfig
          .builder()
          .apiKey("api-key")
          .apiEndpoint("https://example.com")
          .featureTag("feature-tag")
          .appVersion("1.2.3")
          .wrapperSdkSourceId(sourceId.value)
          .wrapperSdkVersion("0.0.1")
          .build()
      val expected =
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
          sourceIdValue = sourceId.value,
          sdkVersion = "0.0.1",
        )
      assertThat(actual).isEqualTo(
        expected,
      )
      assertThat(actual.sourceId).isEqualTo(sourceId)
    }
  }

  @Test
  fun `wrapperSdkSourceId - not available`() {
    val listSourceIds =
      SourceID.entries.filter {
        it != SourceID.FLUTTER && it != SourceID.OPEN_FEATURE_KOTLIN
      }
    for (si in listSourceIds) {
      val actual =
        BKTConfig
          .builder()
          .apiKey("api-key")
          .apiEndpoint("https://example.com")
          .featureTag("feature-tag")
          .appVersion("1.2.3")
          .wrapperSdkSourceId(si.value)
          .build()
      val expected =
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
          // no error expected
          // the sourceId will be set to SourceID.ANDROID
          // and the sdkVersion will be set to BuildConfig.SDK_VERSION
          sourceIdValue = SourceID.ANDROID.value,
          sdkVersion = BuildConfig.SDK_VERSION,
        )
      assertThat(actual).isEqualTo(
        expected,
      )
      assertThat(actual.sourceId).isEqualTo(SourceID.ANDROID)
    }
  }

  // Test cases for resolveSourceIdAndSdkVersion function
  @Test
  fun `resolveSourceIdAndSdkVersion should return ANDROID sourceID and SDK_VERSION when no wrapper SDK is provided`() {
    val (sourceId, sdkVersion) =
      resolveSourceIdAndSdkVersion(
        wrapperSdkSourceId = null,
        wrapperSdkVersion = null,
      )

    assertEquals(SourceID.ANDROID, sourceId)
    assertEquals(BuildConfig.SDK_VERSION, sdkVersion)
  }

  @Test
  fun `resolveSourceIdAndSdkVersion should return provided wrapper SDK sourceID and version`() {
    val (sourceId, sdkVersion) =
      resolveSourceIdAndSdkVersion(
        wrapperSdkSourceId = SourceID.FLUTTER,
        wrapperSdkVersion = "1.2.3",
      )

    assertEquals(SourceID.FLUTTER, sourceId)
    assertEquals("1.2.3", sdkVersion)
  }

  @Test
  fun `resolveSourceIdAndSdkVersion should return default wrapper SDK version when version is null or blank`() {
    val (sourceId, sdkVersion) =
      resolveSourceIdAndSdkVersion(
        wrapperSdkSourceId = SourceID.FLUTTER,
        wrapperSdkVersion = null,
      )

    assertEquals(SourceID.FLUTTER, sourceId)
    assertEquals(DEFAULT_WRAPPER_SDK_VERSION, sdkVersion)
  }

  @Test
  fun `resolveSourceIdAndSdkVersion should return ANDROID sourceID when an unsupported sourceID is provided`() {
    val (sourceId, sdkVersion) =
      resolveSourceIdAndSdkVersion(
        wrapperSdkSourceId = SourceID.UNKNOWN,
        wrapperSdkVersion = "1.2.3",
      )

    assertEquals(SourceID.ANDROID, sourceId)
    assertEquals(BuildConfig.SDK_VERSION, sdkVersion)
  }
}
