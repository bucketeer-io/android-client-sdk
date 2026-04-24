package io.bucketeer.sdk.android.internal.cache

internal interface MemCache<in Key : Any, Value : Any> {
  fun set(
    key: Key,
    value: Value,
  )

  fun get(key: Key): Value?

  class Builder<K : Any, V : Any> {
    fun build(): MemCache<K, V> = MemCacheImpl()
  }
}

private class MemCacheImpl<in Key : Any, Value : Any> : MemCache<Key, Value> {
  private val map = mutableMapOf<Key, Value>()

  // Use a private lock object instead of synchronizing on 'this' to prevent external callers
  // from holding the same lock, which could lead to lock contention or deadlock issues.
  private val lock = Any()

  override fun set(
    key: Key,
    value: Value,
  ) {
    synchronized(lock) {
      map[key] = value
    }
  }

  override fun get(key: Key): Value? {
    synchronized(lock) {
      return map[key]
    }
  }
}
