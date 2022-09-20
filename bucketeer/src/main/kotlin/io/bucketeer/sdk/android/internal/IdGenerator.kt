package io.bucketeer.sdk.android.internal

import java.util.UUID

internal interface IdGenerator {
  fun newId(): String
}

internal class IdGeneratorImpl : IdGenerator {
  override fun newId(): String = UUID.randomUUID().toString()
}
