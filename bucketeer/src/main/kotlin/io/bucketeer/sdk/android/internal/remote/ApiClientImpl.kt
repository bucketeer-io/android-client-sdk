package io.bucketeer.sdk.android.internal.remote

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.BKTException
import io.bucketeer.sdk.android.internal.logd
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.SourceId
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.model.request.GetEvaluationsRequest
import io.bucketeer.sdk.android.internal.model.request.RegisterEventsRequest
import io.bucketeer.sdk.android.internal.model.response.ErrorResponse
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.model.response.RegisterEventsResponse
import io.bucketeer.sdk.android.internal.util.requireNotNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal const val DEFAULT_REQUEST_TIMEOUT_MILLIS: Long = 30_000

internal class ApiClientImpl(
  apiEndpoint: String,
  private val apiKey: String,
  private val featureTag: String,
  private val moshi: Moshi,
  defaultRequestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
  private val sourceId: SourceId,
  private val sdkVersion: String,
) : ApiClient {
  private val apiEndpoint = apiEndpoint.toHttpUrl()

  private val client: OkHttpClient =
    OkHttpClient
      .Builder()
      .addNetworkInterceptor(FixJsonContentTypeInterceptor())
      .callTimeout(defaultRequestTimeoutMillis, TimeUnit.MILLISECONDS)
      .connectTimeout(defaultRequestTimeoutMillis, TimeUnit.MILLISECONDS)
      .readTimeout(defaultRequestTimeoutMillis, TimeUnit.MILLISECONDS)
      .writeTimeout(defaultRequestTimeoutMillis, TimeUnit.MILLISECONDS)
      .build()

  private val errorResponseJsonAdapter: JsonAdapter<ErrorResponse> by lazy {
    moshi.adapter(ErrorResponse::class.java)
  }

  private val getEvaluationExecutor = Executors.newSingleThreadScheduledExecutor()

  override fun getEvaluations(
    user: User,
    userEvaluationsId: String,
    timeoutMillis: Long?,
    condition: UserEvaluationCondition,
  ): GetEvaluationsResult =
    retryOnException(
      executor = getEvaluationExecutor,
      maxRetries = 3,
      delayMillis = 1000,
      exceptionCheck = { e ->
        val bktException = e as? BKTException
        bktException is BKTException.ClientClosedRequestException
      },
    ) {
      getEvaluationsInternal(
        user = user,
        userEvaluationsId = userEvaluationsId,
        timeoutMillis = timeoutMillis,
        condition = condition,
      )
    }.get()

  private fun getEvaluationsInternal(
    user: User,
    userEvaluationsId: String,
    timeoutMillis: Long?,
    condition: UserEvaluationCondition,
  ): GetEvaluationsResult {
    val body =
      GetEvaluationsRequest(
        tag = featureTag,
        user = user,
        userEvaluationsId = userEvaluationsId,
        userEvaluationCondition = condition,
        sourceId = sourceId,
        sdkVersion = sdkVersion,
      )

    val request =
      Request
        .Builder()
        .url(
          apiEndpoint
            .newBuilder()
            .addPathSegments("get_evaluations")
            .build(),
        ).applyHeaders()
        .post(body = body.toJsonRequestBody())
        .build()

    val actualClient =
      if (timeoutMillis == null) {
        client
      } else {
        client
          .newBuilder()
          .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
          .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
          .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
          .writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
          .build()
      }

    var responseStatusCode = 0
    try {
      val call = actualClient.newCall(request)

      logd { "--> Fetch Evaluation\n$body" }

      val (millis, data) =
        measureTimeMillisWithResult {
          val rawResponse = call.execute()
          responseStatusCode = rawResponse.code

          if (!rawResponse.isSuccessful) {
            throw rawResponse.toBKTException(errorResponseJsonAdapter)
          }

          val response =
            requireNotNull(rawResponse.fromJson<GetEvaluationsResponse>()) { "failed to parse GetEvaluationsResponse" }

          response to (rawResponse.body?.contentLength() ?: -1).toInt()
        }

      val (response, contentLength) = data

      logd { "--> END Fetch Evaluation" }
      logd { "<-- Fetch Evaluation\n$response\n<-- END Evaluation response" }

      return GetEvaluationsResult.Success(
        value = response,
        seconds = millis / 1000.0,
        sizeByte = contentLength,
        featureTag = featureTag,
      )
    } catch (e: Exception) {
      return GetEvaluationsResult.Failure(
        e.toBKTException(
          requestTimeoutMillis = client.callTimeoutMillis.toLong(),
          statusCode = responseStatusCode,
        ),
        featureTag,
      )
    }
  }

  override fun registerEvents(events: List<Event>): RegisterEventsResult =
    retryOnException(
      executor = getEvaluationExecutor,
      maxRetries = 3,
      delayMillis = 1000,
      exceptionCheck = { e ->
        val bktException = e as? BKTException
        bktException is BKTException.ClientClosedRequestException
      },
    ) {
      registerEventsInternal(
        events,
      )
    }.get()

  private fun registerEventsInternal(events: List<Event>): RegisterEventsResult {
    val body = RegisterEventsRequest(events = events, sourceId = sourceId, sdkVersion = sdkVersion)

    val request =
      Request
        .Builder()
        .url(
          apiEndpoint
            .newBuilder()
            .addPathSegments("register_events")
            .build(),
        ).applyHeaders()
        .post(body = body.toJsonRequestBody())
        .build()

    var responseStatusCode = 0
    val result =
      client.newCall(request).runCatching {
        logd { "--> Register events\n$body" }
        val response = execute()
        responseStatusCode = response.code

        if (!response.isSuccessful) {
          val e = response.toBKTException(errorResponseJsonAdapter)
          logd(throwable = e) { "<-- Register events error" }
          throw e
        }

        val result =
          requireNotNull(response.fromJson<RegisterEventsResponse>()) { "failed to parse RegisterEventsResponse" }

        logd { "--> END Register events" }
        logd { "<-- Register events\n$result\n<-- END Register events" }

        RegisterEventsResult.Success(value = result)
      }

    return result.fold(
      onSuccess = { res -> res },
      onFailure = { e ->
        RegisterEventsResult.Failure(
          e.toBKTException(
            requestTimeoutMillis = client.callTimeoutMillis.toLong(),
            statusCode = responseStatusCode,
          ),
        )
      },
    )
  }

  private inline fun <reified T> T.toJson(): String = moshi.adapter(T::class.java).toJson(this)

  private inline fun <reified T> Response.fromJson(): T? {
    val adapter = moshi.adapter(T::class.java)
    return adapter.fromJson(this.body!!.source())
  }

  private inline fun <reified T> T.toJsonRequestBody(): RequestBody =
    this
      .toJson()
      .toRequestBody("application/json".toMediaType())

  private fun Request.Builder.applyHeaders(): Request.Builder = this.header("Authorization", apiKey)
}

