package io.pagenote.android.flashcard

import io.pagenote.core.project.PageNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 制卡捕获策略（移植批次 11）：选区/段落边界与标签规范化。 */
class FlashcardCaptureTest {

  @Test
  fun `fromSelection keeps raw quote and trims front`() {
    val page = "前缀 **加粗内容** 后缀"
    val start = page.indexOf("**")
    val end = page.indexOf("** 后缀")
    val draft = FlashcardCapture.fromSelection(3, page, start, end + 2)
    assertEquals(3, draft.page)
    assertEquals(FlashcardSourceKind.NOTE_SELECTION, draft.sourceKind)
    assertEquals("**加粗内容**", draft.front)
    assertEquals(page.substring(start, end + 2), draft.sourceQuote)
    assertEquals(start, draft.anchorStart)
    assertEquals(end + 2, draft.anchorEnd)
    assertEquals(PageNotes.revision(page), draft.revision)
  }

  @Test
  fun `fromSelection accepts swapped start end`() {
    val page = "abcdef"
    val draft = FlashcardCapture.fromSelection(1, page, 4, 1)
    assertEquals("bcd", draft.front)
    assertEquals(1, draft.anchorStart)
    assertEquals(4, draft.anchorEnd)
  }

  @Test
  fun `fromSelection rejects empty outside page and blank`() {
    assertThrows(IllegalArgumentException::class.java) {
      FlashcardCapture.fromSelection(1, "abc", 2, 2)
    }
    assertThrows(IllegalArgumentException::class.java) {
      FlashcardCapture.fromSelection(1, "abc", 0, 9)
    }
    assertThrows(IllegalArgumentException::class.java) {
      FlashcardCapture.fromSelection(1, "abc", 0, 0)
    }
    assertThrows(IllegalArgumentException::class.java) {
      FlashcardCapture.fromSelection(1, "   ", 0, 3)
    }
    assertThrows(IllegalArgumentException::class.java) {
      FlashcardCapture.fromSelection(0, "abc", 0, 1)
    }
  }

  @Test
  fun `paragraphAround finds block containing cursor`() {
    val page = "第一段第一行\n第一段第二行\n\n第二段\n\n   \n第三段"
    assertEquals(
      page.indexOf("第一段第一行") to page.indexOf("第一段第二行") + "第一段第二行".length,
      FlashcardCapture.paragraphAround(page, page.indexOf("第一段第二行") + 1),
    )
    assertEquals(
      page.indexOf("第二段") to page.indexOf("第二段") + "第二段".length,
      FlashcardCapture.paragraphAround(page, page.indexOf("第二段")),
    )
    assertEquals(
      page.indexOf("第三段") to page.length,
      FlashcardCapture.paragraphAround(page, page.length),
    )
  }

  @Test
  fun `paragraphAround returns null on blank line or empty page`() {
    val page = "段落一\n\n段落二"
    assertEquals(null, FlashcardCapture.paragraphAround(page, page.indexOf("\n\n") + 1))
    assertEquals(null, FlashcardCapture.paragraphAround("", 0))
  }

  @Test
  fun `fromParagraph captures cursor paragraph with revision`() {
    val page = "intro\n\n目标段落内容\n\noutro"
    val draft = FlashcardCapture.fromParagraph(2, page, page.indexOf("目标段落") + 2)
    assertEquals(FlashcardSourceKind.NOTE_PARAGRAPH, draft.sourceKind)
    assertEquals("目标段落内容", draft.front)
    assertEquals("目标段落内容", draft.sourceQuote)
    assertEquals(page.indexOf("目标段落"), draft.anchorStart)
    assertEquals(PageNotes.revision(page), draft.revision)
  }

  @Test
  fun `fromParagraph throws when cursor on blank line`() {
    assertThrows(IllegalArgumentException::class.java) {
      FlashcardCapture.fromParagraph(1, "a\n\nb", 2)
    }
  }

  @Test
  fun `normalizeTags splits trims dedupes and caps`() {
    assertEquals(
      listOf("算法", "阅读"),
      FlashcardCapture.normalizeTags(" 算法 ，、阅读；算法\n"),
    )
    assertEquals(listOf("书名 章节"), FlashcardCapture.normalizeTags("书名 章节"))
    assertEquals(listOf("a", "b"), FlashcardCapture.normalizeTags("a,b,,b,a"))
    assertEquals(
      listOf("x".repeat(32)),
      FlashcardCapture.normalizeTags("x".repeat(32)),
    )
    // 超长标签丢弃；超过 12 个截断。
    assertEquals(emptyList<String>(), FlashcardCapture.normalizeTags("x".repeat(33)))
    val many = (1..15).joinToString(",") { "t$it" }
    assertEquals(12, FlashcardCapture.normalizeTags(many).size)
    assertEquals("t12", FlashcardCapture.normalizeTags(many).last())
  }
}
