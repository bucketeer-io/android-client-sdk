package io.bucketeer.sdk.android.internal.user

import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.internal.model.User
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The user is written on the app's thread (updateUserAttributes) and read on the SDK executor
 * (polling, the stream request, events). May pass even without @Volatile on the field: the
 * annotation itself is checked in review.
 */
class UserHolderConcurrencyTest {
  @Test
  fun `attribute updates on another thread are seen whole by request building`() {
    val holder = UserHolder(User(id = "user-1", data = mapOf("v" to "0", "w" to "0")))
    val executor = Executors.newSingleThreadExecutor()
    val writing = AtomicBoolean(true)
    val lastWritten = AtomicInteger(0)

    val writer =
      Thread {
        var i = 1
        while (writing.get()) {
          val value = "$i"
          holder.updateAttributes { mapOf("v" to value, "w" to value) }
          lastWritten.set(i)
          i++
        }
      }
    writer.start()

    try {
      val built =
        (1..1_000).map {
          executor.submit<Map<String, String>> { holder.get().data }.get(5, TimeUnit.SECONDS)
        }
      writing.set(false)
      writer.join(5_000)

      built.forEach { assertThat(it["v"]).isEqualTo(it["w"]) }
      val last = executor.submit<Map<String, String>> { holder.get().data }.get(5, TimeUnit.SECONDS)
      assertThat(last["v"]).isEqualTo("${lastWritten.get()}")
    } finally {
      writing.set(false)
      executor.shutdownNow()
    }
  }
}
