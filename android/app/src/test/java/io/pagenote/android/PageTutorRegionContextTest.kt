package io.pagenote.android

import io.pagenote.feature.ocr.OcrBlock
import io.pagenote.feature.ocr.OcrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTutorRegionContextTest {
  private val renderer = Any()
  private val region = NormalizedOcrRegion(100, 120, 500, 600)

  @Test
  fun mapsUsableRegionTextAndConfidenceToBoundPageContext() {
    val context = PageTutorRegionContext.fromResult(
      projectId = "project-1",
      projectUri = "content://project",
      pageIndex = 3,
      sourceRevision = "source-revision",
      region = region,
      rendererIdentity = renderer,
      result = OcrResult(
        engine = "test",
        status = "partial",
        blocks = listOf(
          OcrBlock("selected text", 0.82, 100.0, 120.0, 200.0, 60.0),
          OcrBlock("  ", 0.2, 100.0, 200.0, 200.0, 50.0),
        ),
      ),
      label = "Selected region",
      reviewWarning = "Review the original region.",
    )

    assertNotNull(context)
    assertEquals(3, context!!.pageIndex)
    assertEquals(region, context.region)
    assertTrue(context.focus.text.contains("selected text (82%)"))
    assertFalse(context.focus.text.contains("  (20%)"))
    assertEquals(listOf("Review the original region."), context.focus.warnings)
    assertTrue(context.matches("project-1", "content://project", 3, "source-revision", renderer))
  }

  @Test
  fun rejectsUnusableResultsAndStaleBindings() {
    val failed = result(status = "failed", text = "recognized")
    val blank = result(status = "ok", text = " \n ")
    assertNull(contextFor(failed))
    assertNull(contextFor(blank))

    val context = contextFor(result(status = "ok", text = "recognized"))!!
    assertFalse(context.matches("other", "content://project", 3, "source-revision", renderer))
    assertFalse(context.matches("project-1", "content://other", 3, "source-revision", renderer))
    assertFalse(context.matches("project-1", "content://project", 4, "source-revision", renderer))
    assertFalse(context.matches("project-1", "content://project", 3, "changed", renderer))
    assertFalse(context.matches("project-1", "content://project", 3, "source-revision", Any()))
  }

  private fun contextFor(result: OcrResult) = PageTutorRegionContext.fromResult(
    projectId = "project-1",
    projectUri = "content://project",
    pageIndex = 3,
    sourceRevision = "source-revision",
    region = region,
    rendererIdentity = renderer,
    result = result,
    label = "Selected region",
    reviewWarning = "Review the original region.",
  )

  private fun result(status: String, text: String) = OcrResult(
    engine = "test",
    status = status,
    blocks = listOf(OcrBlock(text, 0.8, 100.0, 120.0, 200.0, 60.0)),
  )
}
