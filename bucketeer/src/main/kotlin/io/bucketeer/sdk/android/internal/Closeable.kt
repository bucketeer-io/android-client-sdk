package io.bucketeer.sdk.android.internal

interface Closeable {
  fun close()
  val isClosed: Boolean
}
