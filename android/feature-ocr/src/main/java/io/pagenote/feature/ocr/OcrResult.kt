package io.pagenote.feature.ocr

data class OcrBlock(
  val text: String,
  val confidence: Double,
  val x: Double,
  val y: Double,
  val width: Double,
  val height: Double,
)

data class OcrResult(
  val engine: String,
  val status: String,
  val blocks: List<OcrBlock> = emptyList(),
  val warnings: List<String> = emptyList(),
  val imagePreserved: Boolean = true,
)

object OcrResultPolicy {
  const val LOW_CONFIDENCE = 0.6

  fun validate(result: OcrResult) {
    require(result.engine.isNotBlank()) { "bad engine" }
    require(result.status in setOf("ok", "partial", "failed", "not-needed")) { "bad status" }
    require(result.imagePreserved) { "original image must stay preserved" }
    result.blocks.forEach { block ->
      require(block.confidence in 0.0..1.0) { "bad confidence" }
      require(block.x >= 0 && block.y >= 0 && block.width > 0 && block.height > 0) { "bad block rect" }
      require(block.x + block.width <= 1000 && block.y + block.height <= 1000) { "block outside page" }
    }
  }

  fun needsVisualReview(result: OcrResult): Boolean {
    if (result.status != "ok") return true
    if (result.warnings.isNotEmpty()) return true
    return result.blocks.any { it.confidence < LOW_CONFIDENCE }
  }

  fun textOf(result: OcrResult): String {
    return result.blocks.joinToString(" ") { it.text }
  }
}
