package io.bucketeer.sdk.android.internal.di

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.mocks.user1
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class DataModuleStreamClientTest {
  private lateinit var server: MockWebServer

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `destroy cancels a running stream call`() {
    // A body that takes about 30 s to arrive, so the call is still reading when destroy() runs.
    server.enqueue(
      MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(":\n\n".repeat(1_000))
        .throttleBody(3, 100, TimeUnit.MILLISECONDS),
    )
    val module = newDataModule()
    val client = module.streamHttpClient
    val failed = CountDownLatch(1)
    client.newCall(Request.Builder().url(server.url("/stream")).build()).enqueue(
      object : Callback {
        override fun onFailure(
          call: Call,
          e: IOException,
        ) {
          failed.countDown()
        }

        override fun onResponse(
          call: Call,
          response: Response,
        ) {
          try {
            response.use { it.body!!.string() }
          } catch (e: IOException) {
            failed.countDown()
          }
        }
      },
    )
    awaitUntil { client.dispatcher.runningCallsCount() == 1 }
    // Positive control: the call is still running after a moment on its own.
    Thread.sleep(200)
    assertThat(failed.count).isEqualTo(1)

    module.destroy()

    assertThat(failed.await(2, TimeUnit.SECONDS)).isTrue()
    awaitUntil { client.dispatcher.runningCallsCount() == 0 }
  }

  @Test
  fun `destroy does not create the stream client when it was never used`() {
    val unused = newDataModule()
    unused.destroy()
    assertThat(unused.isStreamHttpClientCreated).isFalse()

    // Positive control: a module that used the client reports it.
    val used = newDataModule()
    used.streamHttpClient
    used.destroy()
    assertThat(used.isStreamHttpClientCreated).isTrue()
  }

  private fun newDataModule(): DataModule =
    DataModule(
      application = ApplicationProvider.getApplicationContext(),
      user = user1,
      config =
        BKTConfig
          .builder()
          .apiEndpoint("https://example.com")
          .apiKey("api_key_value")
          .featureTag("feature_tag_value")
          .appVersion("1.2.3")
          .build(),
      inMemoryDB = true,
    )

  private fun awaitUntil(
    timeoutMillis: Long = 5_000,
    condition: () -> Boolean,
  ) {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    while (!condition()) {
      check(System.nanoTime() < deadline) { "condition not met within $timeoutMillis ms" }
      Thread.sleep(10)
    }
  }
}
