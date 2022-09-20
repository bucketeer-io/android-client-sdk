@file:Suppress("ktlint:filename")

package io.bucketeer.sdk.android.internal.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlin.reflect.KClass

internal fun <T : Any> createBroadcastPendingIntent(
  context: Context,
  targetClass: KClass<T>,
): PendingIntent {
  val intent = Intent(context, targetClass.java)

  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    PendingIntent.getBroadcast(
      context,
      0,
      intent,
      PendingIntent.FLAG_IMMUTABLE,
    )
  } else {
    PendingIntent.getBroadcast(
      context,
      0,
      intent,
      0,
    )
  }
}
