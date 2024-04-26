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
  override fun getCurrentEvaluationId(): String {
    return evaluationSharedPrefs.currentEvaluationsId
  }

  override fun clearCurrentEvaluationId() {
    evaluationSharedPrefs.currentEvaluationsId = ""
  }

  override fun setFeatureTag(tag: String) {
    evaluationSharedPrefs.featureTag = tag
  }

  override fun getFeatureTag(): String {
    return evaluationSharedPrefs.featureTag
  }

  override fun getEvaluatedAt(): String {
    return evaluationSharedPrefs.evaluatedAt
  }

  override fun getUserAttributesUpdated(): Boolean {
    return evaluationSharedPrefs.userAttributesUpdated
  }

  override fun setUserAttributesUpdated() {
    evaluationSharedPrefs.userAttributesUpdated = true
  }

  override fun clearUserAttributesUpdated() {
    evaluationSharedPrefs.userAttributesUpdated = false
  }

  override fun getBy(featureId: String): Evaluation? {
    return get().firstOrNull {
      it.featureId == featureId
    }
  }

  override fun get(): List<Evaluation> {
    return memCache.get(userId) ?: emptyList()
  }

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
