package io.bucketeer.sdk.android.e2e

import android.content.Context
import androidx.test.annotation.UiThreadTest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.BKTUser
import io.bucketeer.sdk.android.BuildConfig
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.database.OpenHelperCallback
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
  fun testUsingRandomStringInTheAPIKeyShouldThrowForbidden() {
    config = BKTConfig.builder()
      .apiKey("random_key")
      .apiEndpoint(BuildConfig.API_ENDPOINT)
      .featureTag(FEATURE_TAG)
      .appVersion("1.2.3")
      .build()

    val result = BKTClient.initialize(context, config, user).get()
    Truth.assertThat(result).isNull()
  }

  @Test
  fun testARandomStringInTheFeatureTagShouldNotAffectAPIRequest() {}

  @Test
  fun testTimeout() {}
}
