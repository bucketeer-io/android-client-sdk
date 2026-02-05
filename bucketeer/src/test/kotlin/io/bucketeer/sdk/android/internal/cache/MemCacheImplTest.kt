package io.bucketeer.sdk.android.internal.cache

import org.junit.Test

class MemCacheImplTest {
  @Test
  fun setAndGet() {
    val cache: MemCache<String, String> = MemCache.Builder<String, String>().build()
    cache.set("key", "value")
    val result = cache.get("key")
    assert(result == "value")
  }
}
