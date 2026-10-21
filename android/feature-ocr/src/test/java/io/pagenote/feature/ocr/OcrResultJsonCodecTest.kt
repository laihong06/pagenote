package io.pagenote.feature.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrResultJsonCodecTest {
  @Test
  fun ocrResultRoundTripsCoordinatesWarningsAndOwnership() {
    val expected = OcrResult(
      engine = "offline-v2",
      status = "partial",
      blocks = listOf(
        OcrBlock("text", 0.8123456789, 12.125, 34.25, 100.5, 200.75),
        OcrBlock("other", 0.6, 300.125, 400.5, 20.25, 30.875),
      ),
      warnings = listOf("low confidence", "page preserved"),
      imagePreserved = true,
    )
    assertEquals(expected, OcrResultJsonCodec.decode(OcrResultJsonCodec.encode(expected)))
  }
}
