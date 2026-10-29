package io.pagenote.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class NotePlainTextTest {
  @Test
  fun stripsCommonMarkdownMarkersAndKeepsReadableText() {
    assertEquals(
      "标题一行\n要点 与 链接\n引用\n保留数字列表项\n[图:截图]",
      NotePlainText.plainify(
        """
        # 标题一行
        **要点** 与 [链接](https://example.com)
        > 引用
        2. 保留数字列表项
        ![截图](assets/x.png)
        """.trimIndent(),
      ),
    )
  }

  @Test
  fun dropsFencesAndBlankLinesButKeepsPlainText() {
    assertEquals(
      "代码外的文字\nplain code",
      NotePlainText.plainify(
        """
        代码外的文字
        ```
        plain code
        ```
        """.trimIndent(),
      ),
    )
  }

  @Test
  fun emptyAndBlankNotesProduceEmptyText() {
    assertEquals("", NotePlainText.plainify(""))
    assertEquals("", NotePlainText.plainify("\n\n  \n"))
  }

  @Test
  fun inlineMarkersInsideHeadingsAndListsAreStripped() {
    assertEquals("项目 *没有残留星号", NotePlainText.plainify("- **项目** *没有残留星号"))
  }
}
