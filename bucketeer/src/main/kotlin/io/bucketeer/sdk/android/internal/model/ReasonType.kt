package io.bucketeer.sdk.android.internal.model

enum class ReasonType(val value: String) {
  TARGET("TARGET"),
  RULE("RULE"),
  DEFAULT("DEFAULT"),
  CLIENT("CLIENT"),
  OFF_VARIATION("OFF_VARIATION"),
  PREREQUISITE("PREREQUISITE"),

  ;

  companion object {
    fun from(value: String): ReasonType = values().first { it.value == value }
  }
}
