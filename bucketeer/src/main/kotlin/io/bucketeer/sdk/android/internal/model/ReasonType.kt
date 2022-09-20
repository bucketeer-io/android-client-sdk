package io.bucketeer.sdk.android.internal.model

enum class ReasonType(val value: Int) {
  TARGET(0),
  RULE(1),
  DEFAULT(3),
  CLIENT(4),
  OFF_VARIATION(5),
  PREREQUISITE(6),

  ;

  companion object {
    fun from(value: Int): ReasonType = values().first { it.value == value }
  }
}
