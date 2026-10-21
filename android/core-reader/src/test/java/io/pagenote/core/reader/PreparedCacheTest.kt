package io.pagenote.core.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class PreparedCacheTest {
  @Test
  fun cacheKeyStableAndFolderPrefixed() {
    val region = PageRegion(0.0, 0.0, 1000.0, 1000.0)
    val a = PreparedCache.cacheKey("a".repeat(64), "auto", region, "engine-1")
    val b = PreparedCache.cacheKey("a".repeat(64), "auto", region, "engine-1")
    assertEquals(a, b)
    assertEquals("6-$a".take(0) + "6-" + a.take(24), PreparedCache.folderName(6, a))
  }

  @Test(expected = IllegalArgumentException::class)
  fun regionOutsidePageRejected() {
    PreparedCache.cacheKey("a".repeat(64), "auto", PageRegion(900.0, 900.0, 200.0, 200.0), "engine-1")
  }
}
