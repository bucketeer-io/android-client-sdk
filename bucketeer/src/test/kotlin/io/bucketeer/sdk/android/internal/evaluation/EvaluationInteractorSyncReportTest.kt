package io.bucketeer.sdk.android.internal.evaluation

import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.BKTEvaluationSyncStatus.Source.POLLING
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.evaluation.RecordingSyncReporter.Report
import io.bucketeer.sdk.android.internal.evaluation.storage.EvaluationStorage
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.remote.GetEvaluationsResult
import io.bucketeer.sdk.android.mocks.user1
import io.bucketeer.sdk.android.mocks.user1Evaluations
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * How EvaluationInteractor.fetch() reports to the sync status: a success is reported as soon as
 * the server answers (before the storage write and the "Nothing to sync" return), a failure with
 * the same error fetch() returns, and a storage problem is not reported as a server problem.
 */
@RunWith(RobolectricTestRunner::class)
class EvaluationInteractorSyncReportTest {
  private lateinit var server: MockWebServer
  private lateinit var dataModule: DataModule
  private lateinit var moshi: Moshi
  private lateinit var storage: ThrowingStorage
  private lateinit var reporter: RecordingSyncReporter
  private lateinit var interactor: EvaluationInteractor

  @Before
  fun setup() {
    server = MockWebServer()
    val config =
      BKTConfig
        .builder()
        .apiEndpoint(server.url("").toString())
        .apiKey("api_key_value")
        .featureTag("feature_tag_value")
        .appVersion("1.2.3")
        .build()
    dataModule =
      DataModule(
        application = ApplicationProvider.getApplicationContext(),
        user = user1,
        config = config,
        inMemoryDB = true,
      )
    moshi = dataModule.moshi
    storage = ThrowingStorage(dataModule.evaluationStorage)
    reporter = RecordingSyncReporter()
    interactor =
      EvaluationInteractor(
        apiClient = dataModule.apiClient,
        evaluationStorage = storage,
        idGenerator = dataModule.idGenerator,
        featureTag = config.featureTag,
        mainHandler = Handler(Looper.getMainLooper()),
        syncReporter = reporter,
      )
  }

  @After
  fun tearDown() {
    server.shutdown()
    dataModule.sharedPreferences
      .edit()
      .clear()
      .commit()
    dataModule.destroy()
  }

  @Test
  fun `a successful fetch reports a polling success`() {
    enqueueEvaluations("user_evaluations_id_value")

    val result = interactor.fetch(user1, null)

    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
    assertThat(reporter.reports).containsExactly(Report.Success(POLLING))
  }

  @Test
  fun `a successful fetch with nothing to sync still reports a polling success`() {
    enqueueEvaluations("same_id")
    enqueueEvaluations("same_id")
    interactor.fetch(user1, null)
    reporter.reports.clear()

    // Same userEvaluationsId: fetch() takes the "Nothing to sync" early return.
    val result = interactor.fetch(user1, null)

    assertThat(result).isInstanceOf(GetEvaluationsResult.Success::class.java)
    assertThat(reporter.reports).containsExactly(Report.Success(POLLING))
  }

  @Test
  fun `a storage write failure after a successful fetch is not reported as a server failure`() {
    enqueueEvaluations("user_evaluations_id_value")
    storage.throwOnWrite = true

    val result = interactor.fetch(user1, null)

    // fetch() still returns the storage error, as before.
    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    assertThat(reporter.reports).containsExactly(Report.Success(POLLING))
  }

  @Test
  fun `a failed fetch reports a polling failure with the same error it returns`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(500)
        .setBody(
          moshi
            .adapter(ErrorResponse::class.java)
            .toJson(ErrorResponse(ErrorResponse.ErrorDetail(code = 500, message = "500 error"))),
        ),
    )

    val result = interactor.fetch(user1, null)

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    val report = reporter.reports.single() as Report.Failure
    assertThat(report.source).isEqualTo(POLLING)
    assertThat(report.error).isSameInstanceAs((result as GetEvaluationsResult.Failure).error)
  }

  @Test
  fun `a storage read failure before the request reports nothing`() {
    storage.throwOnRead = true

    val result = interactor.fetch(user1, null)

    assertThat(result).isInstanceOf(GetEvaluationsResult.Failure::class.java)
    assertThat(server.requestCount).isEqualTo(0)
    assertThat(reporter.reports).isEmpty()
  }

  private fun enqueueEvaluations(userEvaluationsId: String) {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          moshi
            .adapter(GetEvaluationsResponse::class.java)
            .toJson(
              GetEvaluationsResponse(
                evaluations = user1Evaluations,
                userEvaluationsId = userEvaluationsId,
              ),
            ),
        ),
    )
  }
}

/** The real storage, with switches that make reads (before the request) or writes throw. */
private class ThrowingStorage(
  private val delegate: EvaluationStorage,
) : EvaluationStorage by delegate {
  var throwOnRead = false
  var throwOnWrite = false

  override fun getFeatureTag(): String {
    if (throwOnRead) throw IllegalStateException("storage read failed")
    return delegate.getFeatureTag()
  }

  override fun deleteAllAndInsert(
    evaluationsId: String,
    evaluations: List<Evaluation>,
    evaluatedAt: String,
  ) {
    if (throwOnWrite) throw IllegalStateException("storage write failed")
    delegate.deleteAllAndInsert(evaluationsId, evaluations, evaluatedAt)
  }

  override fun update(
    evaluationsId: String,
    evaluations: List<Evaluation>,
    archivedFeatureIds: List<String>,
    evaluatedAt: String,
  ): Boolean {
    if (throwOnWrite) throw IllegalStateException("storage write failed")
    return delegate.update(evaluationsId, evaluations, archivedFeatureIds, evaluatedAt)
  }
}
