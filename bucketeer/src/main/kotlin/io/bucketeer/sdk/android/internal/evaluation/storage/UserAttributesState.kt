package io.bucketeer.sdk.android.internal.evaluation.storage

data class UserAttributesState(
  val userAttributesUpdated: Boolean,
  // Int is sufficient for version counter in a mobile app session.
  // Even with 1000 updates per second, it would take ~24 days to overflow (2^31 / 1000 / 60 / 60 / 24).
  // In practice, mobile apps are restarted frequently, resetting the counter.
  val version: Int,
)
