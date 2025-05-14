package io.bucketeer.sdk.android.internal.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.EnumJsonAdapter
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.internal.Clock
import io.bucketeer.sdk.android.internal.ClockImpl
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.IdGenerator
import io.bucketeer.sdk.android.internal.IdGeneratorImpl
import io.bucketeer.sdk.android.internal.cache.MemCache
import io.bucketeer.sdk.android.internal.database.OpenHelperCallback
import io.bucketeer.sdk.android.internal.database.createDatabase
import io.bucketeer.sdk.android.internal.evaluation.cache.EvaluationSharedPrefs
import io.bucketeer.sdk.android.internal.evaluation.cache.EvaluationSharedPrefsImpl
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationSQLDao
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationSQLDaoImpl
import io.bucketeer.sdk.android.internal.evaluation.storage.EvaluationStorage
import io.bucketeer.sdk.android.internal.evaluation.storage.EvaluationStorageImpl
import io.bucketeer.sdk.android.internal.event.db.EventSQLDao
import io.bucketeer.sdk.android.internal.event.db.EventSQLDaoImpl
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.internal.model.ReasonType
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.model.jsonadapter.ApiIdAdapter
import io.bucketeer.sdk.android.internal.model.jsonadapter.EventAdapterFactory
import io.bucketeer.sdk.android.internal.model.jsonadapter.EventTypeAdapter
import io.bucketeer.sdk.android.internal.model.jsonadapter.MetricsEventAdapterFactory
import io.bucketeer.sdk.android.internal.model.jsonadapter.MetricsEventTypeAdapter
import io.bucketeer.sdk.android.internal.model.jsonadapter.SourceIDAdapter
import io.bucketeer.sdk.android.internal.remote.ApiClient
import io.bucketeer.sdk.android.internal.remote.ApiClientImpl
import io.bucketeer.sdk.android.internal.user.UserHolder

internal open class DataModule(
  val application: Application,
  user: User,
  val config: BKTConfig,
  val inMemoryDB: Boolean = false,
) {
  open val clock: Clock by lazy { ClockImpl() }

  open val idGenerator: IdGenerator by lazy { IdGeneratorImpl() }

  val moshi: Moshi by lazy { createMoshi() }

  open val apiClient: ApiClient by lazy {
    ApiClientImpl(
      apiEndpoint = config.apiEndpoint,
      apiKey = config.apiKey,
      featureTag = config.featureTag,
      moshi = moshi,
      sourceId = config.sourceId,
      sdkVersion = config.sdkVersion,
    )
  }

  private val sqliteOpenHelper: SupportSQLiteOpenHelper by lazy {
    createDatabase(
      context = application,
      fileName = if (inMemoryDB) null else OpenHelperCallback.FILE_NAME,
      sharedPreferences,
    )
  }

  internal val evaluationStorage: EvaluationStorage by lazy {
    EvaluationStorageImpl(
      userId = user.id,
      evaluationSQLDao,
      evaluationSharedPrefs,
      MemCache.Builder<String, List<Evaluation>>().build(),
    )
  }

  internal val evaluationSQLDao: EvaluationSQLDao by lazy {
    EvaluationSQLDaoImpl(sqliteOpenHelper, moshi)
  }

  internal val evaluationSharedPrefs: EvaluationSharedPrefs by lazy {
    EvaluationSharedPrefsImpl(sharedPreferences)
  }

  internal val eventSQLDao: EventSQLDao by lazy {
    EventSQLDaoImpl(sqliteOpenHelper, moshi)
  }

  internal val sharedPreferences: SharedPreferences by lazy {
    application.getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE)
  }

  internal val userHolder: UserHolder by lazy { UserHolder(user) }

  internal fun destroy() {
    runCatching {
      eventSQLDao.close()
      evaluationSQLDao.close()
      sqliteOpenHelper.close()
    }
  }

  companion object {
    @VisibleForTesting
    internal fun createMoshi(): Moshi =
      Moshi
        .Builder()
        .add(EventTypeAdapter())
        .add(MetricsEventTypeAdapter())
        .add(SourceIDAdapter())
        .add(EventAdapterFactory())
        .add(MetricsEventAdapterFactory())
        .add(
          ReasonType::class.java,
          EnumJsonAdapter.create(ReasonType::class.java).withUnknownFallback(
            ReasonType.DEFAULT,
          ),
        ).add(ApiIdAdapter())
        .build()
  }
}
