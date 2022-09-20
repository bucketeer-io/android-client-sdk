package io.bucketeer.sdk.android.internal.model

enum class SourceID(val value: Int) {
  UNKNOWN(0),
  ANDROID(1),
  IOS(2),
  WEB(3),
  GOAL_BATCH(4),
  GO_SERVER(5),
  NODE_SERVER(6),

  ;

  companion object {
    fun from(value: Int): SourceID = values().first { it.value == value }
  }
}
