@file:Suppress("ktlint:filename")

package io.bucketeer.sdk.android.mocks

import io.bucketeer.sdk.android.internal.model.User

val user1: User by lazy {
  User(
    id = "user id 1",
    data = mapOf("age" to "28"),
  )
}

val user2: User by lazy {
  User(id = "user id 2")
}
