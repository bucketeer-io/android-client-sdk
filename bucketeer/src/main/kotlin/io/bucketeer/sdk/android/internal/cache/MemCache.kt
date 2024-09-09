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

  override fun set(
    key: Key,
    value: Value,
  ) {
    map[key] = value
  }

  override fun get(key: Key): Value? = map[key]
}
