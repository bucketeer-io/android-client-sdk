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
    fun from(value: String): ReasonType {
      return try {
        values().first { it.name == value }
      } catch (ex: NoSuchElementException) {
        // FALLBACK
        DEFAULT
      }
    }
  }
}
