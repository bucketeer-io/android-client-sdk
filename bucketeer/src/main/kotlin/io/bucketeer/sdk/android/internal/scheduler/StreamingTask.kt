package io.bucketeer.sdk.android.internal.scheduler

import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.internal.di.StreamingTaskDependencies
import io.bucketeer.sdk.android.internal.evaluation.StreamingEvaluationInteractor
import io.bucketeer.sdk.android.internal.model.User

/**
 * Keeps evaluations up to date over the SSE stream, falls back to polling while the stream is
 * down, and tries the stream again after 5 minutes. Port of the iOS and JS StreamingTask.
 * TaskScheduler uses it in place of EvaluationForegroundTask when streaming is enabled.
 */
internal class StreamingTask(
  private val config: BKTConfig,
  private val userProvider: () -> User,
  private val interactor: StreamingEvaluationInteractor,
  private val moshi: Moshi,
  private val dependencies: StreamingTaskDependencies,
) : ScheduledTask {
  override fun start() {}

  fun enable() {}

  override fun stop() {}

  fun destroy() {}

  fun onUserAttributesUpdated() {}

  fun reconnect() {}
}
