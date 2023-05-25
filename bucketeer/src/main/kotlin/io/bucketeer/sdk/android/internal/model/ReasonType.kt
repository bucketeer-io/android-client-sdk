package io.bucketeer.sdk.android.internal.model

enum class ReasonType {
  TARGET,
  RULE,
  DEFAULT,
  CLIENT,
  OFF_VARIATION,
  PREREQUISITE,

  ;

  companion object {
    fun from(value: String): ReasonType = values().first { it.name == value }
  }
}
