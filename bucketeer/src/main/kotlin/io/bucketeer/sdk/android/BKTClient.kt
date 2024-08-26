package io.bucketeer.sdk.android

import android.content.Context
import io.bucketeer.sdk.android.internal.LoggerHolder
import io.bucketeer.sdk.android.internal.logw
import io.bucketeer.sdk.android.internal.util.Futures
import io.bucketeer.sdk.android.internal.util.requireNotNull
import org.json.JSONObject
import java.util.concurrent.Future

interface BKTClient {
  fun booleanVariation(
    featureId: String,
    defaultValue: Boolean,
  ): Boolean

  fun boolVariationDetails(
    featureId: String,
    defaultValue: Boolean,
  ): BKTEvaluationDetails<Boolean>

  fun intVariation(
    featureId: String,
    defaultValue: Int,
  ): Int

  fun intVariationDetails(
    featureId: String,
    defaultValue: Int,
  ): BKTEvaluationDetails<Int>

  fun doubleVariation(
    featureId: String,
    defaultValue: Double,
  ): Double

  fun doubleVariationDetails(
    featureId: String,
    defaultValue: Double,
  ): BKTEvaluationDetails<Double>

  fun stringVariation(
    featureId: String,
    defaultValue: String,
  ): String

  fun stringVariationDetails(
    featureId: String,
    defaultValue: String,
  ): BKTEvaluationDetails<String>

  fun objectVariation(
    featureId: String,
    defaultValue: BKTValue,
  ): BKTValue

  fun objectVariationDetails(
    featureId: String,
    defaultValue: BKTValue,
  ): BKTEvaluationDetails<BKTValue>

  @Deprecated(
    message =
      "jsonVariation() is deprecated. Use objectVariation() instead.",
  )
  fun jsonVariation(
    featureId: String,
    defaultValue: JSONObject,
  ): JSONObject

  fun track(
    goalId: String,
    value: Double = 0.0,
  )

  fun currentUser(): BKTUser

  fun updateUserAttributes(attributes: Map<String, String>)

  fun fetchEvaluations(timeoutMillis: Long? = null): Future<BKTException?>

  fun flush(): Future<BKTException?>

  @Deprecated(
    message =
      "evaluationDetails() is deprecated. Use stringEvaluationDetails() instead.",
  )
  fun evaluationDetails(featureId: String): BKTEvaluation?

  fun addEvaluationUpdateListener(listener: EvaluationUpdateListener): String

  fun removeEvaluationUpdateListener(key: String)

  fun clearEvaluationUpdateListeners()

  companion object {
    @Volatile
    private var instance: BKTClient? = null

    fun getInstance(): BKTClient {
      synchronized(this) {
        return requireNotNull(instance) { "BKTClient is not initialized" }
      }
    }

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

    fun destroy() {
      synchronized(this) {
        val client = instance ?: return
        (client as BKTClientImpl).let { clientImpl ->
          clientImpl.resetTasks()
          clientImpl.destroy()
        }
        instance = null
      }
    }
  }

  fun interface EvaluationUpdateListener {
    // The listener callback is called on the main thread.
    fun onUpdate()
  }
}
