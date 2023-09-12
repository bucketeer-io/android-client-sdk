package io.bucketeer.sdk.android.internal.evaluation

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Handler
import androidx.annotation.VisibleForTesting
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.IdGenerator
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
  private val idGenerator: IdGenerator,
  featureTag: String,
  private val mainHandler: Handler,
) {
  // key: userId
  @VisibleForTesting
  internal val evaluations = mutableMapOf<String, List<Evaluation>>()

  @VisibleForTesting
  internal val updateListeners = mutableMapOf<String, BKTClient.EvaluationUpdateListener>()

  @VisibleForTesting
  internal var currentEvaluationsId: String
    get() = sharedPrefs.getString(Constants.PREFERENCE_KEY_USER_EVALUATION_ID, "") ?: ""

    @SuppressLint("ApplySharedPref")
    set(value) {
      sharedPrefs.edit()
        .putString(Constants.PREFERENCE_KEY_USER_EVALUATION_ID, value)
        .commit()
    }

  @VisibleForTesting
  internal var featureTag: String
    get() = sharedPrefs.getString(Constants.PREFERENCE_KEY_USER_EVALUATION_ID, "") ?: ""

    @SuppressLint("ApplySharedPref")
    private set(value) {
      sharedPrefs.edit()
        .putString(Constants.PREFERENCE_KEY_USER_EVALUATION_ID, value)
        .commit()
    }

  init {
    updateFeatureTag(featureTag)
  }

  private fun updateFeatureTag(value: String) {
    // using `this.featureTag` to make it doesn't confuse with the constructor params `featureTag`
    // https://github.com/bucketeer-io/android-client-sdk/issues/69
    // 1- Save the featureTag in the UserDefault configured in the BKTConfig
    // 2- Clear the userEvaluationsID in the UserDefault if the featureTag changes
    if (this.featureTag != value) {
      currentEvaluationsId = ""
      this.featureTag = value
    }
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  fun fetch(user: User, timeoutMillis: Long?): GetEvaluationsResult {
    val currentEvaluationsId = this.currentEvaluationsId

    val result = apiClient.getEvaluations(user, currentEvaluationsId, timeoutMillis)

    when (result) {
      is GetEvaluationsResult.Success -> {
        val response = result.value
        val newEvaluationsId = response.userEvaluationsId
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

        // Update listeners should be called on the main thread
        // to avoid unintentional lock on Interactor's execution thread.
        mainHandler.post {
          updateListeners.forEach { it.value.onUpdate() }
        }
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

  fun clearCurrentEvaluationsId() {
    this.currentEvaluationsId = ""
  }

  fun addUpdateListener(listener: BKTClient.EvaluationUpdateListener): String {
    val key = idGenerator.newId()
    updateListeners[key] = listener
    return key
  }

  fun removeUpdateListener(key: String) {
    updateListeners.remove(key)
  }

  fun clearUpdateListeners() {
    updateListeners.clear()
  }

  fun getLatest(userId: String, featureId: String): Evaluation? {
    val evaluations = evaluations[userId] ?: return null
    return evaluations.firstOrNull { it.featureId == featureId }
  }
}
