package io.bucketeer.sdk.android.internal.evaluation

import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.internal.di.ComponentImpl
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.di.InteractorModule
import io.bucketeer.sdk.android.internal.model.request.GetEvaluationsRequest
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.remote.GetEvaluationsResult
import io.bucketeer.sdk.android.internal.remote.UserEvaluationCondition
import io.bucketeer.sdk.android.mocks.evaluation1
import io.bucketeer.sdk.android.mocks.evaluation2
import io.bucketeer.sdk.android.mocks.evaluation2ForUpdate
import io.bucketeer.sdk.android.mocks.evaluation3
import io.bucketeer.sdk.android.mocks.evaluation4
import io.bucketeer.sdk.android.mocks.user1
import io.bucketeer.sdk.android.mocks.user1Evaluations
import io.bucketeer.sdk.android.mocks.user1EvaluationsForceUpdate
import io.bucketeer.sdk.android.mocks.user1EvaluationsUpsert
import io.bucketeer.sdk.android.mocks.user2
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.LooperMode.Mode

@RunWith(RobolectricTestRunner::class)
class EvaluationInteractorTest {
  private lateinit var server: MockWebServer

  private lateinit var component: ComponentImpl
  private lateinit var moshi: Moshi

  private lateinit var interactor: EvaluationInteractor

  @Before
  fun setup() {
    server = MockWebServer()

    val config = BKTConfig.builder()
      .apiEndpoint(server.url("").toString())
      .apiKey("api_key_value")
      .featureTag("feature_tag_value")
      .appVersion("1.2.3")
      .build()

    component = ComponentImpl(
      dataModule = DataModule(
        application = ApplicationProvider.getApplicationContext(),
        user = user1,
        config = config,
        inMemoryDB = true,
      ),
      interactorModule = InteractorModule(
        mainHandler = Handler(Looper.getMainLooper()),
      ),
    )

    interactor = component.evaluationInteractor
    moshi = component.dataModule.moshi
  }

  @After
  fun tearDown() {
    server.shutdown()
    component.dataModule.sharedPreferences.edit()
      .clear()
      .commit()
  }

  @Test
  fun `checking evaluation condition after init`() {
    // the featureTag should be `feature_tag_value`
    assertThat(interactor.featureTag).isEqualTo("feature_tag_value")
    // the evaluatedAt should be 0
    assertThat(interactor.evaluatedAt).isEqualTo("0")
    // the userAttributesUpdated should be false
    assertThat(interactor.userAttributesUpdated).isEqualTo(false)
  }

  @Test
  fun `set userAttributesUpdated`() {
    interactor.setUserAttributesUpdated()
    // the userAttributesUpdated should be true
    assertThat(interactor.userAttributesUpdated).isEqualTo(true)
  }

  @Test
  fun `clear the userEvaluationsID in the SharedPreferences if the featureTag changes`() {
    interactor.currentEvaluationsId = "should_be_clear"
    assertThat(interactor.currentEvaluationsId).isEqualTo("should_be_clear")
    // feature_tag will be ""
    val configEmptyFeatureTag = BKTConfig.builder()
      .apiEndpoint(server.url("").toString())
      .apiKey("api_key_value")
      .appVersion("1.2.3")
      .build()

    val componentWithEmptyFeatureTag = ComponentImpl(
      dataModule = DataModule(
        application = ApplicationProvider.getApplicationContext(),
        user = user1,
        config = configEmptyFeatureTag,
        inMemoryDB = true,
      ),
      interactorModule = InteractorModule(
        mainHandler = Handler(Looper.getMainLooper()),
      ),
    )

    val interactorWithEmptyFeatureTag = componentWithEmptyFeatureTag.evaluationInteractor
    assertThat(interactorWithEmptyFeatureTag.currentEvaluationsId).isEqualTo("")

    interactorWithEmptyFeatureTag.currentEvaluationsId = "should_be_clear"
    assertThat(interactorWithEmptyFeatureTag.currentEvaluationsId).isEqualTo("should_be_clear")

    val config = BKTConfig.builder()
      .apiEndpoint(server.url("").toString())
      .featureTag("test")
      .apiKey("api_key_value")
      .appVersion("1.2.3")
      .build()

    val component = ComponentImpl(
      dataModule = DataModule(
        application = ApplicationProvider.getApplicationContext(),
        user = user1,
        config = config,
        inMemoryDB = true,
      ),
      interactorModule = InteractorModule(
        mainHandler = Handler(Looper.getMainLooper()),
      ),
    )

    val interactor = component.evaluationInteractor
    assertThat(interactor.currentEvaluationsId).isEqualTo("")
  }

