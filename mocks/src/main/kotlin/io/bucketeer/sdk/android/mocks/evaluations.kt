@file:Suppress("ktlint:filename")

package io.bucketeer.sdk.android.mocks

import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.EventData
import io.bucketeer.sdk.android.internal.model.EventType
import io.bucketeer.sdk.android.internal.model.Reason
import io.bucketeer.sdk.android.internal.model.ReasonType
import io.bucketeer.sdk.android.internal.model.SourceID
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.model.UserEvaluations

val user1Evaluations: UserEvaluations by lazy {
  UserEvaluations(
    id = "17388826713971171773",
    evaluations = listOf(
      evaluation1,
      evaluation2,
    ),
    createdAt = "1690798021",
    forceUpdate = true,
  )
}

val user1EvaluationsForeUpdate: UserEvaluations by lazy {
  UserEvaluations(
    id = "17388826713971171773",
    evaluations = listOf(
      evaluation2,
    ),
    createdAt = "1690798025",
    forceUpdate = true,
  )
}

val user1EvaluationsUpsert: UserEvaluations by lazy {
  UserEvaluations(
    id = "17388826713971171773",
    evaluations = listOf(
      evaluation1,
      evaluation2,
    ),
    createdAt = "1690798025",
    forceUpdate = true,
  )
}

val user2Evaluations: UserEvaluations by lazy {
  UserEvaluations(
    id = "17388826713971171774",
    evaluations = listOf(evaluation3),
    createdAt = "1690799033",
    forceUpdate = true,
  )
}

val evaluation1: Evaluation by lazy {
  Evaluation(
    id = "test-feature-1:9:user id 1",
    featureId = "test-feature-1",
    featureVersion = 9,
    userId = "user id 1",
    variationId = "test-feature-1-variation-A",
    variationName = "test variation name1",
    variationValue = "test variation value1",
    reason = Reason(
      type = ReasonType.DEFAULT,
    ),
  )
}

val evaluation2: Evaluation by lazy {
  Evaluation(
    id = "test-feature-2:9:user id 1",
    featureId = "test-feature-2",
    featureVersion = 9,
    userId = "user id 1",
    variationId = "test-feature-2-variation-A",
    variationName = "test variation name2",
    variationValue = "test variation value2",
    reason = Reason(
      type = ReasonType.DEFAULT,
    ),
  )
}

val evaluation2ForUpdate: Evaluation by lazy {
  Evaluation(
    id = "test-feature-2:9:user id 1",
    featureId = "test-feature-2",
    featureVersion = 10,
    userId = "user id 1",
    variationId = "test-feature-2-variation-A",
    variationName = "test variation name2 update",
    variationValue = "test variation value2 update",
    reason = Reason(
      type = ReasonType.DEFAULT,
    ),
  )
}

val evaluation3: Evaluation by lazy {
  Evaluation(
    id = "test-feature-1:9:user id 2",
    featureId = "test-feature-3",
    featureVersion = 9,
    userId = "user id 2",
    variationId = "test-feature-1-variation-A",
    variationName = "test variation name2",
    variationValue = "test variation value2",
    reason = Reason(
      type = ReasonType.DEFAULT,
    ),
  )
}

val evaluationEvent: Event by lazy {
  Event(
    "event-id",
    EventData.EvaluationEvent(
      featureId = "test-feature-3",
      featureVersion = 9,
      userId = "user-id",
      variationId = "test-feature-1-variation-A",
      reason = Reason(
        type = ReasonType.DEFAULT,
      ),
      tag = "android",
      timestamp = 10,
      sourceId = SourceID.ANDROID,
      user = User("user-id"),
    ),
    EventType.EVALUATION,
  )
}
