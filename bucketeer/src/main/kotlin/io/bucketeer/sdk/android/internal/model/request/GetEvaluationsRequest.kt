package io.bucketeer.sdk.android.internal.model.request

import com.squareup.moshi.JsonClass
import io.bucketeer.sdk.android.internal.model.SourceId
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.remote.UserEvaluationCondition

@JsonClass(generateAdapter = true)
internal data class GetEvaluationsRequest(
  val tag: String,
  val user: User,
  val userEvaluationsId: String,
  val sourceId: SourceId,
  val userEvaluationCondition: UserEvaluationCondition,
  val sdkVersion: String,
)
