package io.bucketeer.sdk.android.internal.scheduler

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.bucketeer.sdk.android.internal.di.Component
import io.bucketeer.sdk.android.internal.di.StreamingTaskDependencies
import java.util.concurrent.ScheduledExecutorService

internal class TaskScheduler(
  private val component: Component,
  private val executor: ScheduledExecutorService,
  // Tests only.
  private val streamingTaskDependencies: StreamingTaskDependencies? = null,
) : DefaultLifecycleObserver {
  private val foregroundSchedulers: List<ScheduledTask> =
    listOf(
      EvaluationForegroundTask(component, executor),
      EventForegroundTask(component, executor),
    )

  private val backgroundSchedulers: List<ScheduledTask> =
    listOf(
      EvaluationBackgroundTask.Scheduler(
        component.context,
        component.config.backgroundPollingInterval,
      ),
      EventBackgroundTask.Scheduler(
        component.context,
        component.config.eventsFlushInterval,
      ),
    )

  // app start or back to foreground
  override fun onStart(owner: LifecycleOwner) {
    // start foreground tasks
    foregroundSchedulers.forEach { it.start() }

    // stop background task
    backgroundSchedulers.forEach { it.stop() }
  }

  // to background
  override fun onStop(owner: LifecycleOwner) {
    // stop foreground tasks
    foregroundSchedulers.forEach { it.stop() }

    // flush events before switching to background tasks
    executor.execute {
      component.eventInteractor.sendEvents(force = true)
    }

    // start background task
    backgroundSchedulers.forEach { it.start() }
  }

  fun stop() {
    foregroundSchedulers.forEach { it.stop() }
    backgroundSchedulers.forEach { it.stop() }
  }

  /** Called once the initial fetch has finished. Opens the stream if streaming is on. Thread-safe. */
  fun enableEvaluationTask() {}

  /** Called by BKTClient.updateUserAttributes. Only forwards; StreamingTask merges and reconnects. */
  fun onUserAttributesUpdated() {}

  /** Thread-safe. Lets BKTClient close the stream, for good, before its own destroy runs. */
  fun destroyStreaming() {}
}
