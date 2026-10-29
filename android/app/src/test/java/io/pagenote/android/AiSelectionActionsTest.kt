package io.pagenote.android

import io.pagenote.core.project.PageNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSelectionActionsTest {

  // 测试上下文中的公式文本用 ASCII 美元符书写，避免 Kotlin 字符串模板把 $E 解析为变量。
  private val SELECTED = "这是需要处理的选段，包含 **加粗** 与公式 \$E=mc^2\$。"
  private val NOTE_CONTEXT = "# 笔记页\n\n$SELECTED\n\n结尾段落。"
  private val NOTE_START = NOTE_CONTEXT.indexOf(SELECTED)
  private val NOTE_END = NOTE_START + SELECTED.length

  private fun task(
    context: String = NOTE_CONTEXT,
    start: Int = NOTE_START,
    end: Int = NOTE_END,
  ): AiSelectionTask = AiSelectionTask.capture(page = 3, context = context, start = start, end = end)

  @Test
  fun captureReusesSelectionReviewBoundaries() {
    val context = NOTE_CONTEXT
    val captured = task(context = context)

    assertEquals(SELECTED, captured.quote)
    assertEquals(context, captured.context)
    assertEquals(3, captured.page)
    assertEquals(PageNotes.revision(context), captured.revision)
    assertEquals(NOTE_START, captured.start)
    assertEquals(NOTE_END, captured.end)
    // anchor 与选区审阅同构：markdown-source-utf16 + 半开区间。
    assertEquals(
      """{"coordinateSystem":"markdown-source-utf16","start":$NOTE_START,"end":$NOTE_END}""",
      captured.anchor.toString(),
    )
  }

  @Test
  fun captureRejectsWhitespaceSelectionAndOversizedContext() {
    val context = "abc\n\n   \n"
    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      AiSelectionTask.capture(page = 1, context = context, start = 3, end = 8)
    }
    val oversized = buildString { repeat(1_000_001) { append('a') } }
    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      AiSelectionTask.capture(page = 1, context = oversized, start = 0, end = 10)
    }
  }

  @Test
  fun buildMessagesComposesSystemPromptAndModules() {
    val action = AiSelectionAction.TONE_FORMAL
    val messages = AiSelectionActions.buildMessages(
      action = action,
      task = task(),
      promptModules = listOf(
        AgentPromptText("system", "SYSTEM MODULE"),
        AgentPromptText("ai-tone", "TONE MODULE"),
      ),
    )

    val system = messages.single { it["role"] == "system" }["content"].orEmpty()
    assertTrue(system.startsWith(AiSelectionActions.BASE_SYSTEM_PROMPT))
    assertTrue(system.contains("[system]\nSYSTEM MODULE"))
    assertTrue(system.contains("[ai-tone]\nTONE MODULE"))
    val user = messages.single { it["role"] == "user" }["content"].orEmpty()
    assertTrue(user.contains(AiSelectionActions.directive(action)))
    assertTrue(user.contains(task().quote))
    assertTrue(user.contains("E=mc^2"))
    assertFalse(user.contains("~~~"))
  }

  @Test
  fun buildMessagesRequireActionPromptModule() {
    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      AiSelectionActions.buildMessages(
        action = AiSelectionAction.SUMMARY,
        task = task(),
        promptModules = listOf(AgentPromptText("system", "SYSTEM MODULE")),
      )
    }
  }

  @Test
  fun directivesCoverEveryActionAndTargetLanguage() {
    AiSelectionAction.entries.forEach { action ->
      assertTrue(AiSelectionActions.directive(action).isNotBlank())
    }
    assertNull(AiSelectionAction.targetLanguage(AiSelectionAction.SUMMARY))
    assertNull(AiSelectionAction.targetLanguage(AiSelectionAction.TONE_FORMAL))
    assertEquals("zh", AiSelectionAction.targetLanguage(AiSelectionAction.TRANSLATE_ZH))
    assertEquals("en", AiSelectionAction.targetLanguage(AiSelectionAction.TRANSLATE_EN))
  }

  @Test
  fun sanitizeStripsFencesAndLeadingLabels() {
    assertEquals("改写后的正文", AiSelectionActions.sanitizeResult("```\n改写后的正文\n```"))
    assertEquals("改写后的正文", AiSelectionActions.sanitizeResult("~~~改写后的正文~~~"))
    assertEquals("第一行\n第二行", AiSelectionActions.sanitizeResult("结果：\n第一行\n第二行"))
    // 保守剥离：只剥离“整行为标签”的引导行，标签与正文同行时按原文返回。
    assertEquals("Translation: translated text", AiSelectionActions.sanitizeResult("Translation: translated text"))
    assertEquals("摘要要点", AiSelectionActions.sanitizeResult("  摘要要点  "))
    assertNull(AiSelectionActions.sanitizeResult("``` ```"))
    assertNull(AiSelectionActions.sanitizeResult("   "))
    // 带正文的标签行才剥离；正文本身以「结果：」开头但无换行时按整体返回由调用方判断。
    assertEquals("结果：没有换行", AiSelectionActions.sanitizeResult("结果：没有换行"))
  }

  @Test
  fun replacementSplicesResultIntoContext() {
    val captured = task()
    val updated = AiSelectionActions.replacement(captured, "处理结果")
    assertEquals(
      captured.context.substring(0, captured.start) + "处理结果" +
        captured.context.substring(captured.end),
      updated,
    )
    assertNotEquals(captured.context, updated)
  }

  @Test
  fun applyGuardRequiresChangedContextAndSuccessfulSave() {
    val captured = task()
    // 未变化：不标记 applied。
    assertFalse(AiSelectionActions.shouldMarkApplied(captured.context, captured.context, true))
    // 保存失败：不标记 applied。
    assertFalse(
      AiSelectionActions.shouldMarkApplied(
        captured.context,
        AiSelectionActions.replacement(captured, "新文本"),
        false,
      ),
    )
    // 变化且保存成功：允许 applied。
    assertTrue(
      AiSelectionActions.shouldMarkApplied(
        captured.context,
        AiSelectionActions.replacement(captured, "新文本"),
        true,
      ),
    )
  }

  @Test
  fun requireCurrentPageRejectsDriftedPage() {
    val captured = task()
    captured.requireCurrentPage(captured.context) // 不抛
    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      captured.requireCurrentPage(captured.context + "\n外部修改")
    }
  }

  @Test
  fun translationLanguageCodeMapsProviderDialects() {
    assertEquals("zh", translationLanguageCode(TranslationProvider.BAIDU, "zh"))
    assertEquals("en", translationLanguageCode(TranslationProvider.BAIDU, "EN"))
    assertEquals("zh-CHS", translationLanguageCode(TranslationProvider.YOUDAO, "zh"))
    assertEquals("zh-CHS", translationLanguageCode(TranslationProvider.YOUDAO, "zh-CN"))
    assertEquals("fr", translationLanguageCode(TranslationProvider.NONE, "fr"))
  }
}
