package io.bucketeer.sdk.android.internal.di

import android.content.Context
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.internal.evaluation.EvaluationInteractor
import io.bucketeer.sdk.android.internal.evaluation.EvaluationSyncStatusTracker
import io.bucketeer.sdk.android.internal.evaluation.EvaluationSyncStatusTrackerImpl
import io.bucketeer.sdk.android.internal.event.EventInteractor
import io.bucketeer.sdk.android.internal.user.UserHolder

internal interface Component {
  val context: Context
  val config: BKTConfig
  val userHolder: UserHolder
  val evaluationInteractor: EvaluationInteractor
  val eventInteractor: EventInteractor

  // For BKTClient: read the sync status, manage its listeners, init and destroy.
  val evaluationSyncStatusTracker: EvaluationSyncStatusTracker

  fun destroy()
}

internal class ComponentImpl(
  val dataModule: DataModule,
  val interactorModule: InteractorModule,
) : Component {
  override val context: Context
    get() = dataModule.application

  override val config: BKTConfig
    get() = dataModule.config

  override val userHolder: UserHolder
    get() = dataModule.userHolder

  override val evaluationInteractor: EvaluationInteractor by lazy {
    interactorModule.evaluationInteractor(
      apiClient = dataModule.apiClient,
      evaluationStorage = dataModule.evaluationStorage,
      idGenerator = dataModule.idGenerator,
      featureTag = dataModule.config.featureTag,
      syncReporter = syncStatusTrackerImpl,
    )
  }

  // One object, given out to each caller as only the interface it uses.
  private val syncStatusTrackerImpl: EvaluationSyncStatusTrackerImpl by lazy {
    interactorModule.evaluationSyncStatusTracker(
      clock = dataModule.clock,
      idGenerator = dataModule.idGenerator,
    )
  }

  override val evaluationSyncStatusTracker: EvaluationSyncStatusTracker
    get() = syncStatusTrackerImpl

  override val eventInteractor: EventInteractor by lazy {
    interactorModule.eventInteractor(
      eventsMaxBatchQueueCount = dataModule.config.eventsMaxBatchQueueCount,
      apiClient = dataModule.apiClient,
      eventSQLDao = dataModule.eventSQLDao,
      clock = dataModule.clock,
      idGenerator = dataModule.idGenerator,
      appVersion = dataModule.config.appVersion,
      featureTag = dataModule.config.featureTag,
      sourceId = dataModule.config.sourceId,
      sdkVersion = dataModule.config.sdkVersion,
    )
  }

  override fun destroy() {
    // Clear all listeners to prevent memory leaks by circular references
    evaluationInteractor.clearUpdateListeners()
    evaluationInteractor.setErrorListener(null)
    eventInteractor.setEventUpdateListener(null)
    dataModule.destroy()
  }
}
