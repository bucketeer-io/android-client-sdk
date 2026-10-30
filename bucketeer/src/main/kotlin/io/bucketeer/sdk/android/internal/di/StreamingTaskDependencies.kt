package io.bucketeer.sdk.android.internal.di

import io.bucketeer.sdk.android.internal.scheduler.EvaluationForegroundTask
import io.bucketeer.sdk.android.internal.scheduler.StreamingFallbackTask
import io.bucketeer.sdk.android.internal.streaming.EventSource
import io.bucketeer.sdk.android.internal.streaming.ExecutorStreamScheduler
import io.bucketeer.sdk.android.internal.streaming.OkHttpEventSource
import io.bucketeer.sdk.android.internal.streaming.StreamBackoff
import io.bucketeer.sdk.android.internal.streaming.StreamScheduler
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService

/**
 * StreamingTask's own parts: the executor it runs on, its timers, a new event source per
 * connection, the polling fallback and the reconnect backoff. Not to be confused with Component
 * (the per-client services every task reads). Owns the executor, so the task, its timers, its
 * event sources and its fallback can't end up on different threads.
 */
internal interface StreamingTaskDependencies {
  val executor: Executor
  val scheduler: StreamScheduler

  fun makeEventSource(): EventSource

  fun makeFallbackTask(): StreamingFallbackTask

  fun makeBackoff(): StreamBackoff
}

internal class StreamingTaskDependenciesImpl(
  private val component: Component,
  override val executor: ScheduledExecutorService,
) : StreamingTaskDependencies {
  override val scheduler: StreamScheduler by lazy { ExecutorStreamScheduler(executor) }

  override fun makeEventSource(): EventSource = OkHttpEventSource(component.streamHttpClient, executor)

  override fun makeFallbackTask(): StreamingFallbackTask = EvaluationForegroundTask(component, executor)

  override fun makeBackoff(): StreamBackoff = StreamBackoff()
}
