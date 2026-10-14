package io.bucketeer.sdk.android.internal

internal object Constants {
  const val PREFERENCES_NAME = "bucketeer"
  const val PREFERENCE_KEY_USER_EVALUATION_ID = "user_evaluations_id"
  const val PREFERENCE_KEY_FEATURE_TAG = "feature_tag"
  const val PREFERENCE_KEY_EVALUATED_AT = "evaluated_at"
  const val PREFERENCE_KEY_USER_ATTRIBUTES_UPDATED = "user_attributes_updated"

  object Streaming {
    // Longer than the backend heartbeat (25s), so a healthy stream never trips it.
    const val WATCHDOG_TIMEOUT_MILLIS = 70_000L

    // How long the connection may stay broken before giving up, so the caller can fall back to polling.
    const val UNHEALTHY_FALLBACK_TIMEOUT_MILLIS = 120_000L

    // A connection open this long counts as stable: the next drop backs off from the start.
    const val RESET_INTERVAL_MILLIS = 60_000L

    // OkHttp read timeout. Longer than the watchdog, so our own timer fires first.
    const val READ_TIMEOUT_MILLIS = 90_000L

    // Appended to apiEndpoint, the same way ApiClientImpl builds the get_evaluations URL.
    const val STREAM_EVALUATIONS_PATH = "v1/gateway/stream_evaluations"

    // After a non-terminal give-up, how long to poll before trying the stream again.
    const val RECOVERY_INTERVAL_MILLIS = 300_000L

    // Merges a burst of user attribute updates into one stream reconnect.
    const val RECONNECT_DEBOUNCE_MILLIS = 200L
  }
}
