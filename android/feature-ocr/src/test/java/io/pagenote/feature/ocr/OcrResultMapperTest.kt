package io.pagenote.feature.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrResultMapperTest {
  @Test
  fun mapsRecognitionAndQuadToPageCoordinates() {
    val result = OcrResultMapper.map(
      imageWidth = 2000,
      imageHeight = 1000,
      recognized = listOf(
        RecognizedTextBlock(
          text = "离线 OCR",
          confidence = 0.87,
          points = listOf(
            OcrPoint(300f, 150f),
            OcrPoint(100f, 150f),
            OcrPoint(100f, 50f),
            OcrPoint(300f, 50f),
          ),
        ),
      ),
    )

    val block = result.blocks.single()
    assertEquals("pp-ocrv6-tiny-onnx", result.engine)
    assertEquals("ok", result.status)
    assertEquals("离线 OCR", block.text)
    assertEquals(0.87, block.confidence, 0.0001)
    assertEquals(50.0, block.x, 0.0001)
    assertEquals(50.0, block.y, 0.0001)
    assertEquals(100.0, block.width, 0.0001)
    assertEquals(100.0, block.height, 0.0001)
  }

  @Test
  fun clipsBoxesToImageAndMarksDiscardedDegenerateBoxes() {
    val result = OcrResultMapper.map(
      imageWidth = 100,
      imageHeight = 100,
      recognized = listOf(
        RecognizedTextBlock(
          text = "edge",
          confidence = 0.8,
          points = listOf(
            OcrPoint(-10f, 10f), OcrPoint(20f, 10f),
            OcrPoint(20f, 30f), OcrPoint(-10f, 30f),
          ),
        ),
        RecognizedTextBlock(
          text = "outside",
          confidence = 0.8,
          points = listOf(
            OcrPoint(110f, 110f), OcrPoint(120f, 110f),
            OcrPoint(120f, 120f), OcrPoint(110f, 120f),
          ),
        ),
      ),
    )

    assertEquals(1, result.blocks.size)
    assertEquals(0.0, result.blocks.single().x, 0.0001)
    assertEquals(200.0, result.blocks.single().width, 0.0001)
    assertTrue(result.warnings.isNotEmpty())
    assertTrue(OcrResultPolicy.needsVisualReview(result))
  }

  @Test
  fun emptyDetectionResultRequiresVisualReview() {
    val result = OcrResultMapper.map(
      imageWidth = 1200,
      imageHeight = 1800,
      recognized = emptyList(),
    )

    assertEquals("partial", result.status)
    assertTrue(result.warnings.any { it.contains("No text") })
    assertTrue(OcrResultPolicy.needsVisualReview(result))
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsAnEmptyImageSize() {
    OcrResultMapper.map(0, 100, emptyList())
  }
}
