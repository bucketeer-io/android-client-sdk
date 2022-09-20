@file:Suppress("ktlint:filename")

package io.bucketeer.sdk.android.internal.util

import android.app.AlarmManager
import android.content.Context

fun Context.getAlarmManager(): AlarmManager {
  return getSystemService(Context.ALARM_SERVICE) as AlarmManager
}
