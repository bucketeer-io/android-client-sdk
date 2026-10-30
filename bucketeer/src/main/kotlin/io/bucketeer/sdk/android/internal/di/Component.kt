package io.bucketeer.sdk.android.internal.di

import android.content.Context
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.internal.evaluation.EvaluationInteractor
import io.bucketeer.sdk.android.internal.event.EventInteractor
import io.bucketeer.sdk.android.internal.user.UserHolder
import okhttp3.OkHttpClient

internal interface Component {
  val context: Context
  val config: BKTConfig
  val userHolder: UserHolder
  val evaluationInteractor: EvaluationInteractor
  val eventInteractor: EventInteractor
  val moshi: Moshi

  /** Shared by every stream connection. */
  val streamHttpClient: OkHttpClient

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

  override val moshi: Moshi
    get() = dataModule.moshi

  override val streamHttpClient: OkHttpClient
    get() = dataModule.streamHttpClient

  override val evaluationInteractor: EvaluationInteractor by lazy {
    interactorModule.evaluationInteractor(
      apiClient = dataModule.apiClient,
      evaluationStorage = dataModule.evaluationStorage,
      idGenerator = dataModule.idGenerator,
      featureTag = dataModule.config.featureTag,
    )
  }

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
