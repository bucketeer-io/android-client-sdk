package io.bucketeer.sdk.android.internal.evaluation.storage

import io.bucketeer.sdk.android.internal.cache.MemCache
import io.bucketeer.sdk.android.internal.evaluation.cache.EvaluationSharedPrefs
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationSQLDao
import io.bucketeer.sdk.android.internal.model.Evaluation

internal class EvaluationStorageImpl(
  override val userId: String,
  private val evaluationSQLDao: EvaluationSQLDao,
  private val evaluationSharedPrefs: EvaluationSharedPrefs,
  private val memCache: MemCache<String, List<Evaluation>>,
) : EvaluationStorage {
  override fun getCurrentEvaluationId(): String = evaluationSharedPrefs.currentEvaluationsId

  override fun clearCurrentEvaluationId() {
    evaluationSharedPrefs.currentEvaluationsId = ""
  }

  override fun setFeatureTag(tag: String) {
    evaluationSharedPrefs.featureTag = tag
  }

  override fun getFeatureTag(): String = evaluationSharedPrefs.featureTag

  override fun getEvaluatedAt(): String = evaluationSharedPrefs.evaluatedAt

  // https://github.com/bucketeer-io/android-client-sdk/issues/69
  // userAttributesUpdated: when the user attributes change via the customAttributes interface,
  // the userAttributesUpdated field must be set to true in the next request.
  override fun getUserAttributesUpdated(): Boolean = evaluationSharedPrefs.userAttributesUpdated

  // We use `@Synchronized` for user attribute state management because it's accessed from multiple threads:
  // 1. Main thread: `setUserAttributesUpdated` is called when user updates attributes.
  // 2. SDK executor thread: `getUserAttributesState` and `clearUserAttributesUpdated` are called during evaluation fetching.
  private var userAttributesVersion: Int = 0

  @Synchronized
  override fun getUserAttributesState(): UserAttributesState =
    UserAttributesState(
      userAttributesUpdated = evaluationSharedPrefs.userAttributesUpdated,
      version = userAttributesVersion,
    )

  @Synchronized
  override fun setUserAttributesUpdated() {
    // https://github.com/bucketeer-io/ios-client-sdk/pull/116
    // We used to simple boolean flag `userAttributesUpdated` to track if the user attributes are updated.
    // However, there is a race condition when the user attributes are updated while the SDK is fetching the evaluations.
    //
    // <pre>
    // 1. [T1] `setUserAttributesUpdated` is called. `userAttributesUpdated` = true.
    // 2. [T2] `fetchEvaluations` is called. The request contains `userAttributesUpdated` = true.
    // 3. [T1] `setUserAttributesUpdated` is called again. `userAttributesUpdated` = true.
    // 4. [T2] `fetchEvaluations` succeeded. `clearUserAttributesUpdated` is called. `userAttributesUpdated` = false.
    // </pre>
    //
    // In step 4, the `userAttributesUpdated` is cleared, but the update in step 3 is not sent to the server.
    // To avoid this race condition, we use a versioning system `userAttributesId` to track the update.
    // The `userAttributesId` is generated when `setUserAttributesUpdated` is called.
    // When `fetchEvaluations` succeeded, we only clear the `userAttributesUpdated` if the `userAttributesId` matches.
    userAttributesVersion++
    evaluationSharedPrefs.userAttributesUpdated = true
  }

  @Synchronized
  override fun clearUserAttributesUpdated(state: UserAttributesState) {
    if (userAttributesVersion == state.version) {
      evaluationSharedPrefs.userAttributesUpdated = false
    }
  }

  override fun getBy(featureId: String): Evaluation? =
    get().firstOrNull {
      it.featureId == featureId
    }

  override fun get(): List<Evaluation> = memCache.get(userId) ?: emptyList()

  override fun deleteAllAndInsert(
    evaluationsId: String,
    evaluations: List<Evaluation>,
    evaluatedAt: String,
  ) {
    evaluationSQLDao.startTransaction {
      evaluationSQLDao.deleteAll(userId)
      evaluationSQLDao.put(userId, evaluations)
    }
    evaluationSharedPrefs.currentEvaluationsId = evaluationsId
    evaluationSharedPrefs.evaluatedAt = evaluatedAt
    memCache.set(userId, evaluations)
  }

  override fun update(
    evaluationsId: String,
    evaluations: List<Evaluation>,
    archivedFeatureIds: List<String>,
    evaluatedAt: String,
  ): Boolean {
    // We will use `featureId` to filter the data
    // Details -> https://github.com/bucketeer-io/android-client-sdk/pull/88/files#r1333847962
    val currentEvaluationsByFeaturedId =
      evaluationSQLDao.get(userId).associateBy { it.featureId }.toMutableMap()
    // 1- Check the evaluation list in the response and upsert them in the DB if the list is not empty
    evaluations.forEach { evaluation ->
      currentEvaluationsByFeaturedId[evaluation.featureId] = evaluation
    }
    // 2- Check the list of the feature flags that were archived on the console and delete them from the DB
    val currentEvaluations =
      currentEvaluationsByFeaturedId.values.filterNot {
        archivedFeatureIds.contains(it.featureId)
      }
    deleteAllAndInsert(
      evaluationsId = evaluationsId,
      evaluations = currentEvaluations,
      evaluatedAt = evaluatedAt,
    )
    return evaluations.isNotEmpty() || archivedFeatureIds.isNotEmpty()
  }

  override fun refreshCache() {
    runCatching {
      val evaluations = evaluationSQLDao.get(userId)
      memCache.set(userId, evaluations)
    }
  }
}
