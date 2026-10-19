package io.pagenote.core.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparedManifestTest {
  private fun manifest() = PreparedManifest(
    documentId = "doc",
    page = 6,
    sourceRevision = "c".repeat(64),
    kind = "pdf",
    region = PageRegion(0.0, 0.0, 1000.0, 1000.0),
    cacheKey = "k".repeat(64),
    width = 1200,
    height = 1600,
    scale = 1.5,
  )

  @Test
  fun validManifestPasses() {
    PreparedManifestStore.validate(manifest())
  }

  @Test
  fun staleSourceMisses() {
    val cached = manifest()
    assertTrue(PreparedManifestStore.isHit(cached, "c".repeat(64), "k".repeat(64)))
    assertFalse(PreparedManifestStore.isHit(cached, "d".repeat(64), "k".repeat(64)))
  }

  @Test(expected = IllegalArgumentException::class)
  fun badSourceRevisionRejected() {
    PreparedManifestStore.validate(manifest().copy(sourceRevision = "bad"))
  }
}
