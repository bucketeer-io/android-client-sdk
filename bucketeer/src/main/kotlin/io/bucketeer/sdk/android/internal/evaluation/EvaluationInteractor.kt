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
import io.bucketeer.sdk.android.internal.remote.UserEvaluationCondition

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
    get() = sharedPrefs.getString(PREFERENCE_KEY_FEATURE_TAG, "") ?: ""

    @SuppressLint("ApplySharedPref")
    private set(value) {
      sharedPrefs.edit()
        .putString(PREFERENCE_KEY_FEATURE_TAG, value)
        .commit()
    }

  // https://github.com/bucketeer-io/android-client-sdk/issues/69
  // evaluatedAt: the last time the user was evaluated.
  // The server will return in the get_evaluations response (UserEvaluations.CreatedAt),
  // and it must be saved in the client
  @VisibleForTesting
  internal var evaluatedAt: String
    get() = sharedPrefs.getString(PREFERENCE_KEY_EVALUATED_AT, "0") ?: "0"

    @SuppressLint("ApplySharedPref")
    set(value) {
      sharedPrefs.edit()
        .putString(PREFERENCE_KEY_EVALUATED_AT, value)
        .commit()
    }

  @VisibleForTesting
  internal var userAttributesUpdated: Boolean
    get() = sharedPrefs.getBoolean(PREFERENCE_KEY_USER_ATTRIBUTES_UPDATED, false)

    @SuppressLint("ApplySharedPref")
    private set(value) {
      sharedPrefs.edit()
        .putBoolean(PREFERENCE_KEY_USER_ATTRIBUTES_UPDATED, value)
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

  // https://github.com/bucketeer-io/android-client-sdk/issues/69
  // userAttributesUpdated: when the user attributes change via the customAttributes interface,
  // the userAttributesUpdated field must be set to true in the next request.
  fun setUserAttributesUpdated() {
    userAttributesUpdated = true
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  fun fetch(user: User, timeoutMillis: Long?): GetEvaluationsResult {
    val currentEvaluationsId = this.currentEvaluationsId
    val condition = UserEvaluationCondition(
      evaluatedAt = evaluatedAt,
      userAttributesUpdated = userAttributesUpdated.toString(),
    )
    val result = apiClient.getEvaluations(user, currentEvaluationsId, timeoutMillis, condition)

    when (result) {
      is GetEvaluationsResult.Success -> {
        val response = result.value
        val newEvaluationsId = response.userEvaluationsId

        if (currentEvaluationsId == newEvaluationsId) {
          logd { "Nothing to sync" }
          // make sure we set `userAttributesUpdated` back to `false` even in case nothing to sync
          userAttributesUpdated = false
          return result
        }

        val newEvaluations = response.evaluations.evaluations
        val activeEvaluations: List<Evaluation>
        var shouldNotifyListener = true

        // https://github.com/bucketeer-io/android-client-sdk/issues/69
        // forceUpdate: a boolean that tells the SDK to delete all the current data
        // and save the latest evaluations from the response
        val forceUpdate = response.evaluations.forceUpdate
        if (forceUpdate) {
          // 1- Delete all the evaluations from DB, and save the latest evaluations from the response into the DB
          activeEvaluations = newEvaluations
        } else {
          val archivedFeatureIds = response.evaluations.archivedFeatureIds
          val updatedEvaluations = response.evaluations.evaluations
          val currentEvaluationsMap = evaluationDao.get(user.id).associateBy { it.id }.toMutableMap()
          // 1- Check the evaluation list in the response and upsert them in the DB if the list is not empty
          updatedEvaluations.forEach { evaluation ->
            currentEvaluationsMap[evaluation.id] = evaluation
          }
          activeEvaluations = currentEvaluationsMap.values.filterNot {
            // 2- Check the list of the feature flags that were archived on the console and delete them from the DB
            archivedFeatureIds.contains(it.featureId)
          }
          shouldNotifyListener = updatedEvaluations.isNotEmpty() || archivedFeatureIds.isNotEmpty()
        }

        val success = evaluationDao.deleteAllAndInsert(user.id, activeEvaluations)
        if (!success) {
          loge { "Failed to update latest evaluations" }
          return result
        }
        // update in-memory cache
        evaluations[user.id] = activeEvaluations

        this.currentEvaluationsId = newEvaluationsId
        userAttributesUpdated = false

        // 3- Save the UserEvaluations.CreatedAt in the response as evaluatedAt in the SharedPreferences
        evaluatedAt = response.evaluations.createdAt

        // Update listeners should be called on the main thread
        // to avoid unintentional lock on Interactor's execution thread.
        if (shouldNotifyListener) {
          mainHandler.post {
            updateListeners.forEach { it.value.onUpdate() }
          }
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

  companion object {
    private const val PREFERENCE_KEY_FEATURE_TAG = "bucketeer_feature_tag"
    private const val PREFERENCE_KEY_EVALUATED_AT = "bucketeer_evaluated_at"
    private const val PREFERENCE_KEY_USER_ATTRIBUTES_UPDATED = "bucketeer_user_attributes_updated"
  }
}
