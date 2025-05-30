package io.bucketeer.sdk.android

import android.content.Context
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.database.OpenHelperCallback
import io.bucketeer.sdk.android.internal.model.SourceId
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

internal fun deleteDatabase(context: Context) {
  context.deleteDatabase(OpenHelperCallback.FILE_NAME)
}

internal fun deleteSharedPreferences(context: Context) {
  context
    .getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE)
    .edit()
    .clear()
    .commit()
}

internal inline fun <reified T> MockWebServer.enqueueResponse(
  moshi: Moshi,
  responseCode: Int,
  response: T,
) {
  enqueue(
    MockResponse()
      .setResponseCode(responseCode)
      .setBody(
        moshi.adapter(T::class.java).toJson(response),
      ),
  )
}

/**
 * Create [BKTConfig] for testing.
 * This bypasses validations in [BKTConfig.Builder]
 */
internal fun createTestBKTConfig(
  apiKey: String,
  apiEndpoint: String,
  featureTag: String,
  eventsFlushInterval: Long = DEFAULT_FLUSH_INTERVAL_MILLIS,
  eventsMaxBatchQueueCount: Int = DEFAULT_MAX_QUEUE_SIZE,
  pollingInterval: Long = DEFAULT_POLLING_INTERVAL_MILLIS,
  backgroundPollingInterval: Long = DEFAULT_BACKGROUND_POLLING_INTERVAL_MILLIS,
  appVersion: String = "1.2.3",
  logger: BKTLogger? = null,
): BKTConfig =
  BKTConfig(
    apiKey = apiKey,
    apiEndpoint = apiEndpoint,
    featureTag = featureTag,
    eventsFlushInterval = eventsFlushInterval,
    eventsMaxBatchQueueCount = eventsMaxBatchQueueCount,
    pollingInterval = pollingInterval,
    backgroundPollingInterval = backgroundPollingInterval,
    appVersion = appVersion,
    logger = logger,
    sourceIdValue = SourceId.ANDROID.value,
    sdkVersion = BuildConfig.SDK_VERSION,
  )
