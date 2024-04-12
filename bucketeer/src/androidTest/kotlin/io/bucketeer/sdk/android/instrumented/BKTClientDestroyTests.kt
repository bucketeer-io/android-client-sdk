package io.bucketeer.sdk.android.instrumented

import android.content.Context
import androidx.test.annotation.UiThreadTest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.BKTClientImpl
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.BKTUser
import io.bucketeer.sdk.android.BuildConfig
import io.bucketeer.sdk.android.e2e.FEATURE_TAG
import io.bucketeer.sdk.android.e2e.USER_ID
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.database.OpenHelperCallback
import io.bucketeer.sdk.android.internal.di.ComponentImpl
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationSQLDaoImpl
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BKTClientDestroyTests {

  private lateinit var context: Context
  private lateinit var config: BKTConfig
  private lateinit var user: BKTUser

  @Before
  @UiThreadTest
  fun setup() {
    context = ApplicationProvider.getApplicationContext()

    config = BKTConfig.builder()
      .apiKey(BuildConfig.API_KEY)
      .apiEndpoint(BuildConfig.API_ENDPOINT)
      .featureTag(FEATURE_TAG)
      .appVersion("1.2.3")
      .build()

    user = BKTUser.builder()
      .id(USER_ID)
      .build()

    val result = BKTClient.initialize(context, config, user).get()

    assertThat(result).isNull()
  }

  @After
  @UiThreadTest
  fun tearDown() {
    context.deleteDatabase(OpenHelperCallback.FILE_NAME)
    context.getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  @Test
  fun shouldCloseDatabase() {
    val clientImpl = BKTClient.getInstance() as BKTClientImpl
    val sqliteHelper = ((clientImpl.component as ComponentImpl).dataModule.evaluationSQLDao as EvaluationSQLDaoImpl).sqLiteOpenHelper
    val db = sqliteHelper.writableDatabase
    BKTClient.destroy()
    Thread.sleep(2000L)
    assertThat(db.isOpen).isFalse()
  }
}
