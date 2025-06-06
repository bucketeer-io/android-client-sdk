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

  @VisibleForTesting
  internal var onErrorListener: OnErrorListener? = null

  init {
    updateFeatureTag(featureTag)
  }

  private fun updateFeatureTag(value: String) {
    // using `this.featureTag` to make it doesn't confuse with the constructor params `featureTag`
    // https://github.com/bucketeer-io/android-client-sdk/issues/69
    // 1- Save the featureTag in the SharedPreferences configured in the BKTConfig
    // 2- Clear the userEvaluationsID in the SharedPreferences if the featureTag changes
    if (evaluationStorage.getFeatureTag() != value) {
      evaluationStorage.clearCurrentEvaluationId()
      evaluationStorage.setFeatureTag(value)
    }
  }

  // https://github.com/bucketeer-io/android-client-sdk/issues/69
  // userAttributesUpdated: when the user attributes change via the customAttributes interface,
  // the userAttributesUpdated field must be set to true in the next request.
  fun setUserAttributesUpdated() {
    evaluationStorage.setUserAttributesUpdated()
  }

  private fun getEvaluations(
    user: User,
    timeoutMillis: Long?,
  ): GetEvaluationsResult {
    val currentEvaluationsId = evaluationStorage.getCurrentEvaluationId()
    val evaluatedAt = evaluationStorage.getEvaluatedAt()
    val userAttributesUpdated = evaluationStorage.getUserAttributesUpdated()

    val condition =
      UserEvaluationCondition(
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
          evaluationStorage.clearUserAttributesUpdated()
          return result
        }

        var shouldNotifyListener = true
        // https://github.com/bucketeer-io/android-client-sdk/issues/69
        // forceUpdate: a boolean that tells the SDK to delete all the current data
        // and save the latest evaluations from the response
        val forceUpdate = response.evaluations.forceUpdate
        val newEvaluatedAt = response.evaluations.createdAt
        if (forceUpdate) {
          val currentEvaluations: List<Evaluation> = response.evaluations.evaluations
          // 1- Delete all the evaluations from DB, and save the latest evaluations from the response into the DB
          // 2- Save the UserEvaluations.CreatedAt in the response as evaluatedAt in the SharedPreferences
          evaluationStorage.deleteAllAndInsert(
            evaluationsId = newEvaluationsId,
            evaluations = currentEvaluations,
            evaluatedAt = newEvaluatedAt,
          )
        } else {
          // 1- Check the evaluation list in the response and upsert them in the DB if the list is not empty
          // 2- Check the list of the feature flags that were archived on the console and delete them from the DB
          // 3- Save the UserEvaluations.CreatedAt in the response as evaluatedAt in the SharedPreferences
          val archivedFeatureIds = response.evaluations.archivedFeatureIds
          val updatedEvaluations = response.evaluations.evaluations
          shouldNotifyListener =
            evaluationStorage.update(
              evaluationsId = newEvaluationsId,
              evaluations = updatedEvaluations,
              archivedFeatureIds = archivedFeatureIds,
              evaluatedAt = newEvaluatedAt,
            )
        }

        evaluationStorage.clearUserAttributesUpdated()
        // Update listeners should be called on the main thread
        // to avoid unintentional lock on Interactor's execution thread.
        if (shouldNotifyListener) {
          mainHandler.post {
            // Prevent crash if consumer code throwing unhandled error
            runCatching {
              triggerOnUpdate()
            }.onFailure { onUpdateError ->
              val message = "failed while calling onUpdate listener: ${onUpdateError.message}"
              logd(onUpdateError) { message }
              logInternalError(BKTException.IllegalStateException(message))
            }
          }
        }
      }

      is GetEvaluationsResult.Failure -> {
        logd(result.error) { "ApiError: ${result.error.message}" }
      }
    }
    return result
  }

  @VisibleForTesting
  @Throws
  internal fun triggerOnUpdate() {
    synchronized(updateListeners) {
      updateListeners.forEach {
        it.value.onUpdate()
      }
    }
  }

  fun fetch(
    user: User,
    timeoutMillis: Long?,
  ): GetEvaluationsResult {
    var featureTag: String? = null
    try {
      featureTag = evaluationStorage.getFeatureTag()
      return getEvaluations(user = user, timeoutMillis = timeoutMillis)
    } catch (ex: Exception) {
      loge(ex) { "failed when fetching evaluations: ${ex.message}" }
      return GetEvaluationsResult.Failure(
        BKTException.IllegalStateException("failed when fetching evaluations: ${ex.message}"),
        featureTag ?: "",
      )
    }
  }

  fun refreshCache() {
    evaluationStorage.refreshCache()
  }

  fun addUpdateListener(listener: BKTClient.EvaluationUpdateListener): String {
    val key = idGenerator.newId()
    synchronized(updateListeners) {
      updateListeners[key] = listener
    }
    return key
  }

  fun removeUpdateListener(key: String) {
    synchronized(updateListeners) {
      updateListeners.remove(key)
    }
  }

  fun clearUpdateListeners() {
    synchronized(updateListeners) {
      updateListeners.clear()
    }
  }

  fun getLatest(featureId: String): Evaluation? = evaluationStorage.getBy(featureId)

  fun setErrorListener(listener: OnErrorListener?) {
    onErrorListener = listener
  }

  private fun logInternalError(error: BKTException) {
    onErrorListener?.onInternalError(error)
  }

  fun interface OnErrorListener {
    fun onInternalError(error: BKTException)
  }
}
