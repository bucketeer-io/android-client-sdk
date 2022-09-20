package io.bucketeer.sdk.android.internal.remote

import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsResponse

sealed class RegisterEventsResult {
  data class Success(
    val value: RegisterEventsResponse,
  ) : RegisterEventsResult()

  data class Failure(
    val error: BKTException,
  ) : RegisterEventsResult()
}
