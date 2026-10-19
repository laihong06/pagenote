package io.pagenote.feature.ocr

import io.pagenote.core.reader.PageRegion
import io.pagenote.core.reader.PdfPagePolicy

data class NormalizedOcrRegion(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
) {
  fun toPixelRect(imageWidth: Int, imageHeight: Int): OcrPixelRect {
    require(imageWidth > 0 && imageHeight > 0) { "OCR image dimensions must be positive" }
    val pixels = PdfPagePolicy.regionPixels(
      imageWidth,
      imageHeight,
      PageRegion(left.toDouble(), top.toDouble(), (right - left).toDouble(), (bottom - top).toDouble()),
    )
    return OcrPixelRect(
      left = pixels[0],
      top = pixels[1],
      right = pixels[0] + pixels[2],
      bottom = pixels[1] + pixels[3],
    )
  }
}

data class OcrPixelRect(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
) {
  init {
    require(left >= 0 && top >= 0) { "OCR crop origin must be non-negative" }
    require(right > left && bottom > top) { "OCR crop must have positive area" }
  }

  val width: Int get() = right - left
  val height: Int get() = bottom - top
}
