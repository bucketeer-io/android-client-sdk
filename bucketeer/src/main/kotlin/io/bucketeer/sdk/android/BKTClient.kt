package io.bucketeer.sdk.android

import android.content.Context
import androidx.annotation.MainThread
import io.bucketeer.sdk.android.internal.LoggerHolder
import io.bucketeer.sdk.android.internal.logw
import io.bucketeer.sdk.android.internal.util.Futures
import io.bucketeer.sdk.android.internal.util.requireNotNull
import org.json.JSONObject
import java.util.concurrent.Future

interface BKTClient {

  fun stringVariation(featureId: String, defaultValue: String): String

  fun intVariation(featureId: String, defaultValue: Int): Int

  fun doubleVariation(featureId: String, defaultValue: Double): Double

  fun booleanVariation(featureId: String, defaultValue: Boolean): Boolean

  fun jsonVariation(featureId: String, defaultValue: JSONObject): JSONObject

  fun track(goalId: String, value: Double)

  fun currentUser(): BKTUser

  fun setUserAttributes(attributes: Map<String, String>)

  fun fetchEvaluations(timeoutMillis: Long? = null): Future<BKTException?>

  fun flush(): Future<BKTException?>

  fun evaluationDetails(featureId: String): BKTEvaluation?

  companion object {

    @Volatile
    private var instance: BKTClient? = null

    fun getInstance(): BKTClient {
      synchronized(this) {
        return requireNotNull(instance) { "BKTClient is not initialized" }
      }
    }

    @MainThread
    fun initialize(
      context: Context,
      config: BKTConfig,
      user: BKTUser,
      timeoutMillis: Long = 5000,
    ): Future<BKTException?> {
      synchronized(this) {
        if (instance != null) {
          logw { "BKTClient is already initialized. not sure if initial fetch has been finished" }
          return Futures.success(null)
        }

        if (config.logger != null) {
          LoggerHolder.addLogger(config.logger)
        }

        val client = BKTClientImpl(context, config, user)

        instance = client

        return client.initializeInternal(timeoutMillis)
      }
    }

    @MainThread
    fun destroy() {
      synchronized(this) {
        val client = instance ?: return
        (client as BKTClientImpl).resetTasks()
        instance = null
      }
    }
  }
}
