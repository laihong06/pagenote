package io.pagenote.android

import kotlin.math.abs

enum class PdfPageSwipeDirection { PREVIOUS, NEXT }

object PdfPageSwipePolicy {
  private const val MIN_VERTICAL_DISTANCE_DP = 72f
  private const val VERTICAL_DOMINANCE = 1.35f

  fun direction(deltaX: Float, deltaY: Float, density: Float): PdfPageSwipeDirection? {
    if (listOf(deltaX, deltaY, density).any { !it.isFinite() } || density <= 0f) return null
    val verticalDistance = abs(deltaY)
    if (verticalDistance < MIN_VERTICAL_DISTANCE_DP * density ||
      verticalDistance < abs(deltaX) * VERTICAL_DOMINANCE
    ) {
      return null
    }
    return if (deltaY < 0f) PdfPageSwipeDirection.NEXT else PdfPageSwipeDirection.PREVIOUS
  }

  fun targetPage(
    pageIndex: Int,
    pageCount: Int,
    direction: PdfPageSwipeDirection,
  ): Int? {
    if (pageIndex !in 0 until pageCount) return null
    return when (direction) {
      PdfPageSwipeDirection.PREVIOUS -> (pageIndex - 1).takeIf { it >= 0 }
      PdfPageSwipeDirection.NEXT -> (pageIndex + 1).takeIf { it < pageCount }
    }
  }
}
