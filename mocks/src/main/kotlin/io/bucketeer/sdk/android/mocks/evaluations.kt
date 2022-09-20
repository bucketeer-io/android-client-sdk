@file:Suppress("ktlint:filename")

package io.bucketeer.sdk.android.mocks

import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.internal.model.Reason
import io.bucketeer.sdk.android.internal.model.ReasonType
import io.bucketeer.sdk.android.internal.model.UserEvaluations
import io.bucketeer.sdk.android.internal.model.Variation

val user1Evaluations: UserEvaluations by lazy {
  UserEvaluations(
    id = "17388826713971171773",
    evaluations = listOf(
      evaluation1,
      evaluation2,
    ),
  )
}

val user2Evaluations: UserEvaluations by lazy {
  UserEvaluations(
    id = "17388826713971171774",
    evaluations = listOf(evaluation3),
  )
}

val evaluation1: Evaluation by lazy {
  Evaluation(
    id = "test-feature-1:9:user id 1",
    feature_id = "test-feature-1",
    feature_version = 9,
    user_id = "user id 1",
    variation_id = "test-feature-1-variation-A",
    variation_value = "test variation value1",
    variation = Variation(
      id = "test-feature-1-variation-A",
      value = "test variation value1",
    ),
    reason = Reason(
      type = ReasonType.DEFAULT,
    ),
  )
}

val evaluation2: Evaluation by lazy {
  Evaluation(
    id = "test-feature-2:9:user id 1",
    feature_id = "test-feature-2",
    feature_version = 9,
    user_id = "user id 1",
    variation_id = "test-feature-2-variation-A",
    variation_value = "test variation value2",
    variation = Variation(
      id = "test-feature-2-variation-A",
      value = "test variation value2",
    ),
    reason = Reason(
      type = ReasonType.DEFAULT,
    ),
  )
}

val evaluation3: Evaluation by lazy {
  Evaluation(
    id = "test-feature-1:9:user id 2",
    feature_id = "test-feature-3",
    feature_version = 9,
    user_id = "user id 2",
    variation_id = "test-feature-1-variation-A",
    variation_value = "test variation value2",
    variation = Variation(
      id = "test-feature-1-variation-A",
      value = "test variation value2",
    ),
    reason = Reason(
      type = ReasonType.DEFAULT,
    ),
  )
}
