package io.bucketeer.sdk.android.internal.di

import android.content.SharedPreferences
import io.bucketeer.sdk.android.internal.Clock
import io.bucketeer.sdk.android.internal.IdGenerator
import io.bucketeer.sdk.android.internal.evaluation.EvaluationInteractor
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationDao
import io.bucketeer.sdk.android.internal.event.EventInteractor
import io.bucketeer.sdk.android.internal.event.db.EventDao
import io.bucketeer.sdk.android.internal.remote.ApiClient

internal class InteractorModule {
  fun evaluationInteractor(
    apiClient: ApiClient,
    evaluationDao: EvaluationDao,
    sharedPreferences: SharedPreferences,
  ): EvaluationInteractor {
    return EvaluationInteractor(
      apiClient = apiClient,
      evaluationDao = evaluationDao,
      sharedPrefs = sharedPreferences,
    )
  }

  fun eventInteractor(
    eventsMaxBatchQueueCount: Int,
    apiClient: ApiClient,
    eventDao: EventDao,
    clock: Clock,
    idGenerator: IdGenerator,
  ): EventInteractor {
    return EventInteractor(
      eventsMaxBatchQueueCount = eventsMaxBatchQueueCount,
      apiClient = apiClient,
      eventDao = eventDao,
      clock = clock,
      idGenerator = idGenerator,
    )
  }
}
