package io.bucketeer.sdk.android

import android.util.Log
import io.bucketeer.sdk.android.internal.model.SourceId
import io.bucketeer.sdk.android.internal.util.require
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val MINIMUM_FLUSH_INTERVAL_MILLIS: Long = 60_000 // 60 seconds
internal const val DEFAULT_FLUSH_INTERVAL_MILLIS: Long = 60_000 // 60 seconds
internal const val DEFAULT_MAX_QUEUE_SIZE: Int = 50
internal const val MINIMUM_POLLING_INTERVAL_MILLIS: Long = 60_000 // 60 seconds
internal const val DEFAULT_POLLING_INTERVAL_MILLIS: Long = 600_000 // 10 minutes
internal const val MINIMUM_BACKGROUND_POLLING_INTERVAL_MILLIS: Long = 1_200_000 // 20 minutes
internal const val DEFAULT_BACKGROUND_POLLING_INTERVAL_MILLIS: Long = 3_600_000 // 1 hour
internal const val DEFAULT_WRAPPER_SDK_VERSION = "0.0.1"

// hide internal constructor from copy() method
@ConsistentCopyVisibility
data class BKTConfig internal constructor(
  val apiKey: String,
  val apiEndpoint: String,
  val featureTag: String,
  val eventsFlushInterval: Long,
  val eventsMaxBatchQueueCount: Int,
  val pollingInterval: Long,
  val backgroundPollingInterval: Long,
  val appVersion: String,
  val logger: BKTLogger?,
  val sourceIdValue: Int,
  val sdkVersion: String,
) {
  companion object {
    fun builder(): Builder = Builder()
  }

  // SourceID is internal and its not exposed to the public API.
  internal val sourceId: SourceId = SourceId.from(sourceIdValue)

  class Builder internal constructor() {
    private var apiKey: String? = null
    private var apiEndpoint: String? = null
    private var featureTag: String? = null
    private var eventsFlushInterval: Long = DEFAULT_FLUSH_INTERVAL_MILLIS
    private var eventsMaxQueueSize: Int = DEFAULT_MAX_QUEUE_SIZE
    private var pollingInterval: Long = DEFAULT_POLLING_INTERVAL_MILLIS
    private var backgroundPollingInterval: Long = DEFAULT_BACKGROUND_POLLING_INTERVAL_MILLIS
    private var appVersion: String? = null
    private var logger: BKTLogger? = DefaultLogger()
    private var wrapperSdkVersion: String? = null
    private var wrapperSdkSourceId: Int? = null

    fun apiKey(apiKey: String): Builder {
      this.apiKey = apiKey
      return this
    }

    fun apiEndpoint(apiEndpoint: String): Builder {
      this.apiEndpoint = apiEndpoint
      return this
    }

    fun featureTag(featureTag: String): Builder {
      this.featureTag = featureTag
      return this
    }

    fun eventsFlushInterval(intervalMillis: Long): Builder {
      this.eventsFlushInterval = intervalMillis
      return this
    }

    fun eventsMaxQueueSize(maxQueueSize: Int): Builder {
      this.eventsMaxQueueSize = maxQueueSize
      return this
    }

    fun pollingInterval(intervalMillis: Long): Builder {
      this.pollingInterval = intervalMillis
      return this
    }

    fun backgroundPollingInterval(intervalMillis: Long): Builder {
      this.backgroundPollingInterval = intervalMillis
      return this
    }

    fun appVersion(version: String): Builder {
      this.appVersion = version
      return this
    }

    fun logger(logger: BKTLogger?): Builder {
      this.logger = logger
      return this
    }

    // Sets the SDK sourceID explicitly.
    // IMPORTANT: This option is intended for internal use only.
    // It should NOT be set by developers directly integrating this SDK.
    // Use this option ONLY when another SDK acts as a proxy and wraps this native SDK.
    // In such cases, set this value to the sourceID of the proxy SDK.
    // The sourceID is used to identify the origin of the request.
    fun wrapperSdkSourceId(sourceId: Int): Builder {
      this.wrapperSdkSourceId = sourceId
      return this
    }

    // Sets the SDK version explicitly.
    // IMPORTANT: This option is intended for internal use only.
    // It should NOT be set by developers directly integrating this SDK.
    // Use this option ONLY when another SDK acts as a proxy and wraps this native SDK.
    // In such cases, set this value to the version of the proxy SDK.
    fun wrapperSdkVersion(version: String): Builder {
      this.wrapperSdkVersion = version
      return this
    }

    private fun logWarning(messageCreator: (() -> String?)? = null) {
      // LoggerHolder.log() is not available here, so we use the logger directly.
      logger?.log(
        Log.WARN,
        messageCreator,
        null,
      )
    }

    fun build(): BKTConfig {
      require(!this.apiKey.isNullOrEmpty()) { "apiKey is required" }
      require(this.apiEndpoint?.toHttpUrlOrNull() != null) { "apiEndpoint is invalid" }
      require(!this.appVersion.isNullOrEmpty()) { "appVersion is required" }

      if (this.pollingInterval < MINIMUM_POLLING_INTERVAL_MILLIS) {
        logWarning { "pollingInterval: $pollingInterval is set but must be above $MINIMUM_POLLING_INTERVAL_MILLIS" }
        this.pollingInterval = MINIMUM_POLLING_INTERVAL_MILLIS
      }

      if (this.backgroundPollingInterval < MINIMUM_BACKGROUND_POLLING_INTERVAL_MILLIS) {
        logWarning {
          "backgroundPollingInterval: $backgroundPollingInterval is set but must be above " +
            "$MINIMUM_BACKGROUND_POLLING_INTERVAL_MILLIS"
        }
        this.backgroundPollingInterval = MINIMUM_BACKGROUND_POLLING_INTERVAL_MILLIS
      }

      if (this.eventsFlushInterval < MINIMUM_FLUSH_INTERVAL_MILLIS) {
        logWarning { "eventsFlushInterval: $eventsFlushInterval is set but must be above $MINIMUM_FLUSH_INTERVAL_MILLIS" }
        this.eventsFlushInterval = DEFAULT_FLUSH_INTERVAL_MILLIS
      }

      val wrapperSdkSourceIdInternal = this.wrapperSdkSourceId?.let { SourceId.from(it) }
      val (sourceId, sdkVersion) =
        resolveSourceIdAndSdkVersion(
          wrapperSdkSourceId = wrapperSdkSourceIdInternal,
          wrapperSdkVersion = this.wrapperSdkVersion,
        )

      wrapperSdkSourceIdInternal?.let {
        // The input wrapperSdkSourceId is different from the resolved sourceId.
        // This means that the wrapper SDK is not supported.
        if (wrapperSdkSourceIdInternal != sourceId) {
          logWarning { "wrapperSdkSourceId: `$wrapperSdkSourceIdInternal` is set, but it’s not supported." }
        }
      }

      return BKTConfig(
        apiKey = this.apiKey!!,
        apiEndpoint = this.apiEndpoint!!,
        featureTag = this.featureTag ?: "",
        eventsFlushInterval = this.eventsFlushInterval,
        eventsMaxBatchQueueCount = this.eventsMaxQueueSize,
        pollingInterval = this.pollingInterval,
        backgroundPollingInterval = this.backgroundPollingInterval,
        appVersion = this.appVersion!!,
        logger = this.logger,
        sourceIdValue = sourceId.value,
        sdkVersion = sdkVersion,
      )
    }
  }
}

internal fun resolveSourceIdAndSdkVersion(
  wrapperSdkSourceId: SourceId?,
  wrapperSdkVersion: String?,
): Pair<SourceId, String> {
  val sourceId = resolveSdkSourceId(wrapperSdkSourceId ?: SourceId.ANDROID)
  val sdkVersion = resolveSdkVersion(sourceId, wrapperSdkVersion)
  return Pair(sourceId, sdkVersion)
}

private fun resolveSdkSourceId(sourceId: SourceId): SourceId {
  val availableWrapperSDKs =
    setOf(
      SourceId.FLUTTER,
      SourceId.OPEN_FEATURE_KOTLIN,
    )
  return if (sourceId in availableWrapperSDKs) {
    sourceId
  } else {
    SourceId.ANDROID
  }
}

private fun resolveSdkVersion(
  sourceId: SourceId,
  version: String?,
): String =
  if (sourceId != SourceId.ANDROID) {
    if (!version.isNullOrBlank()) version else DEFAULT_WRAPPER_SDK_VERSION
  } else {
    BuildConfig.SDK_VERSION
  }
