package io.bucketeer.sdk.android.internal.event

internal data class EventEntity(
  val event: ByteArray,
) {
  companion object {
    const val TABLE_NAME = "event"
    const val COLUMN_ID = "id"
    const val COLUMN_EVENT = "event"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is EventEntity) return false

    if (!event.contentEquals(other.event)) return false

    return true
  }

  override fun hashCode(): Int {
    return event.contentHashCode()
  }
}
