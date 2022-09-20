package io.bucketeer.sdk.android.internal.evaluation

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationDao
import io.bucketeer.sdk.android.internal.logd
import io.bucketeer.sdk.android.internal.loge
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.remote.ApiClient
import io.bucketeer.sdk.android.internal.remote.GetEvaluationsResult

/**
 * Evaluation business logics.
 *
 * All methods in this class must be sync to keep things simple.
 */
internal class EvaluationInteractor(
  private val apiClient: ApiClient,
  private val evaluationDao: EvaluationDao,
  private val sharedPrefs: SharedPreferences,
) {
  // key: userId
  @VisibleForTesting
  internal val evaluations = mutableMapOf<String, List<Evaluation>>()

  @VisibleForTesting
  internal var currentEvaluationsId: String
    get() = sharedPrefs.getString(Constants.PREFERENCE_KEY_USER_EVALUATION_ID, "") ?: ""

    @SuppressLint("ApplySharedPref")
    set(value) {
      sharedPrefs.edit()
        .putString(Constants.PREFERENCE_KEY_USER_EVALUATION_ID, value)
        .commit()
    }

  @Suppress("MoveVariableDeclarationIntoWhen")
  fun fetch(user: User, timeoutMillis: Long?): GetEvaluationsResult {
    val currentEvaluationsId = this.currentEvaluationsId

    val result = apiClient.getEvaluations(user, currentEvaluationsId, timeoutMillis)

    when (result) {
      is GetEvaluationsResult.Success -> {
        val response = result.value.data
        val newEvaluationsId = response.user_evaluations_id
        if (currentEvaluationsId == newEvaluationsId) {
          logd { "Nothing to sync" }
          return result
        }

        val newEvaluations = response.evaluations.evaluations

        val success = evaluationDao.deleteAllAndInsert(user.id, newEvaluations)
        if (!success) {
          loge { "Failed to update latest evaluations" }
          return result
        }

        this.currentEvaluationsId = newEvaluationsId

        evaluations[user.id] = newEvaluations
      }
      is GetEvaluationsResult.Failure -> {
        logd(result.error) { "ApiError: ${result.error.message}" }
      }
    }
    return result
  }

  fun refreshCache(userId: String) {
    evaluations[userId] = evaluationDao.get(userId)
  }

  fun getLatest(userId: String, featureId: String): Evaluation? {
    val evaluations = evaluations[userId] ?: return null
    return evaluations.firstOrNull { it.feature_id == featureId }
  }
}
