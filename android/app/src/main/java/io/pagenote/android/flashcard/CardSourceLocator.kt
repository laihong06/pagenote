package io.pagenote.android.flashcard

/**
 * 来源定位回跳（移植批次 11 / 差距清单 #20）：卡片 → 捕获时的笔记页与选区。
 *
 * 策略与搜索跳转（SearchJumpPolicy）互补：页码经笔记页数校验；选区锚点先按
 * 捕获偏移精确校验（页文本与 sourceQuote 一致），失配后在页内重查原文位置
 * （内容编辑导致偏移漂移），仍找不到则判定不可定位（调用方给出可恢复提示）。
 */
object CardSourceLocator {

  /** 回跳锚点（来自 FlashcardEntity 的定位字段投影）。 */
  data class Anchor(
    val page: Int,
    val anchorStart: Int,
    val anchorEnd: Int,
    val sourceQuote: String,
  )

  sealed interface LocateResult {
    /** [start]/[end] 为应应用的选区；[moved] 表示偏移已漂移、按原文重查定位。 */
    data class Found(val start: Int, val end: Int, val moved: Boolean) : LocateResult

    /** 无法定位：锚点越界且原文已不存在于当前页文本。 */
    data object Unavailable : LocateResult
  }

  /** 卡片页码（1 起）→ 笔记页索引（0 起）；超出笔记页数时为 null。 */
  fun pageIndex(cardPage: Int, notePageCount: Int): Int? {
    if (notePageCount <= 0) return null
    return cardPage.takeIf { it in 1..notePageCount }?.minus(1)
  }

  /**
   * 在目标页文本中定位选区。无锚点（start < 0，如导图占位卡）返回 Unavailable。
   * 精确命中时 [LocateResult.Found.moved] 为 false。
   */
  fun locate(pageText: String, anchor: Anchor): LocateResult {
    if (anchor.anchorStart < 0 || anchor.anchorEnd <= anchor.anchorStart) return LocateResult.Unavailable
    if (anchor.anchorStart >= pageText.length || anchor.anchorEnd > pageText.length) {
      return relocate(pageText, anchor)
    }
    if (pageText.substring(anchor.anchorStart, anchor.anchorEnd) == anchor.sourceQuote) {
      return LocateResult.Found(anchor.anchorStart, anchor.anchorEnd, moved = false)
    }
    return relocate(pageText, anchor)
  }

  private fun relocate(pageText: String, anchor: Anchor): LocateResult {
    if (anchor.sourceQuote.isEmpty()) return LocateResult.Unavailable
    val index = pageText.indexOf(anchor.sourceQuote)
    if (index < 0) return LocateResult.Unavailable
    return LocateResult.Found(index, index + anchor.sourceQuote.length, moved = true)
  }
}
