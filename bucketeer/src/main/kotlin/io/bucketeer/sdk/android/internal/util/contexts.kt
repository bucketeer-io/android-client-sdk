@file:Suppress("ktlint:standard:filename")

package io.bucketeer.sdk.android.internal.util

import android.app.AlarmManager
import android.content.Context

fun Context.getAlarmManager(): AlarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