  @Test
  fun `userEvaluationsID should not change if the feature_tag didn't change`() {
    interactor.currentEvaluationsId = "should_not_change"
    assertThat(interactor.currentEvaluationsId).isEqualTo("should_not_change")
    val config = BKTConfig.builder()
      .apiEndpoint(server.url("").toString())
      .featureTag("feature_tag_value")
      .apiKey("api_key_value")
      .appVersion("1.2.3")
      .build()

    val component = ComponentImpl(
      dataModule = DataModule(
        application = ApplicationProvider.getApplicationContext(),
        user = user1,
        config = config,
        inMemoryDB = true,
      ),
      interactorModule = InteractorModule(
        mainHandler = Handler(Looper.getMainLooper()),
      ),
    )

    val interactor = component.evaluationInteractor
    assertThat(interactor.currentEvaluationsId).isEqualTo("should_not_change")
  }

  @Test
  fun `set evaluation condition when requesting`() {
    // initial response(for preparation)
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                evaluations = user1Evaluations,
                userEvaluationsId = "user_evaluations_id_value",
              ),
            ),
        ),
    )
    interactor.evaluatedAt = "10000"
    interactor.fetch(user1, null)

    // assert request
    val firstRequest = server.takeRequest()
    val firstRequestBody = moshi.adapter(GetEvaluationsRequest::class.java)
      .fromJson(firstRequest.body.readString(Charsets.UTF_8))
    assertThat(firstRequestBody).isNotNull()
    assertThat(firstRequestBody!!.userEvaluationCondition).isEqualTo(
      UserEvaluationCondition(
        evaluatedAt = "10000",
        userAttributesUpdated = "false",
      ),
    )

    shadowOf(Looper.getMainLooper()).idle()
    interactor.setUserAttributesUpdated()
    interactor.fetch(user1, null)
    val secondRequest = server.takeRequest()
    val secondRequestBody = moshi.adapter(GetEvaluationsRequest::class.java)
      .fromJson(secondRequest.body.readString(Charsets.UTF_8))
    assertThat(secondRequestBody).isNotNull()
    assertThat(secondRequestBody!!.userEvaluationCondition).isEqualTo(
      UserEvaluationCondition(
        evaluatedAt = "1690798021",
        userAttributesUpdated = "true",
      ),
    )

    shadowOf(Looper.getMainLooper()).idle()
  }


  @Test
  @LooperMode(Mode.PAUSED)
  fun `fetch - initial load`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                evaluations = user1Evaluations,
                userEvaluationsId = "user_evaluations_id_value",
              ),
            ),
        ),
    )

    var listenerCalled = false
    interactor.addUpdateListener {
      assertThat(Looper.myLooper()).isEqualTo(Looper.getMainLooper())
      listenerCalled = true
    }

    assertThat(interactor.currentEvaluationsId).isEmpty()
    // set setUserAttributesUpdated = true
    interactor.setUserAttributesUpdated()

    val result = interactor.fetch(user1, null)

    // assert request
    assertThat(server.requestCount).isEqualTo(1)
    val request = server.takeRequest()
    val requestBody = moshi.adapter(GetEvaluationsRequest::class.java)
      .fromJson(request.body.readString(Charsets.UTF_8))

    assertThat(requestBody!!.userEvaluationsId).isEmpty()
    assertThat(requestBody.tag).isEqualTo(component.dataModule.config.featureTag)
    assertThat(requestBody.userEvaluationCondition).isEqualTo(
      UserEvaluationCondition(
        evaluatedAt = "0",
        userAttributesUpdated = "true",
      ),
    )

    // assert response
    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
    val success = result as GetEvaluationsResult.Success
    assertThat(success.featureTag).isEqualTo("feature_tag_value")
    assertThat(success.value.userEvaluationsId).isEqualTo("user_evaluations_id_value")
    assertThat(success.value.evaluations.evaluations).isEqualTo(listOf(evaluation1, evaluation2))
    assert(success.value.evaluations.forceUpdate)
    assertThat(success.value.evaluations.createdAt).isEqualTo("1690798021")

    assertThat(interactor.currentEvaluationsId).isEqualTo("user_evaluations_id_value")

    assertThat(interactor.evaluations[user1.id]).isEqualTo(listOf(evaluation1, evaluation2))
    val latestEvaluations = component.dataModule.evaluationDao.get(user1.id)
    assertThat(latestEvaluations).isEqualTo(listOf(evaluation1, evaluation2))

    // the featureTag should be `api_key_value`
    assertThat(interactor.featureTag).isEqualTo("feature_tag_value")
    // the evaluatedAt should be updated
    assertThat(interactor.evaluatedAt).isEqualTo("1690798021")
    // the userAttributesUpdated should be false after success request
    assertThat(interactor.userAttributesUpdated).isEqualTo(false)

    shadowOf(Looper.getMainLooper()).idle()

    assertThat(listenerCalled).isTrue()
  }

  //https://github.com/bucketeer-io/android-client-sdk/issues/69
  @Test
  @LooperMode(Mode.PAUSED)
  fun `fetch - force update`() {
    `fetch - initial load`()
    // second response
    val expectResponse = GetEvaluationsResponse(
      evaluations = user1EvaluationsForceUpdate,
      userEvaluationsId = "user_evaluations_id_value_updated",
    )
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              expectResponse,
            ),
        ),
    )

    var listenerCalled = false
    interactor.addUpdateListener {
      assertThat(Looper.myLooper()).isEqualTo(Looper.getMainLooper())
      listenerCalled = true
    }

    val result = interactor.fetch(user1, null)

    // assert request
    assertThat(server.requestCount).isEqualTo(2)

    // assert response
    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
    val response = (result as GetEvaluationsResult.Success).value
    assertThat(response).isEqualTo(expectResponse)

    // check cache should not contain `evaluation1`
    assertThat(interactor.evaluations[user1.id]).isEqualTo(listOf(evaluation2))
    assertThat(interactor.currentEvaluationsId).isEqualTo("user_evaluations_id_value_updated")
    // check database should not contain `evaluation1`
    val latestEvaluations = component.dataModule.evaluationDao.get(user1.id)
    assertThat(latestEvaluations).isEqualTo(listOf(evaluation2))

    // the featureTag should be `api_key_value`
    assertThat(interactor.featureTag).isEqualTo("feature_tag_value")
    // the evaluatedAt should be updated
    assertThat(interactor.evaluatedAt).isEqualTo("1690798025")
    // the userAttributesUpdated should be false after success request
    assertThat(interactor.userAttributesUpdated).isEqualTo(false)

    shadowOf(Looper.getMainLooper()).idle()

    assertThat(listenerCalled).isTrue()
  }

  //https://github.com/bucketeer-io/android-client-sdk/issues/69
  @Test
  @LooperMode(Mode.PAUSED)
  fun `fetch - upsert`() {
    `fetch - initial load`()

    // second response(test target)
    val expectResponse = GetEvaluationsResponse(
      evaluations = user1EvaluationsUpsert,
      userEvaluationsId = "user_evaluations_id_value_updated",
    )
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              expectResponse,
            ),
        ),
    )

    var listenerCalled = false
    interactor.addUpdateListener {
      assertThat(Looper.myLooper()).isEqualTo(Looper.getMainLooper())
      listenerCalled = true
    }

    val result = interactor.fetch(user1, null)

    // assert request
    assertThat(server.requestCount).isEqualTo(2)

    // assert response
    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
    val response = (result as GetEvaluationsResult.Success).value
    assertThat(response).isEqualTo(expectResponse)
    // check cache should not contain `evaluation1`
    assertThat(interactor.evaluations[user1.id]).isEqualTo(
      listOf(
        evaluation2ForUpdate,
        evaluation3, // its a new evaluation
      ),
    )
    assertThat(interactor.currentEvaluationsId).isEqualTo("user_evaluations_id_value_updated")
    // check database should not contain `evaluation1`
    val latestEvaluations = component.dataModule.evaluationDao.get(user1.id)
    assertThat(latestEvaluations).isEqualTo(
      listOf(
        evaluation2ForUpdate,
        evaluation3, // its a new evaluation
      ),
    )

    // the featureTag should be `api_key_value`
    assertThat(interactor.featureTag).isEqualTo("feature_tag_value")
    // the evaluatedAt should be updated
    assertThat(interactor.evaluatedAt).isEqualTo("16907999999")
    // the userAttributesUpdated should be false after success request
    assertThat(interactor.userAttributesUpdated).isEqualTo(false)

    shadowOf(Looper.getMainLooper()).idle()

    assertThat(listenerCalled).isTrue()
  }

  @Test
  @LooperMode(Mode.PAUSED)
  fun `fetch - no update`() {
    // initial response(for preparation)
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                evaluations = user1Evaluations,
                userEvaluationsId = "user_evaluations_id_value",
              ),
            ),
        ),
    )
    interactor.fetch(user1, null)

    shadowOf(Looper.getMainLooper()).idle()

    // second response(test target)
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                evaluations = user1Evaluations,
                userEvaluationsId = "user_evaluations_id_value",
              ),
            ),
        ),
    )

    var listenerCalled = false
    interactor.addUpdateListener {
      // should not reach here, as there's no update
      listenerCalled = true
    }

    val result = interactor.fetch(user1, null)

    assertThat(server.requestCount).isEqualTo(2)

    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)

    assertThat(interactor.currentEvaluationsId).isEqualTo("user_evaluations_id_value")

    assertThat(interactor.evaluations[user1.id]).isEqualTo(listOf(evaluation1, evaluation2))
    val latestEvaluations = component.dataModule.evaluationDao.get(user1.id)
    assertThat(latestEvaluations).isEqualTo(listOf(evaluation1, evaluation2))

    shadowOf(Looper.getMainLooper()).idle()

    assertThat(listenerCalled).isFalse()
  }

  @Test
  fun refreshCache() {
    component.dataModule.evaluationDao.put(user1.id, listOf(evaluation1, evaluation2))
    component.dataModule.evaluationDao.put(user2.id, listOf(evaluation4))

    assertThat(interactor.evaluations).isEmpty()

    interactor.refreshCache(user1.id)

    assertThat(interactor.evaluations).containsExactlyEntriesIn(
      mapOf(user1.id to listOf(evaluation1, evaluation2)),
    )
  }

  @Test
  fun `getLatest - has cache`() {
    component.dataModule.evaluationDao.put(user1.id, listOf(evaluation1, evaluation2))
    component.dataModule.evaluationDao.put(user2.id, listOf(evaluation4))

    interactor.refreshCache(user1.id)

    val actual = interactor.getLatest(user1.id, evaluation1.featureId)

    assertThat(actual).isEqualTo(evaluation1)
  }

  @Test
  fun `getLatest - no cache`() {
    val actual = interactor.getLatest(user1.id, evaluation1.featureId)

    assertThat(actual).isNull()
  }

  @Test
  fun `getLatest - no corresponding evaluation`() {
    component.dataModule.evaluationDao.put(user1.id, listOf(evaluation1))

    interactor.refreshCache(user1.id)

    val actual = interactor.getLatest(user1.id, "invalid_feature_id")

    assertThat(actual).isNull()
  }

  @Test
  fun addUpdateListener() {
    val key1 = interactor.addUpdateListener { /* listener1 */ }
    val key2 = interactor.addUpdateListener { /* listener2 */ }

    assertThat(interactor.updateListeners).hasSize(2)
    assertThat(interactor.updateListeners.keys).containsExactly(key1, key2)
  }

  @Test
  fun removeUpdateListener() {
    val key1 = interactor.addUpdateListener { /* listener1 */ }
    val key2 = interactor.addUpdateListener { /* listener2 */ }

    assertThat(interactor.updateListeners).hasSize(2)

    interactor.removeUpdateListener(key2)

    assertThat(interactor.updateListeners).hasSize(1)
    assertThat(interactor.updateListeners.keys).containsExactly(key1)
  }

  @Test
  fun clearUpdateListener() {
    interactor.addUpdateListener { /* listener1 */ }
    interactor.addUpdateListener { /* listener2 */ }

    assertThat(interactor.updateListeners).hasSize(2)

    interactor.clearUpdateListeners()

    assertThat(interactor.updateListeners).isEmpty()
  }

  fun initialDataBeforeTestingConditionUpdate() {
    //https://github.com/bucketeer-io/android-client-sdk/issues/69
    // initial response(for preparation)
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                evaluations = user1Evaluations,
                userEvaluationsId = "user_evaluations_id_value",
              ),
            ),
        ),
    )
    val firstResult = interactor.fetch(user1, null)
    server.takeRequest()
    assertThat(server.requestCount).isEqualTo(1)

    assertThat(interactor.currentEvaluationsId).isEqualTo("user_evaluations_id_value")
    assertThat(firstResult).isInstanceOf(GetEvaluationsResult.Success::class.java)
    val initialEvaluations =
      (firstResult as GetEvaluationsResult.Success).value.evaluations.evaluations
    assertThat(initialEvaluations).isEqualTo(
      listOf(
        evaluation1,
        evaluation2,
      ),
    )
    // Check cache
    assertThat(interactor.evaluations[user1.id]).isEqualTo(
      listOf(
        evaluation1,
        evaluation2,
      ),
    )

    shadowOf(Looper.getMainLooper()).idle()
  }
}
