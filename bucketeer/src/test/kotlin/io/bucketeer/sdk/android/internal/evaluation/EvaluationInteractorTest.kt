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
import io.bucketeer.sdk.android.mocks.evaluation1
import io.bucketeer.sdk.android.mocks.evaluation2
import io.bucketeer.sdk.android.mocks.evaluation3
import io.bucketeer.sdk.android.mocks.user1
import io.bucketeer.sdk.android.mocks.user1Evaluations
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

    component = ComponentImpl(
      dataModule = DataModule(
        application = ApplicationProvider.getApplicationContext(),
        user = user1,
        config = BKTConfig.builder()
          .apiEndpoint(server.url("").toString())
          .apiKey("api_key_value")
          .featureTag("feature_tag_value")
          .appVersion("1.2.3")
          .build(),
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

    val result = interactor.fetch(user1, null)

    // assert request
    assertThat(server.requestCount).isEqualTo(1)
    val request = server.takeRequest()
    val requestBody = moshi.adapter(GetEvaluationsRequest::class.java)
      .fromJson(request.body.readString(Charsets.UTF_8))

    assertThat(requestBody!!.userEvaluationsId).isEmpty()
    assertThat(requestBody.tag).isEqualTo(component.dataModule.config.featureTag)
    assertThat(requestBody.user).isEqualTo(user1)

    // assert response
    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)

    assertThat(interactor.currentEvaluationsId).isEqualTo("user_evaluations_id_value")

    assertThat(interactor.evaluations[user1.id]).isEqualTo(listOf(evaluation1, evaluation2))
    val latestEvaluations = component.dataModule.evaluationDao.get(user1.id)
    assertThat(latestEvaluations).isEqualTo(listOf(evaluation1, evaluation2))

    shadowOf(Looper.getMainLooper()).idle()

    assertThat(listenerCalled).isTrue()
  }

  @Test
  @LooperMode(Mode.PAUSED)
  fun `fetch - update`() {
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

    val newEvaluation = evaluation1.copy(
      variationValue = evaluation1.variationValue + "_updated",
    )
    // second response(test target)
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi.adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                evaluations = user1Evaluations.copy(
                  evaluations = listOf(newEvaluation),
                ),
                userEvaluationsId = "user_evaluations_id_value_updated",
              ),
            ),
        ),
    )

    var listenerCalled = false
    interactor.addUpdateListener {
      assertThat(Looper.myLooper()).isEqualTo(Looper.getMainLooper())
      listenerCalled = true
    }

    val result = interactor.fetch(user1, null)

    assertThat(server.requestCount).isEqualTo(2)

    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)

    assertThat(interactor.currentEvaluationsId).isEqualTo("user_evaluations_id_value_updated")

    assertThat(interactor.evaluations[user1.id]).isEqualTo(listOf(newEvaluation))
    val latestEvaluations = component.dataModule.evaluationDao.get(user1.id)
    assertThat(latestEvaluations).isEqualTo(listOf(newEvaluation))

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
    component.dataModule.evaluationDao.put(user2.id, listOf(evaluation3))

    assertThat(interactor.evaluations).isEmpty()

    interactor.refreshCache(user1.id)

    assertThat(interactor.evaluations).containsExactlyEntriesIn(
      mapOf(user1.id to listOf(evaluation1, evaluation2)),
    )
  }

  @Test
  fun `getLatest - has cache`() {
    component.dataModule.evaluationDao.put(user1.id, listOf(evaluation1, evaluation2))
    component.dataModule.evaluationDao.put(user2.id, listOf(evaluation3))

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
}
