package io.bucketeer.sdk.android.e2e

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for the proxy server the streaming E2E tests run through ([RecordingProxy]).
 *
 * They check the proxy itself, not the SDK, so they need no backend and no keys: the proxy
 * forwards to a MockWebServer playing the backend, and a plain OkHttp client plays the SDK's
 * side over real loopback HTTP.
 */
class RecordingProxyTest {
  private lateinit var backend: MockWebServer
  private lateinit var proxy: RecordingProxy

  // Counts backend requests the proxy cancelled, the way the proxy's own client sees them.
  private val cancelledCalls = AtomicInteger()
  private val forwardingClient =
    OkHttpClient
      .Builder()
      .eventListener(
        object : EventListener() {
          override fun canceled(call: Call) {
            cancelledCalls.incrementAndGet()
          }
        },
      ).build()

  // The SDK's side. No retries: a refused request must fail, not be sent again.
  private val client =
    OkHttpClient
      .Builder()
      .retryOnConnectionFailure(false)
      .readTimeout(10, TimeUnit.SECONDS)
      .build()

  @Before
  fun setUp() {
    backend = MockWebServer()
    backend.start()
    proxy = RecordingProxy(backend.url("/v1").toString(), forwardingClient)
  }

  @After
  fun tearDown() {
    proxy.stop()
    backend.shutdown()
  }

  @Test
  fun plainHttpReachesTheBackend() {
    backend.enqueue(MockResponse().setBody("ok"))

    client.newCall(get("${proxy.url}/get_evaluations")).execute().use { response ->
      assertThat(response.code).isEqualTo(200)
      assertThat(response.body!!.string()).isEqualTo("ok")
    }
    assertThat(backend.takeRequest(5, TimeUnit.SECONDS)!!.path).isEqualTo("/v1/get_evaluations")
  }

  @Test
  fun streamPiecesArriveOneAtATime() {
    // One byte, then 3 s of silence, then the next byte.
    backend.enqueue(MockResponse().setBody("ab").throttleBody(1, 3, TimeUnit.SECONDS))

    // Timed from the request: a proxy that waits for the whole reply also holds back its head.
    val startedAt = System.currentTimeMillis()
    client.newCall(get("${proxy.url}/stream")).execute().use { response ->
      val body = response.body!!.byteStream()

      // "a" must reach the client while "b" is still 3 s away, not when the reply ends.
      assertThat(body.read()).isEqualTo('a'.code)
      assertWithMessage("the first piece waited for the rest of the reply")
        .that(System.currentTimeMillis() - startedAt)
        .isLessThan(2_000L)
      assertThat(body.read()).isEqualTo('b'.code)
      assertThat(body.read()).isEqualTo(-1)
    }
  }

  @Test
  fun forwardsMethodPathAndBodyButNotConnectionHeaders() {
    backend.enqueue(MockResponse())
    val request =
      Request
        .Builder()
        .url("${proxy.url}/get_evaluations")
        .header("Authorization", "api-key")
        // A byte body, so OkHttp adds no charset: the SDK sends exactly "application/json".
        .post("""{"tag":"android"}""".toByteArray().toRequestBody("application/json".toMediaType()))
        .build()

    client.newCall(request).execute().close()

    val forwarded = backend.takeRequest(5, TimeUnit.SECONDS)!!
    assertThat(forwarded.method).isEqualTo("POST")
    assertThat(forwarded.path).isEqualTo("/v1/get_evaluations")
    assertThat(forwarded.body.readUtf8()).isEqualTo("""{"tag":"android"}""")
    assertThat(forwarded.getHeader("Authorization")).isEqualTo("api-key")
    assertThat(forwarded.getHeader("Content-Type")).isEqualTo("application/json")
    // The client's Host describes its hop to the proxy. Forwarding it would send the backend
    // "127.0.0.1:<port>" of the proxy.
    assertThat(forwarded.getHeader("Host")).isEqualTo("${backend.hostName}:${backend.port}")
    assertThat(forwarded.getHeader("Transfer-Encoding")).isNull()
  }

  @Test
  fun recordsPathAndStatusAndPassesAnErrorStatusBack() {
    backend.enqueue(MockResponse().setResponseCode(401))

    client.newCall(get("${proxy.url}/stream_evaluations")).execute().use { response ->
      assertThat(response.code).isEqualTo(401)
    }
    assertThat(proxy.records.map { it.path }).containsExactly("/v1/stream_evaluations")
    assertThat(proxy.records.map { it.status }).containsExactly(401)
    assertThat(proxy.count("stream_evaluations")).isEqualTo(1)
  }

