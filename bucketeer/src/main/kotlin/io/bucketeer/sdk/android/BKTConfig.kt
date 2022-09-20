package io.bucketeer.sdk.android

import io.bucketeer.sdk.android.internal.logw
import io.bucketeer.sdk.android.internal.util.require
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val MINIMUM_FLUSH_INTERVAL_MILLIS: Long = 60_000 // 60 seconds
internal const val DEFAULT_FLUSH_INTERVAL_MILLIS: Long = 60_000 // 60 seconds
internal const val DEFAULT_MAX_QUEUE_SIZE: Int = 50
internal const val MINIMUM_POLLING_INTERVAL_MILLIS: Long = 60_000 // 60 seconds
internal const val DEFAULT_POLLING_INTERVAL_MILLIS: Long = 600_000 // 10 minutes
internal const val MINIMUM_BACKGROUND_POLLING_INTERVAL_MILLIS: Long = 1_200_000 // 20 minutes
internal const val DEFAULT_BACKGROUND_POLLING_INTERVAL_MILLIS: Long = 3_600_000 // 1 hour

data class BKTConfig internal constructor(
  val apiKey: String,
  val endpoint: String,
  val featureTag: String,
  val eventsFlushInterval: Long,
  val eventsMaxBatchQueueCount: Int,
  val pollingInterval: Long,
  val backgroundPollingInterval: Long,
  val logger: BKTLogger?,
) {

  companion object {
    fun builder(): Builder = Builder()
  }

  class Builder internal constructor() {
    private var apiKey: String? = null
    private var endpoint: String? = null
    private var featureTag: String? = null
    private var eventsFlushInterval: Long = DEFAULT_FLUSH_INTERVAL_MILLIS
    private var eventsMaxQueueSize: Int = DEFAULT_MAX_QUEUE_SIZE
    private var pollingInterval: Long = DEFAULT_POLLING_INTERVAL_MILLIS
    private var backgroundPollingInterval: Long = DEFAULT_BACKGROUND_POLLING_INTERVAL_MILLIS
    private var logger: BKTLogger? = DefaultLogger()

    fun apiKey(apiKey: String): Builder {
      this.apiKey = apiKey
      return this
    }

    fun endpoint(endpoint: String): Builder {
      this.endpoint = endpoint
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

    fun logger(logger: BKTLogger?): Builder {
      this.logger = logger
      return this
    }

    fun build(): BKTConfig {
      require(!this.apiKey.isNullOrEmpty()) { "apiKey is required" }
      require(this.endpoint?.toHttpUrlOrNull() != null) { "endpoint is invalid" }
      require(!this.featureTag.isNullOrEmpty()) { "featureTag is required" }

      if (this.pollingInterval < MINIMUM_POLLING_INTERVAL_MILLIS) {
        logw { "pollingInterval: $pollingInterval is set but must be above $MINIMUM_POLLING_INTERVAL_MILLIS" }
        this.pollingInterval = MINIMUM_POLLING_INTERVAL_MILLIS
      }

      if (this.backgroundPollingInterval < MINIMUM_BACKGROUND_POLLING_INTERVAL_MILLIS) {
        logw { "backgroundPollingInterval: $backgroundPollingInterval is set but must be above $MINIMUM_BACKGROUND_POLLING_INTERVAL_MILLIS" }
        this.backgroundPollingInterval = MINIMUM_BACKGROUND_POLLING_INTERVAL_MILLIS
      }

      if (this.eventsFlushInterval < MINIMUM_FLUSH_INTERVAL_MILLIS) {
        logw { "eventsFlushInterval: $eventsFlushInterval is set but must be above $MINIMUM_FLUSH_INTERVAL_MILLIS" }
        this.eventsFlushInterval = DEFAULT_FLUSH_INTERVAL_MILLIS
      }

      return BKTConfig(
        apiKey = this.apiKey!!,
        endpoint = this.endpoint!!,
        featureTag = this.featureTag!!,
        eventsFlushInterval = this.eventsFlushInterval,
        eventsMaxBatchQueueCount = this.eventsMaxQueueSize,
        pollingInterval = this.pollingInterval,
        backgroundPollingInterval = this.backgroundPollingInterval,
        logger = this.logger,
      )
    }
  }
}
