package io.bucketeer.sdk.android.internal.model.request

import com.squareup.moshi.JsonClass
import io.bucketeer.sdk.android.internal.model.SourceID
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.remote.UserEvaluationCondition

@JsonClass(generateAdapter = true)
data class GetEvaluationsRequest(
  val tag: String,
  val user: User,
  val userEvaluationsId: String,
  val sourceId: SourceID = SourceID.ANDROID,
  val userEvaluationCondition: UserEvaluationCondition,
)
