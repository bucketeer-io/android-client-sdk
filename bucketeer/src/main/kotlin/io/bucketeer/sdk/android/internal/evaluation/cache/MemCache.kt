package io.bucketeer.sdk.android.internal.evaluation.cache

internal interface MemCache<in Key : Any, Value : Any> {
  fun set(key: Key, value: Value)
  fun get(key: Key): Value?

  class Builder<K : Any, V : Any> {
    fun build(): MemCache<K, V> {
      return MemCacheImpl()
    }
  }
}

private class MemCacheImpl<in Key : Any, Value : Any> : MemCache<Key, Value> {
  private val map = mutableMapOf<Key, Value>()
  override fun set(key: Key, value: Value) {
    map[key] = value
  }

  override fun get(key: Key): Value? {
    return map[key]
  }
}
