package io.bucketeer.sdk.android.internal.remote

import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.User

interface ApiClient {
  fun getEvaluations(
    user: User,
    userEvaluationsId: String,
    timeoutMillis: Long? = null,
  ): GetEvaluationsResult

  fun registerEvents(events: List<Event>): RegisterEventsResult
}
