package io.pagenote.core.search

/** 搜索结果跳转策略：页码合法性与 rect → 视口归一化中心。 */
object SearchJumpPolicy {
  /**
   * hit.page（1 起）→ 阅读器 pageIndex（0 起）；超出当前文档页数时为 null
   * （索引可能滞后于文档变化，调用方给出可恢复提示而不是越界跳页）。
   */
  fun pageIndex(hitPage: Int, pageCount: Int): Int? {
    if (pageCount <= 0) return null
    return hitPage.takeIf { it in 1..pageCount }?.minus(1)
  }

  /**
   * 归一化 rect（0..1000）→ PdfViewportBookmark 的归一化中心（0..1）。
   * x 按页宽、y 按页高，与 OCR 选区语义一致。
   */
  fun focusCenter(rect: SearchRect): Pair<Float, Float> {
    val centerX = ((rect.left + rect.right) / 2000.0).toFloat().coerceIn(0f, 1f)
    val centerY = ((rect.top + rect.bottom) / 2000.0).toFloat().coerceIn(0f, 1f)
    return centerX to centerY
  }
}
