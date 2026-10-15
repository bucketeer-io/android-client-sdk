package io.bucketeer.sdk.android.internal.evaluation

import android.os.Handler
import androidx.annotation.VisibleForTesting
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.IdGenerator
import io.bucketeer.sdk.android.internal.evaluation.storage.EvaluationStorage
import io.bucketeer.sdk.android.internal.evaluation.storage.EvaluationWriteResult
import io.bucketeer.sdk.android.internal.evaluation.storage.UserAttributesState
import io.bucketeer.sdk.android.internal.logd
import io.bucketeer.sdk.android.internal.loge
import io.bucketeer.sdk.android.internal.logw
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
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
    val userAttributesState = evaluationStorage.getUserAttributesState()

    val condition =
      UserEvaluationCondition(
        evaluatedAt = evaluatedAt,
        userAttributesUpdated = userAttributesState.userAttributesUpdated,
      )

    val result = apiClient.getEvaluations(user, currentEvaluationsId, timeoutMillis, condition)

    when (result) {
      is GetEvaluationsResult.Success -> {
        val response = result.value
        val newEvaluationsId = response.userEvaluationsId

        if (currentEvaluationsId == newEvaluationsId) {
          logd { "Nothing to sync" }
          // make sure we set `userAttributesUpdated` back to `false` even in case nothing to sync
          evaluationStorage.clearUserAttributesUpdated(userAttributesState)
          return result
        }

        // Write before clear: a reply that did not land must keep the flag, so the next poll
        // asks again. Clear before notify: a listener that triggers a nested fetch must see
        // the flag already cleared. Streamed data never clears it (see applyStreamedEvaluations).
        when (val writeResult = writeEvaluations(response)) {
          EvaluationWriteResult.SkippedStale -> {
            logd { "Poll reply skipped as stale, keeping userAttributesUpdated" }
          }

          is EvaluationWriteResult.Landed -> {
            evaluationStorage.clearUserAttributesUpdated(userAttributesState)
            if (writeResult.shouldNotify) {
              notifyListeners { true }
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

  val cachedEvaluationsState: CachedEvaluationsState
    get() =
      CachedEvaluationsState(
        userEvaluationsId = evaluationStorage.getCurrentEvaluationId(),
        evaluatedAt = evaluationStorage.getEvaluatedAt(),
      )

  val userAttributesState: UserAttributesState
    get() = evaluationStorage.getUserAttributesState()

  fun clearUserAttributesUpdated(state: UserAttributesState) {
    evaluationStorage.clearUserAttributesUpdated(state)
  }

  /**
   * Applies a payload that arrived over the stream (put or patch), through the same write path
   * as [fetch]. Runs on the SDK executor. Never throws.
   *
   * Does not clear the user-attributes-updated flag: only [fetch] knows its own request carried
   * the flag.
   *
   * [shouldNotify] is checked on the main thread right before the listeners run, so a stop or
   * destroy after the write still suppresses callbacks into torn-down app code. The write is not
   * rolled back.
   */
  fun applyStreamedEvaluations(
    response: GetEvaluationsResponse,
    shouldNotify: () -> Boolean = { true },
  ) {
    // Without a readable timestamp the stale check cannot order this payload, so applying it
    // could rewind the cache.
    if (response.evaluations.createdAt.toLongOrNull() == null) {
      logw {
        "Streamed evaluations dropped: unreadable createdAt " +
          "'${response.evaluations.createdAt}' (userEvaluationsId: ${response.userEvaluationsId})"
      }
      return
    }
    val writeResult =
      runCatching { writeEvaluations(response) }
        .getOrElse { error ->
          loge(error) { "Failed to apply streamed evaluations: ${error.message}" }
          return
        }
    if (writeResult is EvaluationWriteResult.Landed && writeResult.shouldNotify) {
      notifyListeners(shouldNotify)
    }
  }

  private fun writeEvaluations(response: GetEvaluationsResponse): EvaluationWriteResult {
    val newEvaluationsId = response.userEvaluationsId
    val newEvaluatedAt = response.evaluations.createdAt
    // https://github.com/bucketeer-io/android-client-sdk/issues/69
    // forceUpdate: a boolean that tells the SDK to delete all the current data
    // and save the latest evaluations from the response
    return if (response.evaluations.forceUpdate) {
      // 1- Delete all the evaluations from DB, and save the latest evaluations from the response into the DB
      // 2- Save the UserEvaluations.CreatedAt in the response as evaluatedAt in the SharedPreferences
      evaluationStorage.deleteAllAndInsert(
        evaluationsId = newEvaluationsId,
        evaluations = response.evaluations.evaluations,
        evaluatedAt = newEvaluatedAt,
      )
    } else {
      // 1- Check the evaluation list in the response and upsert them in the DB if the list is not empty
      // 2- Check the list of the feature flags that were archived on the console and delete them from the DB
      // 3- Save the UserEvaluations.CreatedAt in the response as evaluatedAt in the SharedPreferences
      evaluationStorage.update(
        evaluationsId = newEvaluationsId,
        evaluations = response.evaluations.evaluations,
        archivedFeatureIds = response.evaluations.archivedFeatureIds,
        evaluatedAt = newEvaluatedAt,
      )
    }
  }

  // Update listeners should be called on the main thread
  // to avoid unintentional lock on Interactor's execution thread.
  private fun notifyListeners(shouldNotify: () -> Boolean) {
    mainHandler.post {
      if (!shouldNotify()) return@post
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
