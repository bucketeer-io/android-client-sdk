package io.bucketeer.sdk.android.e2e

import android.content.Context
import androidx.test.annotation.UiThreadTest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.BKTEvaluation
import io.bucketeer.sdk.android.BKTUser
import io.bucketeer.sdk.android.BuildConfig
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.database.OpenHelperCallback
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BKTClientTest {

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
  fun evaluation_update_flow() {
    val client = BKTClient.getInstance()

    assertThat(client.stringVariation(FEATURE_ID_STRING, ""))
      .isEqualTo("value-1")

    client.setUserAttributes(mapOf("app_version" to "0.0.1"))

    client.fetchEvaluations().get()

    val actual = client.stringVariation(FEATURE_ID_STRING, "")
    assertThat(actual).isEqualTo("value-2")

    val detail = client.evaluationDetails(FEATURE_ID_STRING)
    assertEvaluation(
      detail,
      BKTEvaluation(
        id = "feature-android-e2e-string:3:bucketeer-android-user-id-1",
        featureId = FEATURE_ID_STRING,
        featureVersion = 3,
        userId = USER_ID,
        variationId = "b59a19d5-f4b1-47f8-a46e-6d9ca14740c1",
        variationValue = "value-2",
        reason = BKTEvaluation.Reason.RULE,
      ),
    )
  }
}
