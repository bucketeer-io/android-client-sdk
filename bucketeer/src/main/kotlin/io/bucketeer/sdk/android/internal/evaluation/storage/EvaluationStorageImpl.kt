package io.bucketeer.sdk.android.internal.evaluation.storage

import io.bucketeer.sdk.android.internal.evaluation.cache.EvaluationSharedPrefs
import io.bucketeer.sdk.android.internal.evaluation.cache.MemCache
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationSQLDao
import io.bucketeer.sdk.android.internal.model.Evaluation

internal class EvaluationStorageImpl(
  override val userId: String,
  private val evaluationSQLDao: EvaluationSQLDao,
  private val evaluationSharedPrefs: EvaluationSharedPrefs,
  private val memCache: MemCache<String, List<Evaluation>>,
) : EvaluationStorage {
  override var currentEvaluationsId: String
    get() = evaluationSharedPrefs.currentEvaluationsId

    set(value) {
      evaluationSharedPrefs.currentEvaluationsId = value
    }

  override var featureTag: String
    get() = evaluationSharedPrefs.featureTag

    set(value) {
      evaluationSharedPrefs.featureTag = value
    }
  override var userAttributesUpdated: Boolean
    get() = evaluationSharedPrefs.userAttributesUpdated

    set(value) {
      evaluationSharedPrefs.userAttributesUpdated = value
    }

  override var evaluatedAt: String
    get() = evaluationSharedPrefs.evaluatedAt

    set(value) {
      evaluationSharedPrefs.evaluatedAt = value
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
    evaluations: List<Evaluation>,
    evaluatedAt: String,
  ) {
    evaluationSQLDao.startTransaction {
      evaluationSQLDao.deleteAll(userId)
      evaluationSQLDao.put(userId, evaluations)
    }
    evaluationSharedPrefs.evaluatedAt = evaluatedAt
    memCache.set(userId, evaluations)
  }

  override fun update(
    evaluations: List<Evaluation>,
    archivedFeatureIds: List<String>,
    evaluatedAt: String,
  ): Boolean {
    TODO("Not yet implemented")
  }

  override fun refreshCache() {
    evaluationSQLDao.get(userId).apply {
      memCache.set(userId, this)
    }
  }
}
