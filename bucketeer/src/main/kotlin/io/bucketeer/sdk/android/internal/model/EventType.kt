package io.bucketeer.sdk.android.internal.model

enum class EventType(val value: Int) {
  GOAL(1),
  GOAL_BATCH(2), // not used in Client SDK
  EVALUATION(3),
  METRICS(4),

  ;

  companion object {
    fun from(value: Int): EventType = values().first { it.value == value }
  }
}
