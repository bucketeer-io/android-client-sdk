package io.bucketeer.sdk.android.e2e

import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.BKTEvaluation
import io.bucketeer.sdk.android.BKTEvaluationDetails

fun assertEvaluation(
  actual: BKTEvaluation?,
  expected: BKTEvaluation,
) {
  requireNotNull(actual)
  assertThat(actual.id).isEqualTo(expected.id)
  assertThat(actual.featureId).isEqualTo(expected.featureId)
  assertThat(actual.featureVersion).isEqualTo(expected.featureVersion)
  assertThat(actual.userId).isEqualTo(expected.userId)
  assertThat(actual.variationId).isEqualTo(expected.variationId)
  assertThat(actual.variationValue).isEqualTo(expected.variationValue)
  assertThat(actual.reason).isEqualTo(expected.reason)
}

fun <T> assertEvaluationDetails(
  actual: BKTEvaluationDetails<T>,
  expected: BKTEvaluationDetails<T>,
) {
  // Skipped check featureVersion
  assertThat(actual.featureId).isEqualTo(expected.featureId)
  assertThat(actual.userId).isEqualTo(expected.userId)
  assertThat(actual.variationId).isEqualTo(expected.variationId)
  assertThat(actual.variationName).isEqualTo(expected.variationName)
  assertThat(actual.variationValue).isEqualTo(expected.variationValue)
  assertThat(actual.reason).isEqualTo(expected.reason)
}
