package io.bucketeer.sdk.android.internal.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.internal.Clock
import io.bucketeer.sdk.android.internal.ClockImpl
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.IdGenerator
import io.bucketeer.sdk.android.internal.IdGeneratorImpl
import io.bucketeer.sdk.android.internal.database.OpenHelperCallback
import io.bucketeer.sdk.android.internal.database.createDatabase
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationDao
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationDaoImpl
import io.bucketeer.sdk.android.internal.event.db.EventDao
import io.bucketeer.sdk.android.internal.event.db.EventDaoImpl
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.model.jsonadapter.EventAdapterFactory
import io.bucketeer.sdk.android.internal.model.jsonadapter.EventTypeAdapter
import io.bucketeer.sdk.android.internal.model.jsonadapter.MetricsEventAdapterFactory
import io.bucketeer.sdk.android.internal.model.jsonadapter.MetricsEventTypeAdapter
import io.bucketeer.sdk.android.internal.model.jsonadapter.ReasonTypeAdapter
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
      endpoint = config.endpoint,
      apiKey = config.apiKey,
      featureTag = config.featureTag,
      moshi = moshi,
    )
  }

  private val sqliteOpenHelper: SupportSQLiteOpenHelper by lazy {
    createDatabase(
      context = application,
      fileName = if (inMemoryDB) null else OpenHelperCallback.FILE_NAME,
    )
  }

  internal val evaluationDao: EvaluationDao by lazy {
    EvaluationDaoImpl(sqliteOpenHelper, moshi)
  }

  internal val eventDao: EventDao by lazy {
    EventDaoImpl(sqliteOpenHelper, moshi)
  }

  internal val sharedPreferences: SharedPreferences by lazy {
    application.getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE)
  }

  internal val userHolder: UserHolder by lazy { UserHolder(user) }

  companion object {
    @VisibleForTesting
    internal fun createMoshi(): Moshi {
      return Moshi.Builder()
        .add(EventTypeAdapter())
        .add(MetricsEventTypeAdapter())
        .add(ReasonTypeAdapter())
        .add(SourceIDAdapter())
        .add(EventAdapterFactory())
        .add(MetricsEventAdapterFactory())
        .build()
    }
  }
}
