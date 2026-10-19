package io.pagenote.feature.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrResultTest {
  private fun ok() = OcrResult(
    engine = "fixture",
    status = "ok",
    blocks = listOf(OcrBlock("你好", 0.9, 10.0, 10.0, 100.0, 20.0)),
  )

  @Test
  fun validResultPasses() {
    OcrResultPolicy.validate(ok())
    assertFalse(OcrResultPolicy.needsVisualReview(ok()))
  }

  @Test
  fun lowConfidenceNeedsReview() {
    val low = ok().copy(blocks = listOf(OcrBlock("疑", 0.2, 10.0, 10.0, 20.0, 20.0)))
    OcrResultPolicy.validate(low)
    assertTrue(OcrResultPolicy.needsVisualReview(low))
  }

  @Test
  fun failedNeedsReviewButKeepsImage() {
    val failed = OcrResult("fixture", "failed", warnings = listOf("ocr engine error"))
    OcrResultPolicy.validate(failed)
    assertTrue(OcrResultPolicy.needsVisualReview(failed))
  }
}
