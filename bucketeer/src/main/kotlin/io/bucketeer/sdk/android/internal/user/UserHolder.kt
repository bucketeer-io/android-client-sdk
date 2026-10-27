package io.bucketeer.sdk.android.internal.user

import io.bucketeer.sdk.android.BKTUser
import io.bucketeer.sdk.android.internal.model.User

/**
 * Written by updateUserAttributes() on the app's thread, read on the SDK executor to build
 * requests and events. [User] is immutable and replaced whole, so readers need only @Volatile
 * to see the latest one. The update reads then writes, so it is synchronized: two app threads
 * updating at once must not lose an update.
 */
internal class UserHolder(
  user: User,
) {
  @Volatile
  private var user: User = user

  val userId: String = user.id

  fun get(): User = user

  @Synchronized
  fun updateAttributes(updater: (previous: Map<String, String>) -> Map<String, String>) {
    this.user =
      user.copy(
        data = updater(user.data),
      )
  }
}

internal fun BKTUser.toUser(): User =
  User(
    id = this.id,
    data = this.attributes,
  )

internal fun User.toBKTUser(): BKTUser =
  BKTUser(
    id = this.id,
    attributes = this.data,
  )
