package io.bucketeer.sdk.android.internal.event

import io.bucketeer.sdk.android.BKTException

sealed class SendEventsResult {
  data class Success(val sent: Boolean) : SendEventsResult()
  data class Failure(val error: BKTException) : SendEventsResult()
}
