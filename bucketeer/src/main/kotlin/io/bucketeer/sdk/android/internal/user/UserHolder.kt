package io.bucketeer.sdk.android.internal.user

import io.bucketeer.sdk.android.BKTUser
import io.bucketeer.sdk.android.internal.model.User

internal class UserHolder(
  private var user: User,
) {
  val userId: String = user.id

  fun get(): User = user

  fun updateAttributes(updater: (previous: Map<String, String>) -> Map<String, String>) {
    this.user = user.copy(
      data = updater(user.data),
    )
  }
}

internal fun BKTUser.toUser(): User {
  return User(
    id = this.id,
    data = this.attributes,
  )
}

internal fun User.toBKTUser(): BKTUser {
  return BKTUser(
    id = this.id,
    attributes = this.data,
  )
}
