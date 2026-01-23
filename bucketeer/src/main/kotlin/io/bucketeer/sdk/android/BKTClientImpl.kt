package io.bucketeer.sdk.android

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ProcessLifecycleOwner
import io.bucketeer.sdk.android.internal.di.Component
import io.bucketeer.sdk.android.internal.di.ComponentImpl
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.di.InteractorModule
import io.bucketeer.sdk.android.internal.evaluation.getVariationValue
import io.bucketeer.sdk.android.internal.event.SendEventsResult
import io.bucketeer.sdk.android.internal.logd
import io.bucketeer.sdk.android.internal.remote.GetEvaluationsResult
import io.bucketeer.sdk.android.internal.scheduler.TaskScheduler
import io.bucketeer.sdk.android.internal.user.toBKTUser
import io.bucketeer.sdk.android.internal.user.toUser
import org.json.JSONObject
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService

internal class BKTClientImpl(
  private val context: Context,
  private val config: BKTConfig,
  user: BKTUser,
  private val mainHandler: Handler = Handler(Looper.getMainLooper()),
  internal val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
  internal val component: Component =
    ComponentImpl(
      dataModule =
        DataModule(
          application = context.applicationContext as Application,
          user = user.toUser(),
          config = config,
          executor = executor,
        ),
      interactorModule =
        InteractorModule(
          mainHandler = mainHandler,
        ),
    ),
) : BKTClient {
  private var taskScheduler: TaskScheduler? = null

  override fun booleanVariation(
    featureId: String,
    defaultValue: Boolean,
  ): Boolean = boolVariationDetails(featureId, defaultValue).variationValue

  override fun boolVariationDetails(
    featureId: String,
    defaultValue: Boolean,
  ): BKTEvaluationDetails<Boolean> = getBKTEvaluationDetails(featureId, defaultValue)

  override fun intVariation(
    featureId: String,
    defaultValue: Int,
  ): Int = intVariationDetails(featureId, defaultValue).variationValue

  override fun intVariationDetails(
    featureId: String,
    defaultValue: Int,
  ): BKTEvaluationDetails<Int> = getBKTEvaluationDetails(featureId, defaultValue)

  override fun doubleVariation(
    featureId: String,
    defaultValue: Double,
  ): Double = doubleVariationDetails(featureId, defaultValue).variationValue

  override fun doubleVariationDetails(
    featureId: String,
    defaultValue: Double,
  ): BKTEvaluationDetails<Double> = getBKTEvaluationDetails(featureId, defaultValue)

  override fun stringVariation(
    featureId: String,
    defaultValue: String,
  ): String = stringVariationDetails(featureId, defaultValue).variationValue

  override fun stringVariationDetails(
    featureId: String,
    defaultValue: String,
  ): BKTEvaluationDetails<String> = getBKTEvaluationDetails(featureId, defaultValue)

  override fun objectVariation(
    featureId: String,
    defaultValue: BKTValue,
  ): BKTValue = objectVariationDetails(featureId, defaultValue).variationValue

  override fun objectVariationDetails(
    featureId: String,
    defaultValue: BKTValue,
  ): BKTEvaluationDetails<BKTValue> = getBKTEvaluationDetails(featureId, defaultValue)

  @Deprecated(message = "evaluationDetails() is deprecated. Use stringEvaluationDetails() instead.")
  override fun jsonVariation(
    featureId: String,
    defaultValue: JSONObject,
  ): JSONObject = getBKTEvaluationDetails(featureId, defaultValue).variationValue

  override fun track(
    goalId: String,
    value: Double,
  ) {
    val user = component.userHolder.get()
    val featureTag = config.featureTag
    executor.execute {
      component.eventInteractor.trackGoalEvent(
        featureTag = featureTag,
        user = user,
        goalId = goalId,
        value = value,
      )
    }
  }

  override fun currentUser(): BKTUser = component.userHolder.get().toBKTUser()

  override fun updateUserAttributes(attributes: Map<String, String>) {
    component.userHolder.updateAttributes { attributes }
    // https://github.com/bucketeer-io/android-client-sdk/issues/69
    // userAttributesUpdated: when the user attributes change via the customAttributes interface,
    // the userAttributesUpdated field must be set to true in the next request.
    component.evaluationInteractor.setUserAttributesUpdated()
  }

  override fun fetchEvaluations(timeoutMillis: Long?): Future<BKTException?> =
    executor.submit<BKTException?> {
      fetchEvaluationsSync(component, executor, timeoutMillis)
    }

  override fun flush(): Future<BKTException?> =
    executor.submit<BKTException?> {
      flushSync(component)
    }

  @Deprecated(message = "evaluationDetails() is deprecated. Use stringEvaluationDetails() instead.")
  @Suppress("DEPRECATION")
  override fun evaluationDetails(featureId: String): BKTEvaluation? {
    val raw =
      component.evaluationInteractor
        .getLatest(featureId) ?: return null

    return BKTEvaluation(
      id = raw.id,
      featureId = raw.featureId,
      featureVersion = raw.featureVersion,
      userId = raw.userId,
      variationId = raw.variationId,
      variationName = raw.variationName,
      variationValue = raw.variationValue,
      reason = BKTEvaluation.Reason.from(raw.reason.type.name),
    )
  }

  override fun addEvaluationUpdateListener(listener: BKTClient.EvaluationUpdateListener): String =
    component.evaluationInteractor.addUpdateListener(listener)

  override fun removeEvaluationUpdateListener(key: String) {
    component.evaluationInteractor.removeUpdateListener(key)
  }

  override fun clearEvaluationUpdateListeners() {
    component.evaluationInteractor.clearUpdateListeners()
  }

  private fun refreshCache() {
    component.evaluationInteractor.refreshCache()
  }

  internal fun initializeInternal(timeoutMillis: Long): Future<BKTException?> {
    scheduleTasks()
    listenEvaluationInteractorError()
    return executor.submit<BKTException?> {
      refreshCache()
      fetchEvaluationsSync(component, executor, timeoutMillis)
    }
  }

  private inline fun <reified T : Any> getBKTEvaluationDetails(
    featureId: String,
    defaultValue: T,
  ): BKTEvaluationDetails<T> {
    logd { "BKTClient.getVariation(featureId = $featureId) called" }

    val raw = component.evaluationInteractor.getLatest(featureId)
    val user = component.userHolder.get()
    val featureTag = config.featureTag

    if (raw == null) {
      // Flag not found in cache
      executor.execute {
        component.eventInteractor.trackDefaultEvaluationEvent(
          featureTag = featureTag,
          user = user,
          featureId = featureId,
          reason = io.bucketeer.sdk.android.internal.model.ReasonType.ERROR_FLAG_NOT_FOUND,
        )
      }
      return BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = user.id,
        defaultValue = defaultValue,
        reason = BKTEvaluationDetails.Reason.ERROR_FLAG_NOT_FOUND,
      )
    }

    val value: T? = raw.getVariationValue()

    if (value != null) {
      // Success case
      executor.execute {
        component.eventInteractor.trackEvaluationEvent(
          featureTag = featureTag,
          user = user,
          evaluation = raw,
        )
      }
      return BKTEvaluationDetails(
        featureId = raw.featureId,
        featureVersion = raw.featureVersion,
        userId = raw.userId,
        variationId = raw.variationId,
        variationName = raw.variationName,
        variationValue = value,
        reason = BKTEvaluationDetails.Reason.from(raw.reason.type.name),
      )
    } else {
      // Type mismatch
      executor.execute {
        component.eventInteractor.trackDefaultEvaluationEvent(
          featureTag = featureTag,
          user = user,
          featureId = featureId,
          reason = io.bucketeer.sdk.android.internal.model.ReasonType.ERROR_WRONG_TYPE,
        )
      }
      return BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = user.id,
        defaultValue = defaultValue,
        reason = BKTEvaluationDetails.Reason.ERROR_WRONG_TYPE,
      )
    }
  }

  private fun listenEvaluationInteractorError() {
    component.evaluationInteractor.setErrorListener { error ->
      executor.execute {
        val featureTag = config.featureTag
        val interactor = component.eventInteractor
        interactor.trackFetchEvaluationsFailure(
          featureTag = featureTag,
          error = error,
        )
      }
    }
  }

  private fun scheduleTasks() {
    // Lifecycle observer must be executed on the main thread
    runOnMainThread {
      taskScheduler = TaskScheduler(component, executor)
      ProcessLifecycleOwner.get().lifecycle.addObserver(taskScheduler!!)
    }
  }

  internal fun resetTasks() {
    runOnMainThread {
      taskScheduler?.let {
        it.stop()
        // Lifecycle observer must be executed on the main thread
        ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
      }
      taskScheduler = null
    }
  }

  internal fun destroy() {
    executor.execute {
      component.destroy()
    }
  }

  private fun runOnMainThread(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      // Currently on the main thread, execute immediately
      block()
    } else {
      // Not on the main thread, post to the main thread
      mainHandler.post(block)
    }
  }

  companion object {
    internal fun fetchEvaluationsSync(
      component: Component,
      executor: Executor,
      timeoutMillis: Long?,
    ): BKTException? {
      val result =
        component.evaluationInteractor
          .fetch(user = component.userHolder.get(), timeoutMillis)

      executor.execute {
        val interactor = component.eventInteractor
        when (result) {
          is GetEvaluationsResult.Success -> {
            interactor.trackFetchEvaluationsSuccess(
              featureTag = result.featureTag,
              seconds = result.seconds,
              sizeByte = result.sizeByte,
            )
          }

          is GetEvaluationsResult.Failure -> {
            interactor.trackFetchEvaluationsFailure(
              featureTag = result.featureTag,
              error = result.error,
            )
          }
        }
      }

      return when (result) {
        is GetEvaluationsResult.Success -> null
        is GetEvaluationsResult.Failure -> result.error
      }
    }

    @Suppress("MoveVariableDeclarationIntoWhen")
    internal fun flushSync(component: Component): BKTException? {
      val result = component.eventInteractor.sendEvents(force = true)

      return when (result) {
        is SendEventsResult.Success -> null
        is SendEventsResult.Failure -> result.error
      }
    }
  }
}
