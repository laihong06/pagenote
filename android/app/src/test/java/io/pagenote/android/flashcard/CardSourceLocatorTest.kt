package io.pagenote.android.flashcard

import org.junit.Assert.assertEquals
import org.junit.Test

/** 来源定位回跳策略（移植批次 11）：页码校验、精确命中、漂移重查、不可定位。 */
class CardSourceLocatorTest {

  @Test
  fun `pageIndex validates card page against note page count`() {
    assertEquals(0, CardSourceLocator.pageIndex(1, 3))
    assertEquals(2, CardSourceLocator.pageIndex(3, 3))
    assertEquals(null, CardSourceLocator.pageIndex(4, 3))
    assertEquals(null, CardSourceLocator.pageIndex(0, 3))
    assertEquals(null, CardSourceLocator.pageIndex(1, 0))
  }

  @Test
  fun `exact anchor hit is reported unmoved`() {
    val page = "前缀 目标文本 后缀"
    val start = page.indexOf("目标文本")
    val result = CardSourceLocator.locate(
      page,
      CardSourceLocator.Anchor(page = 1, anchorStart = start, anchorEnd = start + 4, sourceQuote = "目标文本"),
    )
    assertEquals(CardSourceLocator.LocateResult.Found(start, start + 4, moved = false), result)
  }

  @Test
  fun `stale anchor relocates by quote`() {
    val page = "编辑后的页面里还有目标文本在后面"
    val result = CardSourceLocator.locate(
      page,
      CardSourceLocator.Anchor(page = 1, anchorStart = 0, anchorEnd = 4, sourceQuote = "目标文本"),
    )
    val expected = page.indexOf("目标文本")
    assertEquals(CardSourceLocator.LocateResult.Found(expected, expected + 4, moved = true), result)
  }

  @Test
  fun `out of bounds anchor with quote still relocates`() {
    val page = "页面内容 目标文本"
    val result = CardSourceLocator.locate(
      page,
      CardSourceLocator.Anchor(page = 1, anchorStart = 50, anchorEnd = 60, sourceQuote = "目标文本"),
    )
    val expected = page.indexOf("目标文本")
    assertEquals(CardSourceLocator.LocateResult.Found(expected, expected + 4, moved = true), result)
  }

  @Test
  fun `missing quote or missing anchor is unavailable`() {
    assertEquals(
      CardSourceLocator.LocateResult.Unavailable,
      CardSourceLocator.locate(
        "页面内容",
        CardSourceLocator.Anchor(page = 1, anchorStart = 0, anchorEnd = 4, sourceQuote = "已删除的文本"),
      ),
    )
    assertEquals(
      CardSourceLocator.LocateResult.Unavailable,
      CardSourceLocator.locate(
        "页面内容",
        CardSourceLocator.Anchor(page = 1, anchorStart = -1, anchorEnd = -1, sourceQuote = ""),
      ),
    )
    assertEquals(
      CardSourceLocator.LocateResult.Unavailable,
      CardSourceLocator.locate(
        "页面内容",
        CardSourceLocator.Anchor(page = 1, anchorStart = 2, anchorEnd = 2, sourceQuote = "页面内容"),
      ),
    )
  }
}
