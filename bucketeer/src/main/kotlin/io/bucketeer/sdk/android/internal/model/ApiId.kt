package io.bucketeer.sdk.android.internal.model

enum class ApiId(
  val value: Int,
) {
  UNKNOWN_API(0),
  GET_EVALUATION(1),
  GET_EVALUATIONS(2),
  REGISTER_EVENTS(3),

  ;

  companion object {
    fun from(value: Int): ApiId = values().firstOrNull { it.value == value } ?: UNKNOWN_API
  }
}