/**
 * Interceptor to remove `charset=utf-8` from Content-Type.
 * OkHttp adds `charset=utf-8` to every Content-Type and there's no official way to fix this.
 * This interceptor workarounds this by re-creating request with Content-Type without charset.
 *
 * https://github.com/square/okhttp/issues/3081#issuecomment-508387187
 */
private class FixJsonContentTypeInterceptor : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val original = chain.request()

    val fixed =
      original
        .newBuilder()
        .header("Content-Type", "application/json")
        .build()

    return chain.proceed(fixed)
  }
}

internal fun <T> retryOnException(
  executor: ScheduledExecutorService,
  maxRetries: Int = 3,
  delayMillis: Long = 1000,
  exceptionCheck: (Throwable) -> Boolean,
  block: () -> T,
): Future<T> {
  return executor.submit<T> {
    var lastException: Throwable? = null
    repeat(maxRetries + 1) { attempt ->
      try {
        return@submit block()
      } catch (e: Throwable) {
        lastException = e
        if (!exceptionCheck(e) || attempt >= maxRetries) {
          throw e
        }
        Thread.sleep(delayMillis * (attempt + 1))
      }
    }
    // Tell the compiler that this function can make sure to return T or throw
    // This code below is never reached
    throw lastException!!
  }
}
