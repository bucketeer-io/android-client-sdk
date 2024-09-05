@file:Suppress("ktlint:standard:filename")

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
    evaluations =
      listOf(
        evaluation1,
        evaluation2,
      ),
    createdAt = "1690798021",
    forceUpdate = true,
  )
}

val user1EvaluationsForceUpdate: UserEvaluations by lazy {
  UserEvaluations(
    id = "17388826713971171773",
    evaluations =
      listOf(
        evaluation2,
      ),
    createdAt = "1690798025",
    forceUpdate = true,
  )
}

val user1EvaluationsUpsert: UserEvaluations by lazy {
  UserEvaluations(
    id = "17388826713971171773",
    evaluations =
      listOf(
        evaluation1,
        evaluation2ForUpdate,
        evaluationForTestInsert,
      ),
    createdAt = "16907999999",
    forceUpdate = false,
    archivedFeatureIds = listOf("test-feature-1"),
  )
}

val userEvaluationsForTestGetDetailsByVariationType: UserEvaluations by lazy {
  UserEvaluations(
    id = "17388826713971171773",
    evaluations =
      listOf(
        stringEvaluation,
        doubleEvaluation,
        booleanEvaluation,
        intValueEvaluation,
        jsonEvaluation,
      ),
    createdAt = "1690798021",
    forceUpdate = true,
  )
}

val user2Evaluations: UserEvaluations by lazy {
  UserEvaluations(
    id = "17388826713971171774",
    evaluations = listOf(evaluation4),
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
    reason =
      Reason(
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
    reason =
      Reason(
        type = ReasonType.DEFAULT,
      ),
  )
}

val stringEvaluation: Evaluation by lazy {
  Evaluation(
    id = "test-feature-1:9:stringEvaluation",
    featureId = "stringEvaluation",
    featureVersion = 9,
    userId = "user id 1",
    variationId = "test-feature-1-variation-A",
    variationName = "test variation name1",
    variationValue = "test variation value1",
    reason =
      Reason(
        type = ReasonType.DEFAULT,
      ),
  )
}

val intValueEvaluation: Evaluation by lazy {
  Evaluation(
    id = "test-feature-1:9:intValueEvaluation",
    featureId = "intValueEvaluation",
    featureVersion = 9,
    userId = "user id 1",
    variationId = "test-feature-1-variation-A",
    variationName = "test variation name1",
    variationValue = "1",
    reason =
      Reason(
        type = ReasonType.DEFAULT,
      ),
  )
}

val booleanEvaluation: Evaluation by lazy {
  Evaluation(
    id = "test-feature-1:9:booleanEvaluation",
    featureId = "booleanEvaluation",
    featureVersion = 9,
    userId = "user id 1",
    variationId = "test-feature-1-variation-A",
    variationName = "test variation name1",
    variationValue = "true",
    reason =
      Reason(
        type = ReasonType.DEFAULT,
      ),
  )
}

val doubleEvaluation: Evaluation by lazy {
  Evaluation(
    id = "test-feature-1:9:doubleEvaluation",
    featureId = "doubleEvaluation",
    featureVersion = 9,
    userId = "user id 1",
    variationId = "test-feature-1-variation-A",
    variationName = "test variation name1",
    variationValue = "2.0",
    reason =
      Reason(
        type = ReasonType.DEFAULT,
      ),
  )
}

val jsonEvaluation: Evaluation by lazy {
  Evaluation(
    id = "test-feature-1:9:jsonEvaluation",
    featureId = "jsonEvaluation",
    featureVersion = 9,
    userId = "user id 1",
    variationId = "test-feature-1-variation-A",
    variationName = "test variation name1",
    variationValue = """{ "key": "value-1" }""",
    reason =
      Reason(
        type = ReasonType.DEFAULT,
      ),
  )
}

val evaluation2ForUpdate: Evaluation by lazy {
  evaluation2.copy(
    // the mock data to cover the bug in the link below
    // https://github.com/bucketeer-io/android-client-sdk/pull/88/files#r1333847962
    id = "test-feature-2:10:user id 1",
    featureVersion = 10,
    variationId = "test-feature-2-variation-A-updated",
    variationName = "test variation name2 updated",
    variationValue = "test variation value2 updated",
  )
}

val evaluationForTestInsert: Evaluation by lazy {
  Evaluation(
    id = "test-feature-3:9:user id 1",
    featureId = "test-feature-3",
    featureVersion = 9,
    userId = "user id 1",
    variationId = "test-feature-1-variation-A",
    variationName = "test variation name2",
    variationValue = "test variation value2",
    reason =
      Reason(
        type = ReasonType.DEFAULT,
      ),
  )
}

val evaluation3: Evaluation by lazy {
  Evaluation(
    id = "test-feature-3:9:user id 2",
    featureId = "test-feature-3",
    featureVersion = 9,
    userId = "user id 2",
    variationId = "test-feature-1-variation-A",
    variationName = "test variation name2",
    variationValue = "test variation value2",
    reason =
      Reason(
        type = ReasonType.DEFAULT,
      ),
  )
}

val evaluation4: Evaluation by lazy {
  Evaluation(
    id = "test-feature-4:9:user id 2",
    featureId = "test-feature-4",
    featureVersion = 9,
    userId = "user id 2",
    variationId = "test-feature-1-variation-A",
    variationName = "test variation name2",
    variationValue = "test variation value2",
    reason =
      Reason(
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
      reason =
        Reason(
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
