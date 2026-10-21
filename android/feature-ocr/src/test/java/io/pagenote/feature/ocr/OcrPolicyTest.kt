package io.pagenote.feature.ocr

import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPolicyTest {
  @Test
  fun mergedTextKeepsBothChannels() {
    val merged = OcrPolicy.mergedText("native", "ocr")
    assertTrue(merged.contains("native"))
    assertTrue(merged.contains("ocr"))
  }
}
