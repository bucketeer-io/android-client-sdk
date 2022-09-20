package io.bucketeer.sdk.android.internal.scheduler

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.BKTClientImpl
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.createTestBKTConfig
import io.bucketeer.sdk.android.internal.user.toBKTUser
import io.bucketeer.sdk.android.mocks.user1
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class EventBackgroundTaskTest {
  private lateinit var server: MockWebServer

  private lateinit var config: BKTConfig

  private lateinit var context: Context

  @Before
  fun setup() {
    server = MockWebServer()

    config = createTestBKTConfig(
      apiKey = "api_key_value",
      endpoint = server.url("").toString(),
      featureTag = "feature_tag_value",
    )

    context = ApplicationProvider.getApplicationContext()
  }

  @After
  fun tearDown() {
    server.shutdown()
    (BKTClient.getInstance() as BKTClientImpl).executor.shutdownNow()
    BKTClient.destroy()
  }

  @Test
  fun start() {
    BKTClient.initialize(context, config, user1.toBKTUser())

    val scheduler = EventBackgroundTask.Scheduler(context, TimeUnit.MINUTES.toMillis(2))
    scheduler.start()

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val shadow = Shadows.shadowOf(alarmManager)

    Truth.assertThat(shadow.scheduledAlarms).hasSize(1)
    val alarm = shadow.scheduledAlarms.first()

    Truth.assertThat(alarm.type).isEqualTo(AlarmManager.ELAPSED_REALTIME)
    Truth.assertThat(alarm.interval).isEqualTo(TimeUnit.MINUTES.toMillis(2))
  }

  @Test
  fun `start - should cancel previous task`() {
    BKTClient.initialize(context, config, user1.toBKTUser())

    val scheduler1 = EventBackgroundTask.Scheduler(context, TimeUnit.MINUTES.toMillis(2))
    val scheduler2 = EventBackgroundTask.Scheduler(context, TimeUnit.MINUTES.toMillis(3))

    scheduler1.start()
    scheduler2.start()

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val shadow = Shadows.shadowOf(alarmManager)

    Truth.assertThat(shadow.scheduledAlarms).hasSize(1)
    val alarm = shadow.scheduledAlarms.first()

    Truth.assertThat(alarm.type).isEqualTo(AlarmManager.ELAPSED_REALTIME)
    Truth.assertThat(alarm.interval).isEqualTo(TimeUnit.MINUTES.toMillis(3))
  }

  @Test
  fun stop() {
    BKTClient.initialize(context, config, user1.toBKTUser())

    val scheduler = EventBackgroundTask.Scheduler(context, TimeUnit.MINUTES.toMillis(2))
    scheduler.start()

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val shadow = Shadows.shadowOf(alarmManager)

    Truth.assertThat(shadow.scheduledAlarms).hasSize(1)

    scheduler.stop()

    Truth.assertThat(shadow.scheduledAlarms).isEmpty()
  }
}
