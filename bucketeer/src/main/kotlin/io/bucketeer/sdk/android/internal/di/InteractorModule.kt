package io.bucketeer.sdk.android.internal.di

import android.os.Handler
import io.bucketeer.sdk.android.internal.Clock
import io.bucketeer.sdk.android.internal.IdGenerator
import io.bucketeer.sdk.android.internal.evaluation.EvaluationInteractor
import io.bucketeer.sdk.android.internal.evaluation.storage.EvaluationStorage
import io.bucketeer.sdk.android.internal.event.EventInteractor
import io.bucketeer.sdk.android.internal.event.db.EventDao
import io.bucketeer.sdk.android.internal.remote.ApiClient

internal class InteractorModule(
  val mainHandler: Handler,
) {
  fun evaluationInteractor(
    apiClient: ApiClient,
    evaluationStorage: EvaluationStorage,
    idGenerator: IdGenerator,
    featureTag: String,
  ): EvaluationInteractor {
    return EvaluationInteractor(
      apiClient = apiClient,
      evaluationStorage = evaluationStorage,
      idGenerator = idGenerator,
      mainHandler = mainHandler,
      featureTag = featureTag,
    )
  }

  fun eventInteractor(
    eventsMaxBatchQueueCount: Int,
    apiClient: ApiClient,
    eventDao: EventDao,
    clock: Clock,
    idGenerator: IdGenerator,
    appVersion: String,
    featureTag: String,
  ): EventInteractor {
    return EventInteractor(
      eventsMaxBatchQueueCount = eventsMaxBatchQueueCount,
      apiClient = apiClient,
      eventDao = eventDao,
      clock = clock,
      idGenerator = idGenerator,
      appVersion = appVersion,
      featureTag = featureTag,
    )
  }
}
