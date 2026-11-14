package io.pagenote.android

import kotlin.math.abs

internal data class PdfVisiblePage(
  val pageIndex: Int,
  val topPx: Int,
  val heightPx: Int,
)

internal object PdfContinuousPagePolicy {
  fun activePage(visiblePages: List<PdfVisiblePage>, viewportCenterPx: Int): Int? =
    visiblePages.asSequence()
      .filter { it.pageIndex >= 0 && it.heightPx > 0 }
      .minWithOrNull(
        compareBy<PdfVisiblePage> {
          val pageCenter = it.topPx.toLong() + it.heightPx / 2L
          abs(pageCenter - viewportCenterPx.toLong())
        }.thenBy(PdfVisiblePage::pageIndex),
      )
      ?.pageIndex
}
