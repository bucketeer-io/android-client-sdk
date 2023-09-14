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
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.internal.model.Reason
import io.bucketeer.sdk.android.internal.model.ReasonType
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BKTClientEvaluationUpdateTests {
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


  @Test
  @UiThreadTest
  // "userEvaluationsId is different and evaluatedAt is too old"
  fun testUserEvaluationsIdMismatchAndEvaluatedAtTooOld() {
    config = BKTConfig.builder()
      .apiKey(BuildConfig.API_KEY)
      .apiEndpoint(BuildConfig.API_ENDPOINT)
      .featureTag("Android")
      .appVersion("1.2.3")
      .build()

    user = BKTUser.builder()
      .id(USER_ID)
      .build()

    val result = BKTClient.initialize(context, config, user).get()

    assertThat(result).isNull()
    val client = BKTClient.getInstance() as BKTClientImpl
    val evaluationDao = (client.component as ComponentImpl).dataModule.evaluationDao
    val evaluationInteractor = client.component.evaluationInteractor
    val tobeRemoveEvaluation = Evaluation(
      id = "test-feature-2:9:user id 1",
      featureId = "test-feature-2",
      featureVersion = 9,
      userId = "user id 1",
      variationId = "test-feature-2-variation-A",
      variationName = "test variation name2",
      variationValue = "test variation value2",
      reason = Reason(
        type = ReasonType.DEFAULT,
      ),
    )
    evaluationDao.put(USER_ID, listOf(tobeRemoveEvaluation))
    assert(evaluationDao.get(USER_ID).contains(tobeRemoveEvaluation))

    evaluationInteractor.evaluatedAt = "0"
    evaluationInteractor.currentEvaluationsId = ""
    assertThat(evaluationInteractor.currentEvaluationsId).isEmpty()
    assertThat(evaluationInteractor.evaluatedAt).isEqualTo("0")

    // Prepare for switch tag
    BKTClient.destroy()

    val configWithNewTag = BKTConfig.builder()
      .apiKey(BuildConfig.API_KEY)
      .apiEndpoint(BuildConfig.API_ENDPOINT)
      .featureTag(FEATURE_TAG)
      .appVersion("1.2.3")
      .build()

    val resultWithNewTag = BKTClient.initialize(context, configWithNewTag, user).get()

    assertThat(resultWithNewTag).isNull()
    val clientWithNewTag = BKTClient.getInstance() as BKTClientImpl
    val evaluationDaoWithNewTag = (clientWithNewTag.component as ComponentImpl).dataModule.evaluationDao
    val evaluationInteractorWithNewTag = client.component.evaluationInteractor
    // Should not contain the previous data
    assert(evaluationDaoWithNewTag.get(USER_ID).contains(tobeRemoveEvaluation).not())
    assertThat(evaluationInteractorWithNewTag.currentEvaluationsId).isNotEmpty()
    assertThat(evaluationInteractorWithNewTag.evaluatedAt).isNotEmpty()
  }

  @Test
  @UiThreadTest
  // userEvaluationId is empty after feature_tag changed
  fun testInitializeWithNewFeatureTag() {
    config = BKTConfig.builder()
      .apiKey(BuildConfig.API_KEY)
      .apiEndpoint(BuildConfig.API_ENDPOINT)
      .featureTag("Android_E2E_TEST_2023")
      .appVersion("1.2.3")
      .build()

    user = BKTUser.builder()
      .id(USER_ID)
      .build()

    val result = BKTClient.initialize(context, config, user).get()

    assertThat(result).isNull()
    val client = BKTClient.getInstance() as BKTClientImpl
    val evaluationDao = (client.component as ComponentImpl).dataModule.evaluationDao
    val tobeRemoveEvaluation = Evaluation(
      id = "test-feature-2:9:user id 1",
      featureId = "test-feature-2",
      featureVersion = 9,
      userId = "user id 1",
      variationId = "test-feature-2-variation-A",
      variationName = "test variation name2",
      variationValue = "test variation value2",
      reason = Reason(
        type = ReasonType.DEFAULT,
      ),
    )
    evaluationDao.put(USER_ID, listOf(tobeRemoveEvaluation))
    assert(evaluationDao.get(USER_ID).contains(tobeRemoveEvaluation))

    // Prepare for switch tag
    BKTClient.destroy()

    val configWithNewTag = BKTConfig.builder()
      .apiKey(BuildConfig.API_KEY)
      .apiEndpoint(BuildConfig.API_ENDPOINT)
      .featureTag(FEATURE_TAG)
      .appVersion("1.2.3")
      .build()

    val resultWithNewTag = BKTClient.initialize(context, configWithNewTag, user).get()

    assertThat(resultWithNewTag).isNull()
    val clientWithNewTag = BKTClient.getInstance() as BKTClientImpl
    val evaluationDaoWithNewTag = (clientWithNewTag.component as ComponentImpl).dataModule.evaluationDao
    // Should not contain the previous data
    assert(evaluationDaoWithNewTag.get(USER_ID).contains(tobeRemoveEvaluation).not())
  }

  @Test
  @UiThreadTest
  fun testInitWithoutFeatureTagShouldRetrievesAllFeatures() {
    config = BKTConfig.builder()
      .apiKey(BuildConfig.API_KEY)
      .apiEndpoint(BuildConfig.API_ENDPOINT)
      .appVersion("1.2.3")
      .build()

    user = BKTUser.builder()
      .id(USER_ID)
      .build()

    val result = BKTClient.initialize(context, config, user).get()
    assertThat(result).isNull()

    val client = BKTClient.getInstance()
    val android = client.evaluationDetails("feature-android-e2e-string")
    assertThat(android).isNotNull()
    val golang = client.evaluationDetails("feature-go-server-e2e-1")
    assertThat(golang).isNotNull()
    val javascript = client.evaluationDetails("feature-js-e2e-string")
    assertThat(javascript).isNotNull()
  }
}
