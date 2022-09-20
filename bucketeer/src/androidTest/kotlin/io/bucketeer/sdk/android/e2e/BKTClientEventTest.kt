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
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BKTClientEventTest {
  private lateinit var context: Context
  private lateinit var config: BKTConfig
  private lateinit var user: BKTUser

  @Before
  @UiThreadTest
  fun setup() {
    context = ApplicationProvider.getApplicationContext()

    config = BKTConfig.builder()
      .apiKey(BuildConfig.API_KEY)
      .endpoint(BuildConfig.API_URL)
      .featureTag(FEATURE_TAG)
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
    BKTClient.destroy()
    context.deleteDatabase(OpenHelperCallback.FILE_NAME)
    context.getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  @Test
  fun track() {
    val client = BKTClient.getInstance() as BKTClientImpl
    val eventDao = (client.component as ComponentImpl).dataModule.eventDao

    Thread.sleep(100)

    assertThat(eventDao.getEvents()).hasSize(2)

    client.track(GOAL_ID, GOAL_VALUE)

    Thread.sleep(100)

    assertThat(eventDao.getEvents()).hasSize(3)

    val result = client.flush().get()

    assertThat(result).isNull()

    assertThat(eventDao.getEvents()).isEmpty()
  }
}
