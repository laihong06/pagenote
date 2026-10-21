package io.pagenote.feature.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrRegionResultMapperTest {
  @Test
  fun mapsCropBoundsToFullPageAndPreservesResultMetadata() {
    val cropResult = OcrResult(
      engine = "fixture-engine",
      status = "partial",
      blocks = listOf(OcrBlock("region text", 0.42, 100.0, 200.0, 500.0, 400.0)),
      warnings = listOf("review this crop"),
    )

    val result = OcrResultMapper.mapRegion(
      cropResult = cropResult,
      crop = OcrPixelRect(20, 10, 80, 40),
      pageWidth = 200,
      pageHeight = 100,
    )

    assertEquals("fixture-engine", result.engine)
    assertEquals("partial", result.status)
    assertEquals(listOf("review this crop"), result.warnings)
    assertEquals(true, result.imagePreserved)
    assertEquals(
      OcrBlock("region text", 0.42, 130.0, 160.0, 150.0, 120.0),
      result.blocks.single(),
    )
    OcrResultPolicy.validate(result)
  }

  @Test
  fun regionMappingKeepsAnInferenceFailureFailed() {
    val failed = OcrResult(
      engine = "fixture-engine",
      status = "failed",
      warnings = listOf("model unavailable"),
    )

    val result = OcrResultMapper.mapRegion(
      cropResult = failed,
      crop = OcrPixelRect(20, 10, 80, 40),
      pageWidth = 200,
      pageHeight = 100,
    )

    assertEquals("failed", result.status)
    assertTrue(result.blocks.isEmpty())
    assertEquals(listOf("model unavailable"), result.warnings)
    assertTrue(OcrResultPolicy.needsVisualReview(result))
  }
}
