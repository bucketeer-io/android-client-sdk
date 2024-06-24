@file:Suppress("DEPRECATION")

package io.bucketeer.sdk.android.e2e

import android.content.Context
import androidx.test.annotation.UiThreadTest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.BKTEvaluation
import io.bucketeer.sdk.android.BKTEvaluationDetails
import io.bucketeer.sdk.android.BKTUser
import io.bucketeer.sdk.android.BuildConfig
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.database.OpenHelperCallback
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BKTClientVariationTest {
  private lateinit var context: Context
  private lateinit var config: BKTConfig
  private lateinit var user: BKTUser

  @Before
  @UiThreadTest
  fun setup() {
    context = ApplicationProvider.getApplicationContext()

    config =
      BKTConfig.builder()
        .apiKey(BuildConfig.API_KEY)
        .apiEndpoint(BuildConfig.API_ENDPOINT)
        .featureTag(FEATURE_TAG)
        .appVersion("1.2.3")
        .build()

    user =
      BKTUser.builder()
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
  fun stringVariation() {
    val result =
      BKTClient.getInstance()
        .stringVariation(FEATURE_ID_STRING, "test")
    assertThat(result).isEqualTo("value-1")
  }

  @Test
  fun stringVariation_detail() {
    val actual = BKTClient.getInstance().evaluationDetails(FEATURE_ID_STRING)
    assertEvaluation(
      actual,
      BKTEvaluation(
        id = "feature-android-e2e-string:4:bucketeer-android-user-id-1",
        featureId = FEATURE_ID_STRING,
        featureVersion = 4,
        userId = USER_ID,
        variationId = "36a53a17-60b4-4a99-a54a-7fcbf21f7c8c",
        variationName = "variation 1",
        variationValue = "value-1",
        reason = BKTEvaluation.Reason.DEFAULT,
      ),
    )

    assertThat(
      BKTClient.getInstance().stringEvaluationDetails(FEATURE_ID_STRING, defaultValue = "1"),
    ).isEqualTo(
      BKTEvaluationDetails(
        featureId = FEATURE_ID_STRING,
        featureVersion = 4,
        userId = USER_ID,
        variationId = "36a53a17-60b4-4a99-a54a-7fcbf21f7c8c",
        variationName = "variation 1",
        variationValue = "value-1",
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )
  }

  @Test
  fun intVariation() {
    val result =
      BKTClient.getInstance()
        .intVariation(FEATURE_ID_INT, 0)
    assertThat(result).isEqualTo(10)
  }

  @Test
  fun intVariation_detail() {
    val actual = BKTClient.getInstance().evaluationDetails(FEATURE_ID_INT)
    assertEvaluation(
      actual,
      BKTEvaluation(
        id = "feature-android-e2e-int:3:bucketeer-android-user-id-1",
        featureId = FEATURE_ID_INT,
        featureVersion = 3,
        userId = USER_ID,
        variationId = "9b9a4396-d2ec-4eaf-aee6-ca0276881120",
        variationName = "variation 10",
        variationValue = "10",
        reason = BKTEvaluation.Reason.DEFAULT,
      ),
    )

    assertThat(
      BKTClient.getInstance().intEvaluationDetails(FEATURE_ID_INT, defaultValue = 1),
    ).isEqualTo(
      BKTEvaluationDetails(
        featureId = FEATURE_ID_INT,
        featureVersion = 3,
        userId = USER_ID,
        variationId = "9b9a4396-d2ec-4eaf-aee6-ca0276881120",
        variationName = "variation 10",
        variationValue = 10,
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )
  }

  @Test
  fun doubleVariation() {
    val result =
      BKTClient.getInstance()
        .doubleVariation(FEATURE_ID_DOUBLE, 0.1)
    assertThat(result).isEqualTo(2.1)
  }

  @Test
  fun doubleVariation_detail() {
    val actual = BKTClient.getInstance().evaluationDetails(FEATURE_ID_DOUBLE)
    assertEvaluation(
      actual,
      BKTEvaluation(
        id = "feature-android-e2e-double:3:bucketeer-android-user-id-1",
        featureId = FEATURE_ID_DOUBLE,
        featureVersion = 3,
        userId = USER_ID,
        variationId = "384bbcf0-0d1d-4e7a-b589-850f16f833b4",
        variationName = "variation 2.1",
        variationValue = "2.1",
        reason = BKTEvaluation.Reason.DEFAULT,
      ),
    )

    assertThat(
      BKTClient.getInstance().doubleEvaluationDetails(FEATURE_ID_DOUBLE, defaultValue = 3.4),
    ).isEqualTo(
      BKTEvaluationDetails(
        featureId = FEATURE_ID_DOUBLE,
        featureVersion = 3,
        userId = USER_ID,
        variationId = "384bbcf0-0d1d-4e7a-b589-850f16f833b4",
        variationName = "variation 2.1",
        variationValue = 2.1,
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )
  }

  @Test
  fun booleanVariation() {
    val result =
      BKTClient.getInstance()
        .booleanVariation(FEATURE_ID_BOOLEAN, false)
    assertThat(result).isTrue()
  }

  @Test
  fun booleanVariation_detail() {
    val actual = BKTClient.getInstance().evaluationDetails(FEATURE_ID_BOOLEAN)
    assertEvaluation(
      actual,
      BKTEvaluation(
        id = "feature-android-e2e-boolean:3:bucketeer-android-user-id-1",
        featureId = FEATURE_ID_BOOLEAN,
        featureVersion = 3,
        userId = USER_ID,
        variationId = "774fb34d-5b08-4305-9995-08cdac47aa0f",
        variationName = "variation true",
        variationValue = "true",
        reason = BKTEvaluation.Reason.DEFAULT,
      ),
    )

    assertThat(
      BKTClient.getInstance().boolEvaluationDetails(FEATURE_ID_BOOLEAN, defaultValue = true),
    ).isEqualTo(
      BKTEvaluationDetails(
        featureId = FEATURE_ID_BOOLEAN,
        featureVersion = 3,
        userId = USER_ID,
        variationId = "774fb34d-5b08-4305-9995-08cdac47aa0f",
        variationName = "variation true",
        variationValue = true,
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )
  }

  @Test
  fun jsonVariation() {
    val result =
      BKTClient.getInstance()
        .jsonVariation(FEATURE_ID_JSON, JSONObject())

    val keys = result.keys().asSequence().toList()
    val values = keys.map { result.get(it) }
    assertThat(keys).isEqualTo(listOf("key"))
    assertThat(values).isEqualTo(listOf("value-1"))
  }

  @Test
  fun jsonVariation_detail() {
    val actual = BKTClient.getInstance().evaluationDetails(FEATURE_ID_JSON)
    assertEvaluation(
      actual,
      BKTEvaluation(
        id = "feature-android-e2e-json:3:bucketeer-android-user-id-1",
        featureId = FEATURE_ID_JSON,
        featureVersion = 3,
        userId = USER_ID,
        variationId = "4499d1ca-411d-4ec6-9ae8-df51087e72bb",
        variationName = "variation 1",
        variationValue = """{ "key": "value-1" }""",
        reason = BKTEvaluation.Reason.DEFAULT,
      ),
    )

    val actualEvaluationDetails =
      BKTClient.getInstance()
        .jsonEvaluationDetails(FEATURE_ID_JSON, defaultValue = JSONObject("""{ "key1": "value-2" }"""))

    assertThat(
      actualEvaluationDetails,
    ).isEqualTo(
      BKTEvaluationDetails(
        featureId = FEATURE_ID_JSON,
        featureVersion = 3,
        userId = USER_ID,
        variationId = "4499d1ca-411d-4ec6-9ae8-df51087e72bb",
        variationName = "variation 1",
        variationValue = JSONObject("""{ "key": "value-1" }"""),
        reason = BKTEvaluationDetails.Reason.DEFAULT,
      ),
    )
  }
}
