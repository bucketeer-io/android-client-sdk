@file:Suppress("ktlint:filename")

package io.bucketeer.sdk.android.internal

import android.util.Log
import io.bucketeer.sdk.android.BKTLogger

internal object LoggerHolder {
  private val logHandlers: MutableList<BKTLogger> = mutableListOf()

  fun addLogger(logger: BKTLogger) {
    logHandlers.add(logger)
  }

  fun log(
    priority: Int,
    messageCreator: (() -> String?)? = null,
    throwable: Throwable? = null,
  ) {
    logHandlers.forEach {
      it.log(priority, messageCreator, throwable)
    }
  }
}

// TODO: Add msgCreator log methods
internal fun logd(
  throwable: Throwable? = null,
  messageCreator: (() -> String?)? = null,
) {
  LoggerHolder.log(
    Log.DEBUG,
    messageCreator = messageCreator,
    throwable = throwable,
  )
}

internal fun loge(
  throwable: Throwable? = null,
  messageCreator: (() -> String?)? = null,
) {
  LoggerHolder.log(
    priority = Log.ERROR,
    messageCreator = messageCreator,
    throwable = throwable,
  )
}

internal fun logi(
  throwable: Throwable? = null,
  messageCreator: (() -> String?)? = null,
) {
  LoggerHolder.log(
    priority = Log.INFO,
    messageCreator = messageCreator,
    throwable = throwable,
  )
}

internal fun logv(
  throwable: Throwable? = null,
  messageCreator: (() -> String?)? = null,
) {
  LoggerHolder.log(
    priority = Log.VERBOSE,
    messageCreator = messageCreator,
    throwable = throwable,
  )
}

internal fun logw(
  throwable: Throwable? = null,
  messageCreator: (() -> String?)? = null,
) {
  LoggerHolder.log(
    priority = Log.WARN,
    messageCreator = messageCreator,
    throwable = throwable,
  )
}

internal fun logwtf(
  throwable: Throwable? = null,
  messageCreator: (() -> String?)? = null,
) {
  LoggerHolder.log(
    priority = Log.ASSERT,
    messageCreator = messageCreator,
    throwable = throwable,
  )
}
