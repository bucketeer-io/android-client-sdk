package io.bucketeer.sdk.android.internal.evaluation.cache

internal interface EvaluationSharedPrefs {
  var currentEvaluationsId: String
  var featureTag: String
  var evaluatedAt: String
  var userAttributesUpdated: Boolean
}
