package io.bucketeer.sdk.android.internal.evaluation

import android.os.Handler
import android.os.Looper
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.IdGeneratorImpl
import io.bucketeer.sdk.android.internal.evaluation.storage.EvaluationStorage
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsErrorResponse
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsResponse
import io.bucketeer.sdk.android.internal.remote.ApiClient
import io.bucketeer.sdk.android.internal.remote.GetEvaluationsResult
import io.bucketeer.sdk.android.internal.remote.RegisterEventsResult
import io.bucketeer.sdk.android.internal.remote.UserEvaluationCondition
import io.bucketeer.sdk.android.mocks.evaluationEvent1
import io.bucketeer.sdk.android.mocks.user1Evaluations
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EvaluationInteractorCaptureErrorTests {
  @Test
  fun captureErrors() {
    TestCase.values().forEach { case ->
      val interactor =
        EvaluationInteractor(
          apiClient = case.apiClient,
          evaluationStorage = case.storage,
          idGenerator = IdGeneratorImpl(),
          featureTag = "feature_tag_value",
          mainHandler = Handler(Looper.getMainLooper()),
        )

      val user =
        User(
          id = "test_user_1",
        )

      val result = interactor.fetch(user, timeoutMillis = 3000L)
      assert(result is GetEvaluationsResult.Failure)
      if (result is GetEvaluationsResult.Failure) {
        // BKTException didn't implement equal() & hash()
        assert(result.featureTag == case.expected.featureTag)
        assert(result.error::class == case.expected.error::class)
        assert(result.error.message == case.expected.error.message)
      }
    }
  }

  @Suppress("unused")
  enum class TestCase(
    internal val apiClient: ApiClient,
    internal val storage: EvaluationStorage,
    val expected: GetEvaluationsResult.Failure,
  ) {
    API_ERROR(
      apiClient = MockErrorAPIClient(),
      storage = MockEvaluationStorage("test_user_1"),
      expected =
        GetEvaluationsResult.Failure(
          error =
            BKTException.TimeoutException(
              message = "timeout",
              cause = Exception("network error"),
              timeoutMillis = 3000L,
            ),
          featureTag = "feature_tag_value",
        ),
    ),
    STORAGE_ERROR_1(
      apiClient = MockReturnSuccessAPIClient(),
      storage = MockEvaluationStorage("test_user_1", deleteAllAndInsertError = Exception("deleteAllAndInsert")),
      expected =
        GetEvaluationsResult.Failure(
          BKTException.IllegalStateException("failed when fetching evaluations: deleteAllAndInsert"),
          "feature_tag_value",
        ),
    ),
    STORAGE_ERROR_2(
      apiClient = MockReturnSuccessAPIClient(),
      storage = MockEvaluationStorage("test_user_1", getUserAttributesUpdatedError = Exception("getUserAttributesUpdatedError")),
      expected =
        GetEvaluationsResult.Failure(
          BKTException.IllegalStateException("failed when fetching evaluations: getUserAttributesUpdatedError"),
          "feature_tag_value",
        ),
    ),
    STORAGE_ERROR_3(
      apiClient = MockReturnSuccessAPIClient(),
      storage = MockEvaluationStorage("test_user_1", clearUserAttributesUpdatedError = Exception("clearUserAttributesUpdatedError")),
      expected =
        GetEvaluationsResult.Failure(
          BKTException.IllegalStateException("failed when fetching evaluations: clearUserAttributesUpdatedError"),
          "feature_tag_value",
        ),
    ),
  }
}

private class MockReturnSuccessAPIClient : ApiClient {
  override fun getEvaluations(
    user: User,
    userEvaluationsId: String,
    timeoutMillis: Long?,
    condition: UserEvaluationCondition,
  ): GetEvaluationsResult =
    GetEvaluationsResult.Success(
      value =
        GetEvaluationsResponse(
          evaluations = user1Evaluations,
          userEvaluationsId = "user_evaluation_id",
        ),
      sizeByte = 706,
      seconds = 1.0,
      featureTag = "feature_tag_value",
    )

  override fun registerEvents(events: List<Event>): RegisterEventsResult =
    RegisterEventsResult.Success(
      RegisterEventsResponse(
        errors =
          mapOf(
            evaluationEvent1.id to
              RegisterEventsErrorResponse(
                retriable = true,
                message = "error",
              ),
          ),
      ),
    )
}

private class MockErrorAPIClient : ApiClient {
  override fun getEvaluations(
    user: User,
    userEvaluationsId: String,
    timeoutMillis: Long?,
    condition: UserEvaluationCondition,
  ): GetEvaluationsResult =
    GetEvaluationsResult.Failure(
      error =
        BKTException.TimeoutException(
          message = "timeout",
          cause = Exception("network error"),
          timeoutMillis = 3000L,
        ),
      featureTag = "feature_tag_value",
    )

  override fun registerEvents(events: List<Event>): RegisterEventsResult =
    RegisterEventsResult.Failure(
      error =
        BKTException.TimeoutException(
          message = "timeout",
          cause = Exception("network error"),
          timeoutMillis = 3000L,
        ),
    )
}

private class MockEvaluationStorage(
  override val userId: String,
  val clearUserAttributesUpdatedError: Throwable? = null,
  val getUserAttributesUpdatedError: Throwable? = null,
  val deleteAllAndInsertError: Throwable? = null,
) : EvaluationStorage {
  private var featureTag = ""

  override fun getCurrentEvaluationId(): String = ""

  override fun clearCurrentEvaluationId() {}

  override fun setUserAttributesUpdated() {}

  override fun clearUserAttributesUpdated() {
    if (clearUserAttributesUpdatedError != null) {
      throw clearUserAttributesUpdatedError
    }
  }

  override fun getUserAttributesUpdated(): Boolean {
    if (getUserAttributesUpdatedError != null) {
      throw getUserAttributesUpdatedError
    }
    return false
  }

  override fun getEvaluatedAt(): String = "0"

  override fun getFeatureTag(): String = featureTag

  override fun setFeatureTag(tag: String) {
    featureTag = tag
  }

  override fun getBy(featureId: String): Evaluation? = null

  override fun get(): List<Evaluation> = emptyList()

  override fun deleteAllAndInsert(
    evaluationsId: String,
    evaluations: List<Evaluation>,
    evaluatedAt: String,
  ) {
    if (deleteAllAndInsertError != null) {
      throw deleteAllAndInsertError
    }
  }

  override fun update(
    evaluationsId: String,
    evaluations: List<Evaluation>,
    archivedFeatureIds: List<String>,
    evaluatedAt: String,
  ): Boolean = true

  override fun refreshCache() {}
}
