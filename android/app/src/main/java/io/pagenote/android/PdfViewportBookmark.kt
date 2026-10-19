package io.pagenote.android

data class PdfViewportBookmark(
  val scale: Float,
  val centerX: Float,
  val centerY: Float,
) {
  fun toTransform(bounds: PdfViewportBounds): PdfViewportTransform {
    if (!bounds.hasValidMeasurements() || !scale.isFinite() || scale <= 0f ||
      !centerX.isFinite() || !centerY.isFinite()
    ) {
      return PdfViewportTransform.DEFAULT
    }

    val normalizedCenterX = centerX.coerceIn(0f, 1f).toDouble()
    val normalizedCenterY = centerY.coerceIn(0f, 1f).toDouble()
    val scaleDouble = scale.toDouble()
    val pageWidth = bounds.pageWidthPx.toDouble()
    val pageHeight = bounds.pageHeightPx.toDouble()
    val viewportWidth = bounds.viewportWidthPx.toDouble()
    val viewportHeight = bounds.viewportHeightPx.toDouble()
    val maxTranslationX = ((pageWidth * scaleDouble - viewportWidth) / 2.0)
      .coerceAtLeast(0.0)
      .coerceAtMost(Float.MAX_VALUE.toDouble())
    val maxTranslationY = ((pageHeight * scaleDouble - viewportHeight) / 2.0)
      .coerceAtLeast(0.0)
      .coerceAtMost(Float.MAX_VALUE.toDouble())
    val translationX = ((0.5 - normalizedCenterX) * pageWidth * scaleDouble)
      .coerceIn(-maxTranslationX, maxTranslationX)
    val translationY = ((0.5 - normalizedCenterY) * pageHeight * scaleDouble)
      .coerceIn(-maxTranslationY, maxTranslationY)

    return PdfViewportTransform(
      scale = scale,
      translationX = translationX.toFloat(),
      translationY = translationY.toFloat(),
    )
  }

  companion object {
    val DEFAULT = PdfViewportBookmark(
      scale = PdfViewportTransform.DEFAULT.scale,
      centerX = 0.5f,
      centerY = 0.5f,
    )

    fun fromTransform(
      transform: PdfViewportTransform,
      bounds: PdfViewportBounds,
    ): PdfViewportBookmark {
      if (!bounds.hasValidMeasurements() || !transform.scale.isFinite() || transform.scale <= 0f ||
        !transform.translationX.isFinite() || !transform.translationY.isFinite()
      ) {
        return DEFAULT
      }

      val scale = transform.scale.toDouble()
      val centerX = (0.5 - transform.translationX.toDouble() / scale /
        bounds.pageWidthPx.toDouble()).coerceIn(0.0, 1.0)
      val centerY = (0.5 - transform.translationY.toDouble() / scale /
        bounds.pageHeightPx.toDouble()).coerceIn(0.0, 1.0)
      return PdfViewportBookmark(
        scale = transform.scale,
        centerX = centerX.toFloat(),
        centerY = centerY.toFloat(),
      )
    }

    private fun PdfViewportBounds.hasValidMeasurements(): Boolean =
      listOf(viewportWidthPx, viewportHeightPx, pageWidthPx, pageHeightPx)
        .all { it.isFinite() && it > 0f }
  }
}
