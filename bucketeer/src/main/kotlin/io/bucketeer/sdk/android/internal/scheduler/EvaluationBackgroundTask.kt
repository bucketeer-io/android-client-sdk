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

internal class EvaluationBackgroundTask : BroadcastReceiver() {
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
      logd { "BKTClient is not initialized, skipping background Evaluation polling..." }
      return
    }
    val pendingResult = goAsync()

    client.executor.execute {
      val result = BKTClientImpl.fetchEvaluationsSync(client.component, client.executor, null)

      if (result == null) {
        logd { "finished background Evaluation polling" }
      } else {
        loge(result) { "background Evaluation polling finished with error" }
      }

      // send events if needed
      client.component.eventInteractor.sendEvents(force = false)

      pendingResult.finish()
    }
  }

  class Scheduler(
    private val context: Context,
    private val interval: Long,
  ) : ScheduledTask {
    override fun start() {
      stop()
      logi { "start background Evaluation polling...: $interval" }

      val intent = createBroadcastPendingIntent(context, EvaluationBackgroundTask::class)
      val alarmManager = context.getAlarmManager()

      try {
        alarmManager.setInexactRepeating(
          AlarmManager.ELAPSED_REALTIME,
          SystemClock.elapsedRealtime() + interval,
          interval,
          intent,
        )
      } catch (e: Throwable) {
        loge(e) { "Error while starting Evaluation background polling" }
      }
    }

    override fun stop() {
      logi { "stop Evaluation background polling..." }
      val alarmManager = context.getAlarmManager()
      val intent = createBroadcastPendingIntent(context, EvaluationBackgroundTask::class)
      alarmManager.cancel(intent)
    }
  }
}
