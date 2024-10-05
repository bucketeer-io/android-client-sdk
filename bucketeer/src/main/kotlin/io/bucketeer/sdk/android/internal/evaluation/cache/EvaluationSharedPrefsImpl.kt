package io.bucketeer.sdk.android.internal.evaluation.cache

import android.content.SharedPreferences
import io.bucketeer.sdk.android.internal.Constants

internal class EvaluationSharedPrefsImpl(
  private val sharedPrefs: SharedPreferences,
) : EvaluationSharedPrefs {
  override var currentEvaluationsId: String
    get() = sharedPrefs.getString(Constants.PREFERENCE_KEY_USER_EVALUATION_ID, "") ?: ""

    set(value) {
      sharedPrefs
        .edit()
        .putString(Constants.PREFERENCE_KEY_USER_EVALUATION_ID, value)
        .apply()
    }

  override var featureTag: String
    get() = sharedPrefs.getString(Constants.PREFERENCE_KEY_FEATURE_TAG, "") ?: ""

    set(value) {
      sharedPrefs
        .edit()
        .putString(Constants.PREFERENCE_KEY_FEATURE_TAG, value)
        .apply()
    }

  // https://github.com/bucketeer-io/android-client-sdk/issues/69
  // evaluatedAt: the last time the user was evaluated.
  // The server will return in the get_evaluations response (UserEvaluations.CreatedAt),
  // and it must be saved in the client
  override var evaluatedAt: String
    get() = sharedPrefs.getString(Constants.PREFERENCE_KEY_EVALUATED_AT, "0") ?: "0"

    set(value) {
      sharedPrefs
        .edit()
        .putString(Constants.PREFERENCE_KEY_EVALUATED_AT, value)
        .apply()
    }
  override var userAttributesUpdated: Boolean
    get() = sharedPrefs.getBoolean(Constants.PREFERENCE_KEY_USER_ATTRIBUTES_UPDATED, false)

    set(value) {
      sharedPrefs
        .edit()
        .putBoolean(Constants.PREFERENCE_KEY_USER_ATTRIBUTES_UPDATED, value)
        .apply()
    }
}
