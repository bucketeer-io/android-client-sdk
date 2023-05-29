package io.bucketeer.sdk.android

enum class ReasonType {
  TARGET,
  RULE,
  DEFAULT,
  CLIENT,
  OFF_VARIATION,
  PREREQUISITE,

  ;

  companion object {
    fun from(value: String): ReasonType = values().firstOrNull { it.name == value } ?: DEFAULT
  }
}
