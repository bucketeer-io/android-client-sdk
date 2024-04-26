package io.bucketeer.sdk.android.e2e

import android.content.Context
import androidx.test.annotation.UiThreadTest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.BKTClientImpl
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.BKTUser
import io.bucketeer.sdk.android.BuildConfig
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.database.OpenHelperCallback
import io.bucketeer.sdk.android.internal.di.ComponentImpl
import io.bucketeer.sdk.android.internal.model.ApiId
import io.bucketeer.sdk.android.internal.model.EventData
import io.bucketeer.sdk.android.internal.model.EventType
import io.bucketeer.sdk.android.internal.model.MetricsEventType
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BKTClientMetricEventTests {
  private lateinit var context: Context
  private lateinit var config: BKTConfig
  private lateinit var user: BKTUser

  @Before
  @UiThreadTest
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    user =
      BKTUser.builder()
        .id(USER_ID)
        .build()
  }

  @After
  @UiThreadTest
  fun tearDown() {
    BKTClient.destroy()
    context.deleteDatabase(OpenHelperCallback.FILE_NAME)
    context.getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  // Metrics Event Tests
  // refs: https://github.com/bucketeer-io/javascript-client-sdk/blob/main/e2e/events.spec.ts#L112
  @Test
  @UiThreadTest
  fun testUsingRandomStringInTheAPIKeyShouldThrowForbidden() {
    config =
      BKTConfig.builder()
        .apiKey("random_key")
        .apiEndpoint(BuildConfig.API_ENDPOINT)
        .featureTag(FEATURE_TAG)
        .appVersion("1.2.3")
        .build()

    val result = BKTClient.initialize(context, config, user).get()
    // Using a random string in the api key setting should throw Forbidden
    assertThat(result).isInstanceOf(BKTException.ForbiddenException::class.java)

    val client = BKTClient.getInstance() as BKTClientImpl
    val eventDao = (client.component as ComponentImpl).dataModule.eventSQLDao

    Thread.sleep(100)
    val events = eventDao.getEvents()
    assertThat(events.count()).isEqualTo(1)
    assertThat(
      events.any {
        val type = it.type
        val event = it.event
        return@any type == EventType.METRICS &&
          event is EventData.MetricsEvent &&
          event.type == MetricsEventType.FORBIDDEN_ERROR &&
          event.event.apiId == ApiId.GET_EVALUATIONS
      },
    ).isTrue()

    val flushResult = client.flush().get()
    assertThat(flushResult).isInstanceOf(BKTException.ForbiddenException::class.java)
    assertThat(eventDao.getEvents().count()).isEqualTo(2)
  }

  @Test
  @UiThreadTest
  fun testARandomStringInTheFeatureTagShouldNotAffectAPIRequest() {
    config =
      BKTConfig.builder()
        .apiKey(BuildConfig.API_KEY)
        .apiEndpoint(BuildConfig.API_ENDPOINT)
        .featureTag("random-string-abc")
        .appVersion("1.2.3")
        .build()

    val result = BKTClient.initialize(context, config, user).get()
    assertThat(result).isNull()
  }

  @Test
  @UiThreadTest
  fun testTimeout() {
    config =
      BKTConfig.builder()
        .apiKey(BuildConfig.API_KEY)
        .apiEndpoint(BuildConfig.API_ENDPOINT)
        .featureTag("random-string-abc")
        .appVersion("1.2.3")
        .build()

    val result = BKTClient.initialize(context, config, user, timeoutMillis = 10).get()
    assertThat(result).isInstanceOf(BKTException.TimeoutException::class.java)

    val client = BKTClient.getInstance() as BKTClientImpl
    val eventDao = (client.component as ComponentImpl).dataModule.eventSQLDao

    Thread.sleep(100)
    val events = eventDao.getEvents()
    assertThat(events.count()).isEqualTo(1)
    assertThat(
      events.any {
        val type = it.type
        val event = it.event
        return@any type == EventType.METRICS &&
          event is EventData.MetricsEvent &&
          event.type == MetricsEventType.TIMEOUT_ERROR &&
          event.event.apiId == ApiId.GET_EVALUATIONS
      },
    ).isTrue()

    val flushResult = client.flush().get()
    assertThat(flushResult).isNull()
    assertThat(eventDao.getEvents().count()).isEqualTo(0)
  }
}
