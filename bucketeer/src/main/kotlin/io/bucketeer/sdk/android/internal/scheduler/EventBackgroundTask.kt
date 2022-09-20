package io.bucketeer.sdk.android.internal.scheduler

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.BKTClientImpl
import io.bucketeer.sdk.android.internal.logd
import io.bucketeer.sdk.android.internal.loge
import io.bucketeer.sdk.android.internal.logi
import io.bucketeer.sdk.android.internal.util.createBroadcastPendingIntent
import io.bucketeer.sdk.android.internal.util.getAlarmManager

class EventBackgroundTask : BroadcastReceiver() {
  override fun onReceive(context: Context?, intent: Intent?) {
    // We need to use BKTClient singleton because we can't serialize logger
    // This means
    // - BKTClient#initialize must be called first
    // - background polling happens as long as BKTClient singleton lives
    val client = try {
      BKTClient.getInstance() as BKTClientImpl
    } catch (e: Throwable) {
      null
    }

    if (client == null) {
      logd { "BKTClient is not initialized, skipping background Event sync..." }
      return
    }

    val pendingResult = goAsync()

    client.executor.execute {
      val result = BKTClientImpl.flushSync(client.component)

      if (result == null) {
        logd { "finished background event sync" }
      } else {
        loge(result) { "background Event sync finished with error" }
      }

      pendingResult.finish()
    }
  }

  class Scheduler(
    private val context: Context,
    private val interval: Long,
  ) : ScheduledTask {
    override fun start() {
      stop()
      logi { "start background Event polling...: $interval" }

      val intent = createBroadcastPendingIntent(context, EventBackgroundTask::class)
      val alarmManager = context.getAlarmManager()

      try {
        alarmManager.setInexactRepeating(
          AlarmManager.ELAPSED_REALTIME,
          SystemClock.elapsedRealtime() + interval,
          interval,
          intent,
        )
      } catch (e: Throwable) {
        loge(e) { "Error while starting background Event polling" }
      }
    }

    override fun stop() {
      logi { "stop background Event sync..." }
      val intent = createBroadcastPendingIntent(context, EventBackgroundTask::class)
      context.getAlarmManager().cancel(intent)
    }
  }
}
