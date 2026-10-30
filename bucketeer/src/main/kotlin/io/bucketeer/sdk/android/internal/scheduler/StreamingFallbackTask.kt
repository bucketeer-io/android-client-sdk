package io.bucketeer.sdk.android.internal.scheduler

/** The polling StreamingTask falls back to while the stream is down. */
internal interface StreamingFallbackTask {
  /** [immediately]: fetch right away instead of after the first polling interval. */
  fun start(immediately: Boolean)

  fun stop()
}
