package io.bucketeer.sdk.android

import android.app.Application
import android.content.Context
import androidx.annotation.MainThread
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
  internal val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
  internal val component: Component = ComponentImpl(
    dataModule = DataModule(
      application = context.applicationContext as Application,
      user = user.toUser(),
      config = config,
    ),
    interactorModule = InteractorModule(),
  ),
) : BKTClient {

  private var taskScheduler: TaskScheduler? = null

  override fun stringVariation(featureId: String, defaultValue: String): String {
    return getVariationValue(featureId, defaultValue)
  }

  override fun intVariation(featureId: String, defaultValue: Int): Int {
    return getVariationValue(featureId, defaultValue)
  }

  override fun doubleVariation(featureId: String, defaultValue: Double): Double {
    return getVariationValue(featureId, defaultValue)
  }

  override fun booleanVariation(featureId: String, defaultValue: Boolean): Boolean {
    return getVariationValue(featureId, defaultValue)
  }

  override fun jsonVariation(featureId: String, defaultValue: JSONObject): JSONObject {
    return getVariationValue(featureId, defaultValue)
  }

  override fun track(goalId: String, value: Double) {
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

  override fun currentUser(): BKTUser {
    return component.userHolder.get().toBKTUser()
  }

  override fun setUserAttributes(attributes: Map<String, String>) {
    component.userHolder.updateAttributes { attributes }
  }

  override fun fetchEvaluations(timeoutMillis: Long?): Future<BKTException?> {
    return executor.submit<BKTException?> {
      fetchEvaluationsSync(component, executor, timeoutMillis)
    }
  }

  override fun flush(): Future<BKTException?> {
    return executor.submit<BKTException?> {
      flushSync(component)
    }
  }

  override fun evaluationDetails(featureId: String): BKTEvaluation? {
    val raw = component.evaluationInteractor
      .getLatest(component.userHolder.userId, featureId) ?: return null

    return BKTEvaluation(
      id = raw.id,
      featureId = raw.feature_id,
      featureVersion = raw.feature_version,
      userId = raw.user_id,
      variationId = raw.variation_id,
      variationValue = raw.variation_value,
      reason = BKTEvaluation.Reason.from(raw.reason.type.value),
    )
  }

  private fun refreshCache() {
    component.evaluationInteractor.refreshCache(component.userHolder.userId)
  }

  @MainThread
  internal fun initializeInternal(timeoutMillis: Long): Future<BKTException?> {
    scheduleTasks()
    return executor.submit<BKTException?> {
      refreshCache()
      fetchEvaluationsSync(component, executor, timeoutMillis)
    }
  }

  private inline fun <reified T : Any> getVariationValue(featureId: String, defaultValue: T): T {
    logd { "BKTClient.getVariation(featureId = $featureId, defaultValue = $defaultValue) called" }

    val raw = component.evaluationInteractor.getLatest(component.userHolder.userId, featureId)

    val user = component.userHolder.get()
    val featureTag = config.featureTag
    if (raw != null) {
      executor.execute {
        component.eventInteractor.trackEvaluationEvent(
          featureTag = featureTag,
          user = user,
          evaluation = raw,
        )
      }
    } else {
      executor.execute {
        component.eventInteractor.trackDefaultEvaluationEvent(
          featureTag = featureTag,
          user = user,
          featureId = featureId,
        )
      }
    }

    return raw.getVariationValue(defaultValue)
  }

  @MainThread
  private fun scheduleTasks() {
    taskScheduler = TaskScheduler(component, executor)
    ProcessLifecycleOwner.get().lifecycle.addObserver(taskScheduler!!)
  }

  @MainThread
  internal fun resetTasks() {
    taskScheduler?.let {
      it.stop()
      ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
    }
    taskScheduler = null
  }

  companion object {
    internal fun fetchEvaluationsSync(
      component: Component,
      executor: Executor,
      timeoutMillis: Long?,
    ): BKTException? {
      val result = component.evaluationInteractor
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
