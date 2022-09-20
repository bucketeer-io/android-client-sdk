package io.bucketeer.sdk.android

import android.util.Log

interface BKTLogger {
  fun log(
    priority: Int,
    messageCreator: (() -> String?)?,
    throwable: Throwable?,
  )
}

internal class DefaultLogger(
  private val tag: String = "Bucketeer",
) : BKTLogger {
  override fun log(
    priority: Int,
    messageCreator: (() -> String?)?,
    throwable: Throwable?,
  ) {
    if (!Log.isLoggable(tag, priority)) return

    val message = buildString {
      messageCreator?.invoke()?.let { append(it) }
      if (throwable != null) append("\n")
      if (throwable != null) append(Log.getStackTraceString(throwable))
    }
    if (message.isBlank()) return

    Log.println(priority, tag, message)
  }

  override fun equals(other: Any?): Boolean {
    return other is DefaultLogger && this.tag == other.tag
  }

  override fun hashCode(): Int {
    return tag.hashCode()
  }
}
