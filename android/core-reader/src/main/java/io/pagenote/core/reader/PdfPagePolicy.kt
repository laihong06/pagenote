package io.pagenote.core.reader

import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.ceil

object PdfPagePolicy {
  const val MAX_PIXELS = 16_000_000
  const val DEFAULT_DPI = 144
  const val POINTS_PER_INCH = 72.0

  fun renderScale(pageWidthPt: Double, pageHeightPt: Double, dpi: Int = DEFAULT_DPI): Double {
    require(pageWidthPt > 0 && pageHeightPt > 0)
    require(dpi > 0)
    val baseScale = dpi / POINTS_PER_INCH
    val basePixels = pageWidthPt * baseScale * (pageHeightPt * baseScale)
    if (basePixels <= MAX_PIXELS) return baseScale
    return baseScale * sqrt(MAX_PIXELS / basePixels)
  }

  fun renderedSize(pageWidthPt: Double, pageHeightPt: Double, dpi: Int = DEFAULT_DPI): Pair<Int, Int> {
    val scale = renderScale(pageWidthPt, pageHeightPt, dpi)
    val width = min(MAX_PIXELS, (pageWidthPt * scale).toInt().coerceAtLeast(1))
    val height = ((pageHeightPt * scale).toInt().coerceAtLeast(1))
    return width to height
  }

  fun regionPixels(
    pageWidthPx: Int,
    pageHeightPx: Int,
    region: PageRegion,
  ): IntArray {
    PreparedCache.validateRegion(region)
    require(pageWidthPx > 0 && pageHeightPx > 0)
    val left = ((region.x / 1000.0) * pageWidthPx).toInt().coerceIn(0, pageWidthPx - 1)
    val top = ((region.y / 1000.0) * pageHeightPx).toInt().coerceIn(0, pageHeightPx - 1)
    val right = ceil(((region.x + region.width) / 1000.0) * pageWidthPx).toInt()
      .coerceIn(left + 1, pageWidthPx)
    val bottom = ceil(((region.y + region.height) / 1000.0) * pageHeightPx).toInt()
      .coerceIn(top + 1, pageHeightPx)
    val width = right - left
    val height = bottom - top
    return intArrayOf(left, top, width, height)
  }
}