  @Test
  fun normalEndOfAStreamIsRecordedAsEnded() {
    backend.enqueue(MockResponse().setBody("a"))

    client.newCall(get("${proxy.url}/stream")).execute().use { it.body!!.string() }

    // A normal end is not an error, but the stream is no longer open.
    assertThat(proxy.records.single().status).isEqualTo(200)
    assertThat(proxy.records.single().error).isNull()
    assertWithMessage("a stream the backend ended normally is still recorded as open")
      .that(awaitUntil(5_000) { proxy.records.single().ended })
      .isTrue()
  }

  @Test
  fun backendFailingMidStreamIsRecordedAndEndsTheClientReply() {
    // Sends half the body, then drops the connection.
    backend.enqueue(
      MockResponse()
        .setBody("aaaa")
        .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
    )

    client.newCall(get("${proxy.url}/stream")).execute().use { response ->
      // The reply has no length, so the client reads until the proxy closes the connection.
      assertThat(response.body!!.string()).isEqualTo("aa")
    }
    assertThat(proxy.records.single().status).isEqualTo(200)
    assertWithMessage("a backend failure mid-stream was not recorded")
      .that(awaitUntil(5_000) { proxy.records.single().error != null })
      .isTrue()
    assertThat(proxy.records.single().ended).isTrue()
  }

  @Test
  fun unreachableBackendIsRecordedAndClosesTheClientConnection() {
    val closedBackend = MockWebServer()
    closedBackend.start()
    val upstream = closedBackend.url("/v1").toString()
    closedBackend.shutdown()
    val unreachableProxy = RecordingProxy(upstream, forwardingClient)

    try {
      val failure = runCatching { client.newCall(get("${unreachableProxy.url}/get_evaluations")).execute() }
      assertThat(failure.exceptionOrNull()).isInstanceOf(IOException::class.java)
      assertThat(unreachableProxy.records.single().status).isNull()
      assertThat(unreachableProxy.records.single().error).isNotNull()
    } finally {
      unreachableProxy.stop()
    }
  }

  @Test
  fun clientClosingTheStreamCancelsTheBackendRequest() {
    // A long, slow body: the backend keeps the stream open until someone cancels it.
    backend.enqueue(MockResponse().setBody("a".repeat(1_000)).throttleBody(1, 1, TimeUnit.SECONDS))

    val call = client.newCall(get("${proxy.url}/stream"))
    val response = call.execute()
    assertThat(response.body!!.byteStream().read()).isEqualTo('a'.code)
    assertWithMessage("an open stream is recorded as ended")
      .that(proxy.records.single().ended)
      .isFalse()

    // What the SDK does when it reconnects: drop its connection.
    call.cancel()
    response.close()

    assertWithMessage("the backend request was not cancelled after the client closed its connection")
      .that(awaitUntil(5_000) { cancelledCalls.get() == 1 })
      .isTrue()
    assertWithMessage("a stream the client closed is still recorded as open")
      .that(awaitUntil(5_000) { proxy.records.single().ended })
      .isTrue()
  }

  @Test
  fun stopEndsAnOpenStreamAndRefusesNewRequests() {
    backend.enqueue(MockResponse().setBody("a".repeat(1_000)).throttleBody(1, 1, TimeUnit.SECONDS))

    client.newCall(get("${proxy.url}/stream")).execute().use { response ->
      val body = response.body!!.byteStream()
      assertThat(body.read()).isEqualTo('a'.code)

      proxy.stop()

      // The reply ends: either the connection reads to its end or it fails.
      val rest = runCatching { body.readBytes() }
      assertWithMessage("stop() left the stream open").that(rest.isSuccess || rest.exceptionOrNull() is IOException).isTrue()
    }
    assertWithMessage("stop() did not cancel the backend request")
      .that(awaitUntil(5_000) { cancelledCalls.get() == 1 })
      .isTrue()
    assertWithMessage("a stream stop() closed is still recorded as open")
      .that(proxy.records.single().ended)
      .isTrue()

    val late = runCatching { client.newCall(get("${proxy.url}/get_evaluations")).execute() }
    assertThat(late.exceptionOrNull()).isInstanceOf(IOException::class.java)
    assertThat(proxy.records).hasSize(1)
  }

  private fun get(url: String): Request = Request.Builder().url(url).build()

  private fun awaitUntil(
    timeoutMillis: Long,
    condition: () -> Boolean,
  ): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
      if (condition()) return true
      Thread.sleep(50)
    }
    return condition()
  }
}
