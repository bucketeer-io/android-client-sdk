package io.bucketeer.sdk.android.internal.streaming

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.bucketeer.sdk.android.BKTLogger
import io.bucketeer.sdk.android.internal.LoggerHolder
import io.bucketeer.sdk.android.internal.remote.FixJsonContentTypeInterceptor
import io.bucketeer.sdk.android.internal.streaming.fakes.FakeStreamScheduler
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OkHttpEventSourceTest {
  private lateinit var server: MockWebServer
  private lateinit var executor: ExecutorService
  private lateinit var logger: RecordingLogger
  private val client = createStreamHttpClient()
  private val sources = mutableListOf<OkHttpEventSource>()

  @Before
  fun setUp() {
    // The event source logs with logd: no Android logger may be left over from another test.
    LoggerHolder.clearLoggers()
    logger = RecordingLogger()
    LoggerHolder.addLogger(logger)
    server = MockWebServer()
    server.start()
    executor = Executors.newSingleThreadExecutor { Thread(it, EXECUTOR_THREAD) }
  }

  @After
  fun tearDown() {
    sources.forEach { it.close() }
    executor.shutdownNow()
    server.shutdown()
    LoggerHolder.clearLoggers()
  }

  @Test
  fun `successful response opens and onMessage receives data`() {
    server.enqueue(sse("data: hello\n\n"))
    val (es, r) = newSource()

    es.open(request())

    r.awaitEvent("error:null")
    assertThat(r.nonBytes()).containsExactly("open", "message:hello", "error:null").inOrder()
  }

  @Test
  fun `named event goes to its listener not onMessage`() {
    server.enqueue(sse("event: put\ndata: p1\n\n"))
    val (es, r) = newSource()

    es.open(request())

    r.awaitEvent("error:null")
    assertThat(r.nonBytes()).containsExactly("open", "put:p1", "error:null").inOrder()
  }

  @Test
  fun `named event with no listener is dropped`() {
    server.enqueue(sse("event: other\ndata: x\n\nevent: put\ndata: p1\n\n"))
    val (es, r) = newSource()

    es.open(request())

    r.awaitEvent("error:null")
    // put (which has a listener) is the positive control.
    assertThat(r.nonBytes()).containsExactly("open", "put:p1", "error:null").inOrder()
  }

  @Test
  fun `explicit message event reaches onMessage`() {
    server.enqueue(sse("event: message\ndata: m1\n\n"))
    val (es, r) = newSource()

    es.open(request())

    r.awaitEvent("error:null")
    assertThat(r.nonBytes()).containsExactly("open", "message:m1", "error:null").inOrder()
  }

  @Test
  fun `empty event line reaches onMessage`() {
    server.enqueue(sse("event:\ndata: e1\n\n"))
    val (es, r) = newSource()

    es.open(request())

    r.awaitEvent("error:null")
    assertThat(r.nonBytes()).containsExactly("open", "message:e1", "error:null").inOrder()
  }

  @Test
  fun `comment only stream signals bytes but no event`() {
    server.enqueue(sse(":\n\n: keep-alive\n\n"))
    val (es, r) = newSource()

    es.open(request())

    r.awaitEvent("error:null")
    assertThat(r.events).contains("bytes")
    assertThat(r.nonBytes()).containsExactly("open", "error:null").inOrder()
  }

  @Test
  fun `event split across chunks arrives whole`() {
    server.enqueue(
      MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setChunkedBody("event: put\ndata: hello world\n\n", 3)
        .throttleBody(3, 10, TimeUnit.MILLISECONDS),
    )
    val (es, r) = newSource()

    es.open(request())

    r.awaitEvent("error:null")
    assertThat(r.events.count { it == "bytes" }).isAtLeast(2)
    assertThat(r.nonBytes()).containsExactly("open", "put:hello world", "error:null").inOrder()
  }

  @Test
  fun `error status is reported once and never opens`() {
    for (status in listOf(500, 401)) {
      server.enqueue(MockResponse().setResponseCode(status).setBody("data: not a stream\n\n"))
      val (es, r) = newSource()

      es.open(request())

      r.awaitEvent("error:$status")
      Thread.sleep(200)
      assertThat(r.events).containsExactly("error:$status")
    }
  }

  @Test
  fun `network failure after data delivers the event then a null status`() {
    server.enqueue(
      sse("data: a\n\n:" + "x".repeat(4_000) + "\n\n")
        .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
    )
    val (es, r) = newSource()

    es.open(request())

    r.awaitEvent("error:null")
    Thread.sleep(200)
    assertThat(r.nonBytes()).containsExactly("open", "message:a", "error:null").inOrder()
  }

  @Test
  fun `server closing the stream reports a null status`() {
    server.enqueue(sse("data: a\n\n"))
    val (es, r) = newSource()

    es.open(request())

    r.awaitEvent("error:null")
    Thread.sleep(200)
    assertThat(r.nonBytes()).containsExactly("open", "message:a", "error:null").inOrder()
  }

  @Test
  fun `close suppresses the cancellation error`() {
    // Positive control: an error before close() is reported.
    server.enqueue(MockResponse().setResponseCode(500))
    val (first, firstRecorder) = newSource()
    first.open(request())
    firstRecorder.awaitEvent("error:500")

    server.enqueue(longStream("data: a\n\n"))
    val (es, r) = newSource()
    es.open(request())
    r.awaitEvent("message:a")

    es.close()

    Thread.sleep(500)
    assertThat(r.nonBytes()).containsExactly("open", "message:a").inOrder()
  }

  @Test
  fun `default accept header is sent caller headers win and content type has no charset`() {
    server.enqueue(sse(""))
    server.enqueue(sse(""))

    newSource().first.open(request())
    val defaults = takeRequest()
    newSource().first.open(request(accept = "text/event-stream; custom=1"))
    val custom = takeRequest()

    assertThat(defaults.getHeader("Accept")).isEqualTo("text/event-stream")
    assertThat(defaults.getHeader("Content-Type")).isEqualTo("application/json")
    assertThat(defaults.getHeader("Authorization")).isEqualTo("api_key_value")
    assertThat(custom.headers.values("Accept")).containsExactly("text/event-stream; custom=1")
    assertThat(custom.getHeader("Content-Type")).isEqualTo("application/json")
  }

  @Test
  fun `stream client has no call timeout and 90 second connect read and write timeouts`() {
    assertThat(client.callTimeoutMillis).isEqualTo(0)
    assertThat(client.connectTimeoutMillis).isEqualTo(90_000)
    assertThat(client.readTimeoutMillis).isEqualTo(90_000)
    assertThat(client.writeTimeoutMillis).isEqualTo(90_000)
    assertThat(client.retryOnConnectionFailure).isTrue()
    assertThat(client.interceptors).isEmpty()
    assertThat(client.networkInterceptors).hasSize(1)
    assertThat(client.networkInterceptors[0]).isInstanceOf(FixJsonContentTypeInterceptor::class.java)
  }

  @Test
  fun `stream connection is closed when its call ends`() {
    server.enqueue(sse("data: a\n\n"))
    val (es, r) = newSource()

    es.open(request())

    r.awaitEvent("error:null")
    // A pooled connection would be reused by the next attempt, even after a silent drop.
    awaitUntil { client.connectionPool.connectionCount() == 0 }
  }

  @Test
  fun `callbacks run on the executor thread`() {
    server.enqueue(sse("event: put\ndata: p1\n\ndata: m1\n\n"))
    val (es, r) = newSource()

    es.open(request())

    r.awaitEvent("error:null")
    assertThat(r.nonBytes()).containsExactly("open", "put:p1", "message:m1", "error:null").inOrder()
    assertThat(r.threads.toSet()).containsExactly(EXECUTOR_THREAD)
  }

  @Test
  fun `no callbacks after close`() {
    server.enqueue(longStream((1..200).joinToString("") { "data: $it\n\n" }))
    val (es, r) = newSource()
    es.onMessage = {
      r.record("message:${it.data}")
      if (it.data == "1") es.close()
    }

    es.open(request())

    r.awaitEvent("message:1")
    Thread.sleep(1_000)
    assertThat(r.nonBytes()).containsExactly("open", "message:1").inOrder()
  }

  @Test
  fun `request method and body are sent unchanged`() {
    server.enqueue(sse(""))
    val body = """{"tag":"android","user":{"id":"user_1"}}"""

    newSource().first.open(request(body = body))

    val recorded = takeRequest()
    assertThat(recorded.method).isEqualTo("POST")
    assertThat(recorded.path).isEqualTo("/v1/gateway/stream_evaluations")
    assertThat(recorded.body.readUtf8()).isEqualTo(body)
  }

  @Test
  fun `named event with no data never reaches its listener`() {
    server.enqueue(sse("event: put\n\nevent: put\ndata: ok\n\n"))
    val (es, r) = newSource()

    es.open(request())

    r.awaitEvent("error:null")
    assertThat(r.nonBytes()).containsExactly("open", "put:ok", "error:null").inOrder()
  }

  @Test
  fun `open never reports an error before it returns`() {
    val fake = FakeStreamScheduler()
    val closedPort = ServerSocket(0).use { it.localPort }
    val (es, r) = newSource(executor = fake)

    es.open(request(url = "http://127.0.0.1:$closedPort/v1/gateway/stream_evaluations"))

    assertThat(r.events).isEmpty()
    // OkHttp's thread posts the connection failure to the executor.
    awaitUntil { fake.pendingCount > 0 }
    assertThat(r.events).isEmpty()

    fake.runCurrent()

    assertThat(r.events).containsExactly("error:null")
  }

  @Test
  fun `a throwing listener is logged and later events still arrive`() {
    server.enqueue(sse("event: put\ndata: first\n\nevent: put\ndata: second\n\n"))
    val (es, r) = newSource()
    es.onOpen = {
      r.record("open")
      throw IllegalStateException("open handler failed")
    }
    es.addEventListener("put") {
      r.record("put:${it.data}")
      if (it.data == "first") throw IllegalStateException("put handler failed")
    }

    es.open(request())

    r.awaitEvent("error:null")
    assertThat(r.nonBytes()).containsExactly("open", "put:first", "put:second", "error:null").inOrder()
    assertThat(logger.throwables(Log.ERROR).map { it.message })
      .containsExactly("open handler failed", "put handler failed")
  }

  @Test
  fun `close from another thread cancels the call at once while the executor is busy`() {
    server.enqueue(longStream("data: a\n\n"))
    val (es, r) = newSource()
    es.open(request())
    r.awaitEvent("message:a")
    assertThat(es.isCallCanceled).isFalse()

    val blocked = CountDownLatch(1)
    val release = CountDownLatch(1)
    executor.execute {
      blocked.countDown()
      release.await()
    }
    assertThat(blocked.await(2, TimeUnit.SECONDS)).isTrue()

    es.close()

    assertThat(es.isCallCanceled).isTrue()
    val seenBeforeRelease = r.events.toList()
    release.countDown()
    Thread.sleep(300)
    assertThat(r.events).isEqualTo(seenBeforeRelease)
    assertThat(r.nonBytes()).doesNotContain("error:null")
  }

  @Test
  fun `close before open sends no request and calls back nothing`() {
    // Positive control: an open source does reach the server.
    server.enqueue(sse("data: a\n\n"))
    val (first, firstRecorder) = newSource()
    first.open(request())
    firstRecorder.awaitEvent("error:null")
    assertThat(server.requestCount).isEqualTo(1)

    server.enqueue(sse("data: b\n\n"))
    val (es, r) = newSource()
    es.close()
    es.open(request())

    Thread.sleep(300)
    assertThat(server.requestCount).isEqualTo(1)
    assertThat(r.events).isEmpty()
  }

  @Test
  fun `queued chunks keep their own bytes while the executor is busy`() {
    val first = "event: put\ndata: first\n\n"
    server.enqueue(
      sse(first + "event: put\ndata: second\n\n")
        .throttleBody(first.length.toLong(), 100, TimeUnit.MILLISECONDS),
    )
    val release = CountDownLatch(1)
    executor.execute { release.await() }
    val (es, r) = newSource()

    es.open(request())
    // Every chunk is read and queued while the executor is blocked.
    Thread.sleep(1_000)
    assertThat(r.events).isEmpty()
    release.countDown()

    r.awaitEvent("error:null")
    assertThat(r.nonBytes()).containsExactly("open", "put:first", "put:second", "error:null").inOrder()
  }

  // Helpers

  private fun newSource(executor: Executor = this.executor): Pair<OkHttpEventSource, Recorder> {
    val r = Recorder()
    val es = OkHttpEventSource(client, executor)
    sources.add(es)
    es.onOpen = { r.record("open") }
    es.onBytesReceived = { r.record("bytes") }
    es.onMessage = { r.record("message:${it.data}") }
    es.onError = { r.record("error:$it") }
    es.addEventListener("put") { r.record("put:${it.data}") }
    return es to r
  }

  private fun request(
    url: String = server.url("/v1/gateway/stream_evaluations").toString(),
    body: String = "{}",
    accept: String? = null,
  ): Request =
    Request
      .Builder()
      .url(url)
      .post(body.toRequestBody("application/json".toMediaType()))
      .header("Authorization", "api_key_value")
      .apply { accept?.let { header("Accept", it) } }
      .build()

  private fun takeRequest(): RecordedRequest {
    val recorded = server.takeRequest(5, TimeUnit.SECONDS)
    assertWithMessage("the server received no request").that(recorded).isNotNull()
    return recorded!!
  }

  private fun sse(body: String): MockResponse =
    MockResponse()
      .setResponseCode(200)
      .setHeader("Content-Type", "text/event-stream")
      .setBody(body)

  /** A stream that stays open for about 30 s: [head] first, then slow heartbeats. */
  private fun longStream(head: String): MockResponse =
    sse(head + ":\n\n".repeat(1_000)).throttleBody(head.length.toLong(), 100, TimeUnit.MILLISECONDS)

  private class Recorder {
    val events = CopyOnWriteArrayList<String>()
    val threads = CopyOnWriteArrayList<String>()

    fun record(event: String) {
      threads.add(Thread.currentThread().name)
      events.add(event)
    }

    fun nonBytes(): List<String> = events.filter { it != "bytes" }

    fun awaitEvent(event: String) = awaitUntil { events.contains(event) }
  }

  private companion object {
    const val EXECUTOR_THREAD = "test-sdk-executor"

    fun awaitUntil(
      timeoutMillis: Long = 5_000,
      condition: () -> Boolean,
    ) {
      val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
      while (!condition()) {
        if (System.nanoTime() > deadline) fail("condition not met within $timeoutMillis ms")
        Thread.sleep(10)
      }
    }
  }

  private class RecordingLogger : BKTLogger {
    private val entries = mutableListOf<Triple<Int, String?, Throwable?>>()

    override fun log(
      priority: Int,
      messageCreator: (() -> String?)?,
      throwable: Throwable?,
    ) {
      synchronized(entries) {
        entries.add(Triple(priority, messageCreator?.invoke(), throwable))
      }
    }

    fun throwables(priority: Int): List<Throwable> =
      synchronized(entries) { entries.filter { it.first == priority }.mapNotNull { it.third } }
  }
}
