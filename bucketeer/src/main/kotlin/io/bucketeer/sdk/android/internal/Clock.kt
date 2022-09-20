package io.bucketeer.sdk.android.internal

internal interface Clock {
  fun currentTimeMillis(): Long
  fun currentTimeSeconds(): Long
}

internal class ClockImpl : Clock {
  override fun currentTimeMillis(): Long = System.currentTimeMillis()
  override fun currentTimeSeconds(): Long = System.currentTimeMillis() / 1000
}
