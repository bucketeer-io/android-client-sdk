package io.bucketeer.sdk.android.e2e

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
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.database.OpenHelperCallback
import io.bucketeer.sdk.android.internal.di.ComponentImpl
import io.bucketeer.sdk.android.internal.model.EventData
import io.bucketeer.sdk.android.internal.model.EventType
import io.bucketeer.sdk.android.internal.model.ReasonType
import io.bucketeer.sdk.android.internal.model.SourceId
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BKTClientWithWrapperSDKSourceId {
  private lateinit var context: Context
  private lateinit var config: BKTConfig
  private lateinit var user: BKTUser

  @Before
  @UiThreadTest
  fun setup() {
    context = ApplicationProvider.getApplicationContext()

    config =
      BKTConfig
        .builder()
        .apiKey(BuildConfig.API_KEY)
        .apiEndpoint(BuildConfig.API_ENDPOINT)
        .featureTag(FEATURE_TAG)
        .wrapperSdkSourceId(SourceId.FLUTTER.value)
        .wrapperSdkVersion("3.2.1")
        .appVersion("1.2.3")
        .build()

    user =
      BKTUser
        .builder()
        .id(USER_ID)
        .build()

    val result = BKTClient.initialize(context, config, user).get()
    assertThat(result).isNull()
  }

  @After
  @UiThreadTest
  fun tearDown() {
    BKTClient.destroy()
    context.deleteDatabase(OpenHelperCallback.FILE_NAME)
    context
      .getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  @Test
  fun testEvaluationEvents() {
    val client = BKTClient.getInstance() as BKTClientImpl
    assertThat(client.stringVariation(FEATURE_ID_STRING, "test")).isEqualTo("value-1")
    val eventDao = (client.component as ComponentImpl).dataModule.eventSQLDao

    Thread.sleep(2000)
    val events = eventDao.getEvents()
    assertThat(
      events.any {
        val type = it.type
        val event = it.event
        return@any type == EventType.EVALUATION &&
          event is EventData.EvaluationEvent &&
          event.reason.type == ReasonType.DEFAULT &&
          event.sourceId == SourceId.FLUTTER &&
          event.sdkVersion == "3.2.1"
      },
    ).isTrue()
    assertThat(
      events.any {
        val type = it.type
        val event = it.event
        return@any type == EventType.METRICS &&
          event is EventData.MetricsEvent &&
          event.sourceId == SourceId.FLUTTER &&
          event.sdkVersion == "3.2.1"
      },
    ).isTrue()
  }
}
