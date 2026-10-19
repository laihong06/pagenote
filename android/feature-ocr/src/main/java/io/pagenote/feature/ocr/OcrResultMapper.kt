package io.pagenote.feature.ocr

data class OcrPoint(val x: Float, val y: Float)

data class RecognizedTextBlock(
  val text: String,
  val confidence: Double,
  val points: List<OcrPoint>,
)

object OcrResultMapper {
  const val ENGINE = "pp-ocrv6-tiny-onnx"

  fun mapRegion(
    cropResult: OcrResult,
    crop: OcrPixelRect,
    pageWidth: Int,
    pageHeight: Int,
  ): OcrResult {
    require(pageWidth > 0 && pageHeight > 0) { "OCR image dimensions must be positive" }
    require(crop.right <= pageWidth && crop.bottom <= pageHeight) { "OCR crop is outside the image" }
    OcrResultPolicy.validate(cropResult)

    val blocks = cropResult.blocks.map { block ->
      val left = ((crop.left + block.x * crop.width / 1000.0) * 1000.0 / pageWidth)
        .coerceIn(0.0, 1000.0)
      val top = ((crop.top + block.y * crop.height / 1000.0) * 1000.0 / pageHeight)
        .coerceIn(0.0, 1000.0)
      val right = ((crop.left + (block.x + block.width) * crop.width / 1000.0) * 1000.0 / pageWidth)
        .coerceIn(left, 1000.0)
      val bottom = ((crop.top + (block.y + block.height) * crop.height / 1000.0) * 1000.0 / pageHeight)
        .coerceIn(top, 1000.0)
      block.copy(x = left, y = top, width = right - left, height = bottom - top)
    }

    return cropResult.copy(blocks = blocks).also(OcrResultPolicy::validate)
  }

  fun map(
    imageWidth: Int,
    imageHeight: Int,
    recognized: List<RecognizedTextBlock>,
  ): OcrResult {
    require(imageWidth > 0 && imageHeight > 0) { "OCR image dimensions must be positive" }

    val warnings = mutableListOf<String>()
    if (recognized.isEmpty()) {
      warnings += "No text was detected; verify the original image."
    }
    val blocks = recognized.mapNotNull { item ->
      require(item.points.size == 4) { "OCR quadrilateral must contain four points" }
      require(item.confidence in 0.0..1.0) { "OCR confidence must be between 0 and 1" }
      require(item.points.all { it.x.isFinite() && it.y.isFinite() }) { "OCR box coordinates must be finite" }

      val left = item.points.minOf { it.x }.coerceIn(0f, imageWidth.toFloat())
      val top = item.points.minOf { it.y }.coerceIn(0f, imageHeight.toFloat())
      val right = item.points.maxOf { it.x }.coerceIn(0f, imageWidth.toFloat())
      val bottom = item.points.maxOf { it.y }.coerceIn(0f, imageHeight.toFloat())

      if (item.text.isBlank() || right <= left || bottom <= top) {
        warnings += "An OCR region had no usable text or area and was omitted."
        null
      } else {
        OcrBlock(
          text = item.text,
          confidence = item.confidence,
          x = left * 1000.0 / imageWidth,
          y = top * 1000.0 / imageHeight,
          width = (right - left) * 1000.0 / imageWidth,
          height = (bottom - top) * 1000.0 / imageHeight,
        )
      }
    }

    return OcrResult(
      engine = ENGINE,
      status = if (warnings.isEmpty()) "ok" else "partial",
      blocks = blocks,
      warnings = warnings,
    ).also(OcrResultPolicy::validate)
  }
}
