package io.bucketeer.sdk.android.e2e

import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * A small HTTP proxy server for the streaming E2E tests. The SDK is pointed at [url] through
 * the public `apiEndpoint` setting, and the proxy forwards every request to the real backend,
 * passing each reply back as it arrives (a stream piece by piece) and recording each request's
 * path and reply status. The client under test starts exactly as an app starts it; only its
 * address changes. See `bucketeer/src/androidTest/kotlin/io/bucketeer/sdk/android/e2e/README.md`.
 *
 * One request per connection: every reply carries `Connection: close` and ends by closing the
 * connection. Each connection runs on its own thread. Call [stop] when done.
 *
 * @param upstream The real backend endpoint (for example `BuildConfig.API_ENDPOINT`).
 * @param client Base for the forwarding client. The proxy's own tests pass one that counts
 *   cancelled calls.
 */
class RecordingProxy(
  upstream: String,
  client: OkHttpClient = OkHttpClient(),
) {
  data class Record(
    val method: String,
    val path: String,
    val status: Int? = null,
    val error: String? = null,
    /**
     * True once the exchange is over: the backend ended the reply (normally or with an error),
     * the client closed its connection, or the proxy stopped. A stream with status 200 and no
     * error can still be over.
     */
    val ended: Boolean = false,
  )

  private class Exchange(
    val socket: Socket,
    val call: Call,
    val recordIndex: Int,
  )

  private val upstream = upstream.toHttpUrl()
  private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

  private val client =
    client
      .newBuilder()
      // Longer than the SDK's own silence limit for the stream, so the SDK decides when a
      // quiet stream is dead, not the proxy.
      .readTimeout(120, TimeUnit.SECONDS)
      .callTimeout(0, TimeUnit.MILLISECONDS)
      // Every request reaching the proxy is forwarded once, so the records match what the
      // SDK sent.
      .retryOnConnectionFailure(false)
      .build()

  // Guards everything below.
  private val lock = Any()
  private val _records = mutableListOf<Record>()
  private val sockets = mutableSetOf<Socket>()
  private val exchanges = mutableMapOf<Int, Exchange>()
  private var isStopped = false

  /** The address to give the SDK: `http://127.0.0.1:<port>` plus the backend's path. */
  val url: String =
    this.upstream
      .newBuilder()
      .scheme("http")
      .host("127.0.0.1")
      .port(serverSocket.localPort)
      .build()
      .toString()
      .removeSuffix("/")

  /** Every request received so far, in order. */
  val records: List<Record>
    get() = synchronized(lock) { _records.toList() }

  init {
    thread("accept") { acceptLoop() }
  }

  /** How many requests had a path ending with [pathSuffix]. */
  fun count(pathSuffix: String): Int = records.count { it.path.endsWith(pathSuffix) }

  /** Cancels every backend request, closes every connection and stops listening. */
  fun stop() {
    val (calls, openSockets) =
      synchronized(lock) {
        if (isStopped) return
        isStopped = true
        exchanges.values.forEach { updateRecord(it.recordIndex) { record -> record.copy(ended = true) } }
        val calls = exchanges.values.map { it.call }
        exchanges.clear()
        calls to sockets.toList().also { sockets.clear() }
      }
    closeQuietly(serverSocket)
    calls.forEach { it.cancel() }
    openSockets.forEach { closeQuietly(it) }
  }

  private fun acceptLoop() {
    while (true) {
      val socket =
        try {
          serverSocket.accept()
        } catch (e: IOException) {
          return // stop() closed the server socket.
        }
      val accepted =
        synchronized(lock) {
          if (isStopped) false else sockets.add(socket)
        }
      if (!accepted) {
        closeQuietly(socket)
        continue
      }
      thread("connection") { handle(socket) }
    }
  }

  private fun handle(socket: Socket) {
    try {
      val input = BufferedInputStream(socket.getInputStream())
      val request = HttpRequest.read(input) ?: return
      forward(request, socket, input)
    } catch (e: IOException) {
      // The client went away before sending a whole request: nothing to record.
    } finally {
      synchronized(lock) { sockets.remove(socket) }
      closeQuietly(socket)
    }
  }

  private fun forward(
    request: HttpRequest,
    socket: Socket,
    input: InputStream,
  ) {
    val call = client.newCall(forwardingRequest(request))
    val recordIndex =
      synchronized(lock) {
        if (isStopped) return
        _records.add(Record(method = request.method, path = request.path))
        val index = _records.size - 1
        exchanges[index] = Exchange(socket, call, index)
        index
      }
    thread("client-close-watch") { watchForClientClose(input, recordIndex) }

    val response: Response =
      try {
        call.execute()
      } catch (e: IOException) {
        endExchange(recordIndex) { it.copy(error = e.toString(), ended = true) }
        return
      }

    response.use {
      updateRecord(recordIndex) { it.copy(status = response.code) }
      try {
        val output = socket.getOutputStream()
        output.write(responseHead(response).toByteArray())
        output.flush()
        val body = response.body!!.byteStream()
        val buffer = ByteArray(8_192)
        while (true) {
          val read = body.read(buffer)
          if (read == -1) break
          output.write(buffer, 0, read)
          output.flush()
        }
        endExchange(recordIndex) { it.copy(ended = true) }
      } catch (e: IOException) {
        // Recorded only while the exchange is live: after a client close or stop() the read
        // fails because the call was cancelled, which is not a backend error.
        endExchange(recordIndex) { it.copy(error = e.toString(), ended = true) }
      }
    }
  }

  /**
   * The client sends nothing after its request, so a read that ends means it closed the
   * connection (for example the SDK reconnecting its stream). Cancel the backend request.
   */
  private fun watchForClientClose(
    input: InputStream,
    recordIndex: Int,
  ) {
    try {
      while (input.read() != -1) {
        // Ignore anything the client sends after its request.
      }
    } catch (e: IOException) {
      // A closed or reset connection: the client is gone too.
    }
    val exchange = endExchange(recordIndex) { it.copy(ended = true) } ?: return
    exchange.call.cancel()
    closeQuietly(exchange.socket)
  }

  /** Ends the exchange once: returns it the first time, null after. */
  private fun endExchange(
    recordIndex: Int,
    change: (Record) -> Record,
  ): Exchange? =
    synchronized(lock) {
      val exchange = exchanges.remove(recordIndex) ?: return null
      updateRecord(recordIndex, change)
      exchange
    }

  private fun updateRecord(
    index: Int,
    change: (Record) -> Record,
  ) {
    synchronized(lock) { _records[index] = change(_records[index]) }
  }

  private fun forwardingRequest(request: HttpRequest): Request {
    val target = "${upstream.scheme}://${upstream.host}:${upstream.port}${request.target}".toHttpUrl()
    val builder = Request.Builder().url(target)
    request.headers
      .filter { (name, _) -> name.lowercase() !in DROPPED_REQUEST_HEADERS }
      .forEach { (name, value) -> builder.addHeader(name, value) }
    // No media type: the client's own Content-Type header is forwarded as sent.
    val body = if (request.body.isEmpty() && request.method in NO_BODY_METHODS) null else request.body.toRequestBody(null)
    return builder.method(request.method, body).build()
  }

  private fun responseHead(response: Response): String =
    buildString {
      append("HTTP/1.1 ${response.code} ${response.message}".trimEnd()).append("\r\n")
      response.headers
        .filter { (name, _) -> name.lowercase() !in DROPPED_RESPONSE_HEADERS }
        .forEach { (name, value) -> append("$name: $value\r\n") }
      append("Connection: close\r\n\r\n")
    }

  private fun thread(
    name: String,
    block: () -> Unit,
  ) {
    Thread(block, "RecordingProxy-$name").apply { isDaemon = true }.start()
  }

  private fun closeQuietly(closeable: java.io.Closeable) {
    try {
      closeable.close()
    } catch (e: IOException) {
      // Already closed.
    }
  }

  /** One HTTP/1.1 request: the head, then a body of `Content-Length` bytes. */
  private class HttpRequest(
    val method: String,
    /** Path and query, as sent. */
    val target: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
  ) {
    val path: String get() = target.substringBefore('?')

    companion object {
      /** Null when the connection ends before a whole request arrived. */
      fun read(input: InputStream): HttpRequest? {
        val head = readHead(input) ?: return null
        val lines = head.split("\r\n")
        val requestLine = lines.first().split(" ")
        if (requestLine.size < 2) return null

        val headers =
          lines.drop(1).mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon < 0) null else line.substring(0, colon) to line.substring(colon + 1).trim()
          }
        val contentLength =
          headers.firstOrNull { it.first.equals("Content-Length", ignoreCase = true) }?.second?.toIntOrNull() ?: 0
        val body = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
          val read = input.read(body, offset, contentLength - offset)
          if (read == -1) return null
          offset += read
        }
        return HttpRequest(requestLine[0], requestLine[1], headers, body)
      }

      private fun readHead(input: InputStream): String? {
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) {
          val byte = input.read()
          if (byte == -1) return null
          head.append(byte.toChar())
        }
        return head.removeSuffix("\r\n\r\n").toString()
      }
    }
  }

  private companion object {
    /**
     * Request headers that describe the client's hop to the proxy, set again by the forwarding
     * client for the hop to the backend. Accept-Encoding too: without it the forwarding client
     * asks for gzip and unzips the reply itself.
     */
    val DROPPED_REQUEST_HEADERS =
      setOf("host", "connection", "content-length", "transfer-encoding", "keep-alive", "accept-encoding")

    /** The forwarding client has already unzipped the body, and the proxy ends each reply by closing. */
    val DROPPED_RESPONSE_HEADERS =
      setOf("content-encoding", "content-length", "transfer-encoding", "connection", "keep-alive")

    val NO_BODY_METHODS = setOf("GET", "HEAD", "DELETE", "OPTIONS")
  }
}
