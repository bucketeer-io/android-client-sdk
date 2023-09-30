package io.bucketeer.sdk.android.internal.evaluation

import android.os.Handler
import androidx.annotation.VisibleForTesting
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.IdGenerator
import io.bucketeer.sdk.android.internal.evaluation.storage.EvaluationStorage
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
  private val evaluationStorage: EvaluationStorage,
  private val idGenerator: IdGenerator,
  featureTag: String,
  private val mainHandler: Handler,
) {

  @VisibleForTesting
  internal val updateListeners = mutableMapOf<String, BKTClient.EvaluationUpdateListener>()

  init {
    updateFeatureTag(featureTag)
  }

  private fun updateFeatureTag(value: String) {
    // using `this.featureTag` to make it doesn't confuse with the constructor params `featureTag`
    // https://github.com/bucketeer-io/android-client-sdk/issues/69
    // 1- Save the featureTag in the SharedPreferences configured in the BKTConfig
    // 2- Clear the userEvaluationsID in the SharedPreferences if the featureTag changes
    if (evaluationStorage.featureTag != value) {
      evaluationStorage.currentEvaluationsId = ""
      evaluationStorage.featureTag = value
    }
  }

  // https://github.com/bucketeer-io/android-client-sdk/issues/69
  // userAttributesUpdated: when the user attributes change via the customAttributes interface,
  // the userAttributesUpdated field must be set to true in the next request.
  fun setUserAttributesUpdated() {
    evaluationStorage.userAttributesUpdated = true
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  fun fetch(user: User, timeoutMillis: Long?): GetEvaluationsResult {
    val currentEvaluationsId = evaluationStorage.currentEvaluationsId
    val evaluatedAt = evaluationStorage.evaluatedAt
    val userAttributesUpdated = evaluationStorage.userAttributesUpdated.toString()
    val featureTag = evaluationStorage.featureTag
    val condition = UserEvaluationCondition(
      evaluatedAt = evaluatedAt,
      userAttributesUpdated = userAttributesUpdated,
    )
    val result = apiClient.getEvaluations(user, currentEvaluationsId, timeoutMillis, condition)

    when (result) {
      is GetEvaluationsResult.Success -> {
        val response = result.value
        val newEvaluationsId = response.userEvaluationsId

        if (currentEvaluationsId == newEvaluationsId) {
          logd { "Nothing to sync" }
          // make sure we set `userAttributesUpdated` back to `false` even in case nothing to sync
          evaluationStorage.userAttributesUpdated = false
          return result
        }

        var shouldNotifyListener = true
        try {
          // https://github.com/bucketeer-io/android-client-sdk/issues/69
          // forceUpdate: a boolean that tells the SDK to delete all the current data
          // and save the latest evaluations from the response
          val forceUpdate = response.evaluations.forceUpdate
          val newEvaluatedAt = response.evaluations.createdAt
          if (forceUpdate) {
            val currentEvaluations: List<Evaluation> = response.evaluations.evaluations
            // 1- Delete all the evaluations from DB, and save the latest evaluations from the response into the DB
            // 2- Save the UserEvaluations.CreatedAt in the response as evaluatedAt in the SharedPreferences
            evaluationStorage.deleteAllAndInsert(currentEvaluations, newEvaluatedAt)
          } else {
            val archivedFeatureIds = response.evaluations.archivedFeatureIds
            val updatedEvaluations = response.evaluations.evaluations
            shouldNotifyListener =
              evaluationStorage.update(updatedEvaluations, archivedFeatureIds, newEvaluatedAt)
          }
        } catch (ex: Exception) {
          loge { "Failed to update latest evaluations" }
          GetEvaluationsResult.Failure(
            BKTException.IllegalStateException("error: ${ex.message}"),
            featureTag,
          )
          return result
        }

        evaluationStorage.currentEvaluationsId = newEvaluationsId
        evaluationStorage.userAttributesUpdated = false

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

  fun refreshCache() {
    evaluationStorage.refreshCache()
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
    return evaluationStorage.getBy(featureId)
  }
}
