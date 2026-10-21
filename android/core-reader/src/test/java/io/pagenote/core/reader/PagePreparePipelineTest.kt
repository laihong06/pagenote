package io.pagenote.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagePreparePipelineTest {
  @Test
  fun cacheHitSkipsWork() {
    var workCalls = 0
    val result = PagePreparePipeline.prepare(
      page = 2,
      region = PageRegion(0.0, 0.0, 100.0, 100.0),
      mode = "region-ocr",
      refresh = false,
      sourceRevision = "rev-1",
      cachedRevision = "rev-1",
      engine = "pp-ocrv6-tiny",
      work = {
        workCalls += 1
        PagePreparePipeline.Prepared("fresh", false)
      },
    )

    assertEquals("cached", result.text)
    assertTrue(result.fromCache)
    assertEquals(0, workCalls)
  }

  @Test
  fun refreshForcesWork() {
    var workCalls = 0
    val result = PagePreparePipeline.prepare(
      page = 2,
      region = PageRegion(0.0, 0.0, 100.0, 100.0),
      mode = "region-ocr",
      refresh = true,
      sourceRevision = "rev-1",
      cachedRevision = "rev-1",
      engine = "pp-ocrv6-tiny",
      work = {
        workCalls += 1
        PagePreparePipeline.Prepared("fresh", false)
      },
    )

    assertEquals("fresh", result.text)
    assertFalse(result.fromCache)
    assertEquals(1, workCalls)
  }

  @Test
  fun revisionDriftRejectsStaleCache() {
    var workCalls = 0
    val result = PagePreparePipeline.prepare(
      page = 2,
      region = PageRegion(0.0, 0.0, 100.0, 100.0),
      mode = "region-ocr",
      refresh = false,
      sourceRevision = "rev-2",
      cachedRevision = "rev-1",
      engine = "pp-ocrv6-tiny",
      work = {
        workCalls += 1
        PagePreparePipeline.Prepared("fresh", false)
      },
    )

    assertEquals("fresh", result.text)
    assertFalse(result.fromCache)
    assertEquals(1, workCalls)
  }
}
