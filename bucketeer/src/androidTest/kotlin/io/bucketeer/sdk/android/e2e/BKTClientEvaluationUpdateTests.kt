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
  fun testUserEvaluationsIdMismatchAndEvaluatedAtTooOld() {
    // "userEvaluationsId is different and evaluatedAt is too old"
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
    val evaluationSQLDao = (client.component as ComponentImpl).dataModule.evaluationSQLDao
    val evaluationStorage = (client.component as ComponentImpl).dataModule.evaluationStorage
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
    evaluationStorage.deleteAllAndInsert("", listOf(tobeRemoveEvaluation), "0")
    assert(evaluationStorage.get() == listOf(tobeRemoveEvaluation))
    assert(evaluationSQLDao.get(USER_ID).contains(tobeRemoveEvaluation))

    evaluationStorage.clearCurrentEvaluationId()
    assertThat(evaluationStorage.getCurrentEvaluationId()).isEmpty()
    assertThat(evaluationStorage.getEvaluatedAt()).isEqualTo("0")

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
    val evaluationStorageWithNewTag = (clientWithNewTag.component as ComponentImpl).dataModule.evaluationStorage
    // Should not contain the previous data
    assert(evaluationStorageWithNewTag.get().contains(tobeRemoveEvaluation).not())
    assertThat(evaluationStorageWithNewTag.getCurrentEvaluationId()).isNotEmpty()
    assertThat(evaluationStorageWithNewTag.getEvaluatedAt()).isNotEmpty()
  }

  @Test
  @UiThreadTest
  fun testInitializeWithNewFeatureTag() {
    // userEvaluationId is empty after feature_tag changed
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
    val evaluationSQLDao = (client.component as ComponentImpl).dataModule.evaluationSQLDao
    val evaluationStorage = (client.component as ComponentImpl).dataModule.evaluationStorage
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
    evaluationStorage.deleteAllAndInsert("", listOf(tobeRemoveEvaluation), "0")
    assert(evaluationStorage.get() == listOf(tobeRemoveEvaluation))
    assert(evaluationSQLDao.get(USER_ID).contains(tobeRemoveEvaluation))
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
    val evaluationSQLDaoWithNewTag = (clientWithNewTag.component as ComponentImpl).dataModule.evaluationSQLDao
    val evaluationStorageWithNewTag = (clientWithNewTag.component as ComponentImpl).dataModule.evaluationStorage
    // Should not contain the previous data
    assert(evaluationSQLDaoWithNewTag.get(USER_ID).contains(tobeRemoveEvaluation).not())
    assert(evaluationStorageWithNewTag.get().contains(tobeRemoveEvaluation).not())
  }

  @Test
  fun testInitializeWithNewFeatureTagInNoneUIThread() {
    // One more test to see what happen with we destroy & reinitialize the sdk from a normal thread.
    testInitializeWithNewFeatureTag()
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
