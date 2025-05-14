package io.bucketeer.sdk.android.internal.model

internal enum class SourceId(
  val value: Int,
) {
  UNKNOWN(0),
  ANDROID(1),
  IOS(2),
  WEB(3),
  GOAL_BATCH(4),
  GO_SERVER(5),
  NODE_SERVER(6),
  FLUTTER(8),
  REACT(9),
  REACT_NATIVE(10),
  OPEN_FEATURE_KOTLIN(100),
  OPEN_FEATURE_SWIFT(101),
  OPEN_FEATURE_JAVASCRIPT(102),
  OPEN_FEATURE_GO(103),
  OPEN_FEATURE_NODE(104),
  ;

  companion object {
    fun from(value: Int): SourceId = entries.firstOrNull { it.value == value } ?: UNKNOWN
  }
}
