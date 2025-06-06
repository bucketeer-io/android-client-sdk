package io.bucketeer.sdk.android.internal.remote

import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.User

internal interface ApiClient {
  fun getEvaluations(
    user: User,
    userEvaluationsId: String,
    timeoutMillis: Long? = null,
    condition: UserEvaluationCondition,
  ): GetEvaluationsResult

  fun registerEvents(events: List<Event>): RegisterEventsResult
}
