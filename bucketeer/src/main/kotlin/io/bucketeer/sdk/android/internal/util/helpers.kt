@file:Suppress("ktlint:filename")

package io.bucketeer.sdk.android.internal.util

import io.bucketeer.sdk.android.BKTException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
internal inline fun require(value: Boolean, lazyMessage: () -> Any) {
  contract {
    returns() implies value
  }
  if (!value) {
    val message = lazyMessage()
    throw BKTException.IllegalArgumentException(message.toString())
  }
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T : Any> requireNotNull(value: T?, lazyMessage: () -> Any): T {
  contract {
    returns() implies (value != null)
  }

  if (value == null) {
    val message = lazyMessage()
    throw BKTException.IllegalArgumentException(message.toString())
  } else {
    return value
  }
}
