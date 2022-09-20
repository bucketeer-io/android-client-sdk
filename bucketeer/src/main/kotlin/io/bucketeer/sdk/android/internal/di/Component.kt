package io.bucketeer.sdk.android.internal.di

import android.content.Context
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.internal.evaluation.EvaluationInteractor
import io.bucketeer.sdk.android.internal.event.EventInteractor
import io.bucketeer.sdk.android.internal.user.UserHolder

internal interface Component {
  val context: Context
  val config: BKTConfig
  val userHolder: UserHolder
  val evaluationInteractor: EvaluationInteractor
  val eventInteractor: EventInteractor
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
      evaluationDao = dataModule.evaluationDao,
      sharedPreferences = dataModule.sharedPreferences,
    )
  }

  override val eventInteractor: EventInteractor by lazy {
    interactorModule.eventInteractor(
      eventsMaxBatchQueueCount = dataModule.config.eventsMaxBatchQueueCount,
      apiClient = dataModule.apiClient,
      eventDao = dataModule.eventDao,
      clock = dataModule.clock,
      idGenerator = dataModule.idGenerator,
    )
  }
}
