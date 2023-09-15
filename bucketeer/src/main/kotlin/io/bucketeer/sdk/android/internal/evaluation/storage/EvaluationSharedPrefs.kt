package io.bucketeer.sdk.android.internal.evaluation.storage

internal interface EvaluationSharedPrefs {
  var currentEvaluationsId: String
  var featureTag: String
  var evaluatedAt: String
  var userAttributesUpdated: Boolean
}
