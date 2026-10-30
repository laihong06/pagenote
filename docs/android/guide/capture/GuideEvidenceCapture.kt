package io.pagenote.android

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.core.project.PageNotes
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Opt-in screenshot capture harness; see guide/capture.md. No application runtime patch. */
@RunWith(AndroidJUnit4::class)
class GuideEvidenceCapture {
  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
  private val inst get() = InstrumentationRegistry.getInstrumentation()
  private val context get() = composeRule.activity
  private val sampleName = "学习示例 · 蚁群算法"
  private val notes = """
    # 蚁群算法：从局部选择到完整路线

    信息素记录此前搜索的经验，距离启发让较近的城市更容易被选择。

    ## 下一步怎样选择？

    对尚未访问的城市，综合考虑信息素与距离，计算选择概率。

    ${'$'}${'$'}
    p(i,j)=\frac{\tau_{ij}^{\alpha}\eta_{ij}^{\beta}}{\sum_{k\in U}\tau_{ik}^{\alpha}\eta_{ik}^{\beta}}
    ${'$'}${'$'}

    ```python
    weight = pheromone ** alpha / distance ** beta
    ```

    ```mermaid
    flowchart LR
      A[构造路线] --> B[评价长度]
      B --> C[挥发与强化]
      C --> A
    ```

    | 要素 | 在搜索中的作用 |
    | --- | --- |
    | 信息素 | 共享成功路线的经验 |
    | 距离启发 | 偏向较近的城市 |

    这是自编离线学习材料，不是在线 AI 生成结果。
  """.trimIndent()

  private fun installAndShowShelf() {
    runBlocking {
      readerSettingsStore(context).save(ReaderSettings(language = "zh-CN", theme = "light"))
    }
    agentModelSettingsStore(context).save(
      AgentModelSettingsDraft(baseUrl = "https://example.invalid/v1", model = "demo-model"),
      AgentKeyChange.Clear,
    )
    val resolver = inst.context.contentResolver
    val id = UUID.randomUUID().toString().replace("-", "").repeat(2)
    resolver.call(ExistingProjectSafTestProvider.AUTHORITY, ExistingProjectSafTestProvider.METHOD_INSTALL, null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST,
          """{"format":"pagenote-project","version":1,"id":"$id","name":"$sampleName","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"蚁群算法.pdf"}""".toByteArray())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, samplePdf())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES,
          PageNotes.join(listOf(notes, "# 复习与自测\n\n请解释为什么信息素不能无限累积。" )).toByteArray())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
      })
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val tree = existingProjectTestTreeUri()
    context.grantUriPermission(context.packageName, tree,
      flags or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
    context.contentResolver.takePersistableUriPermission(tree, flags)
    runBlocking { BookshelfStore(context).bind(tree.toString(), "演示书架") }
    composeRule.waitUntil(20_000) {
      composeRule.onAllNodesWithTag("bookshelf_book_$sampleName").fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun openReader() {
    composeRule.onNodeWithTag("bookshelf_book_$sampleName").performClick()
    composeRule.waitUntil(30_000) {
      composeRule.onAllNodesWithTag("pdf_ink_canvas").fetchSemanticsNodes().firstOrNull()?.size?.width?.let { it > 0 } == true
    }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitForIdle()
    collapse()
  }

  private fun collapse() {
    if (composeRule.onAllNodesWithTag("reader_menu_collapse").fetchSemanticsNodes().isNotEmpty())
      composeRule.onNodeWithTag("reader_menu_collapse").performClick()
  }
  private fun command(group: String, id: String) {
    if (composeRule.onAllNodesWithTag("reader_menu_expand").fetchSemanticsNodes().isNotEmpty())
      composeRule.onNodeWithTag("reader_menu_expand").performClick()
    revealReaderMenuCommand(composeRule, group, id)
    composeRule.waitUntil(10_000) {
      runCatching { composeRule.onNodeWithTag("reader_menu_command_$id").assertIsEnabled() }.isSuccess
    }
    composeRule.onNodeWithTag("reader_menu_command_$id").performScrollTo().assertIsEnabled().performClick()
    composeRule.waitForIdle()
  }
  private fun back() {
    inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    composeRule.waitForIdle()
  }
  private fun hideKeyboard() {
    composeRule.runOnUiThread {
      context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(context.window.decorView.windowToken, 0)
    }
    SystemClock.sleep(400)
  }
  private fun snap(name: String) {
    composeRule.waitForIdle()
    SystemClock.sleep(450)
    val image = requireNotNull(inst.uiAutomation.takeScreenshot())
    val out = File(context.getExternalFilesDir(null), "review-evidence").apply { mkdirs() }
    val file = File(out, "$name.png")
    file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    image.recycle()
    shell("mkdir -p /sdcard/Download/pagenote-review-evidence")
    shell("cp ${file.absolutePath} /sdcard/Download/pagenote-review-evidence/$name.png")
    println("CAPTURE $name")
  }
  private fun shell(command: String) {
    android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
  }
  private fun editor(): LongTokenMarkdownEditText =
    context.window.decorView.findViewWithTag("note_markdown_editor") ?: error("note editor missing")

  @Test fun captureReaderAndStudyModules() {
    installAndShowShelf()
    snap("01-bookshelf")
    openReader()
    snap("02-reading")
    command("handwriting", "shape")
    snap("03-shape-picker")
    composeRule.onNodeWithTag("annotation_shape_rectangle").performScrollTo().performClick()
    collapse()
    composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput {
      down(Offset(width * 0.2f, height * 0.45f)); moveTo(Offset(width * 0.8f, height * 0.65f), delayMillis = 500); up()
    }
    snap("04-shape-created")
    if (composeRule.onAllNodesWithTag("reader_menu_expand").fetchSemanticsNodes().isNotEmpty())
      composeRule.onNodeWithTag("reader_menu_expand").performClick()
    revealReaderMenuCommand(composeRule, "handwriting", "pen_undo")
    composeRule.onNodeWithTag("reader_menu_command_pen_undo").performScrollTo()
    snap("04a-shape-creation-undo-disabled")
    command("handwriting", "select_tool"); collapse()
    composeRule.onNodeWithTag("pdf_page_viewport").performTouchInput { click(Offset(width * 0.5f, height * 0.45f)) }
    command("handwriting", "delete_ink"); collapse(); snap("05-shape-deleted")
    command("handwriting", "pen_undo"); collapse(); snap("06-shape-delete-undone")
    command("handwriting", "ink_rich_text")
    composeRule.onNodeWithTag("rich_editor_webview").assertExists()
    snap("07-rich-editor")
    composeRule.onNodeWithTag("rich_editor_cancel").performClick()
    command("file", "note_settings"); snap("08-project-paper"); back()
    command("file", "export_merged_pdf"); snap("09-merged-pdf-export"); back()
    command("settings", "settings_action"); snap("10-reader-settings")
    composeRule.onNodeWithTag("ocr_language_zh").performScrollTo()
    snap("11-ocr-models")
    composeRule.onNodeWithTag("asr_auto_transcribe_switch").performScrollTo()
    snap("12-audio-models")
    composeRule.onNodeWithTag("configure_agent_prompts").performScrollTo().performClick()
    composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("agent_prompt_text").fetchSemanticsNodes().isNotEmpty() }
    snap("13-prompts")
    composeRule.onNodeWithTag("agent_prompt_cancel").performClick()
    if (composeRule.onAllNodesWithTag("reader_settings_cancel").fetchSemanticsNodes().isNotEmpty())
      composeRule.onNodeWithTag("reader_settings_cancel").performClick()
    command("settings", "model_settings")
    composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("save_agent_model_settings").fetchSemanticsNodes().isNotEmpty() }
    snap("14-model-settings"); back()
    command("ai_annotations", "page_tutor"); snap("15-page-tutor"); back()
    command("reading", "search")
    composeRule.onNodeWithTag("search_input").performTextInput("pheromone")
    composeRule.onNodeWithTag("search_run").performClick()
    hideKeyboard()
    SystemClock.sleep(1000)
    snap("16-search"); composeRule.onNodeWithTag("search_close").performClick()
  }

  @Test fun captureEditingAndReview() {
    installAndShowShelf(); openReader()
    collapse()
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(10_000) {
      var found = false
      composeRule.runOnUiThread { found = context.window.decorView.findViewWithTag<View>("note_markdown_editor") != null }
      found
    }
    composeRule.runOnUiThread {
      editor().requestFocus()
      context.getSystemService(InputMethodManager::class.java).showSoftInput(editor(), InputMethodManager.SHOW_IMPLICIT)
    }
    SystemClock.sleep(700)
    snap("17-editing-keyboard")
    hideKeyboard()
    val selected = "信息素记录此前搜索的经验，距离启发让较近的城市更容易被选择。"
    composeRule.runOnUiThread { editor().setMarkdownSourceSelection(notes.indexOf(selected), notes.indexOf(selected) + selected.length) }
    command("word_editing", "ai_actions"); snap("18-selection-ai"); back()
    composeRule.runOnUiThread { editor().setMarkdownSourceSelection(notes.indexOf(selected), notes.indexOf(selected) + selected.length) }
    command("word_editing", "make_flashcard")
    composeRule.onNodeWithTag("flashcard_back_input").performTextInput("信息素共享搜索经验；距离启发偏向较近城市。")
    composeRule.onNodeWithTag("flashcard_tags_input").performTextInput("算法,复习")
    hideKeyboard(); snap("19-flashcard-create")
    composeRule.onNodeWithTag("flashcard_create_confirm").performClick()
    command("word_editing", "flashcards")
    snap("20-flashcard-library")
    composeRule.onNodeWithTag("flashcard_library_close").performClick()
    command("word_editing", "flashcard_review")
    composeRule.onNodeWithTag("flashcard_reveal").performClick()
    snap("21-flashcard-review")
    composeRule.onNodeWithTag("flashcard_grade_good").performClick()
    composeRule.onNodeWithTag("flashcard_review_close").performClick()
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    command("settings", "settings_action")
    composeRule.onNodeWithTag("night_filter_switch").performScrollTo().performClick()
    snap("22-night-settings")
    composeRule.onNodeWithTag("reader_settings_save").performClick()
    collapse(); snap("23-night-reading")
    runBlocking { readerSettingsStore(context).save(ReaderSettings(language = "zh-CN", theme = "light")) }
  }

  @Test fun captureFloatingWindows() {
    installAndShowShelf(); openReader()
    inst.uiAutomation.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow").close()
    SystemClock.sleep(500)
    val info = inst.uiAutomation.serviceInfo
    info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
    inst.uiAutomation.serviceInfo = info
    context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(
      ClipData.newPlainText("演示剪贴板", "请用一个简单例子说明信息素挥发。"))
    command("settings", "floating_windows"); collapse()
    composeRule.waitUntil(10_000) { FloatingWindowHub.phase.value == FloatingWindowHub.Phase.ACTIVE }
    snap("24-floating-ball")
    tapNativeText(context.getString(R.string.floating_ball_label))
    snap("25-floating-note")
    tapNativeText(context.getString(R.string.floating_tab_clipboard))
    snap("26-floating-clipboard")
    tapNativeText(context.getString(R.string.floating_tab_phrases))
    snap("27-floating-phrases")
    tapNativeText(context.getString(R.string.floating_tab_timer))
    tapNativeText(context.getString(R.string.floating_timer_start))
    snap("28-floating-timer")
    tapNativeText(context.getString(R.string.floating_close_action))
    composeRule.waitUntil(10_000) { FloatingWindowHub.phase.value == FloatingWindowHub.Phase.IDLE }
    snap("29-floating-closed")
  }

  private fun tapNativeText(text: String) {
    var match: AccessibilityNodeInfo? = null
    fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
      if (node == null) return null
      if (node.text?.toString() == text) return node
      for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
      return null
    }
    val until = SystemClock.uptimeMillis() + 8_000
    while (match == null && SystemClock.uptimeMillis() < until) {
      match = inst.uiAutomation.windows.firstNotNullOfOrNull { find(it.root) }
      if (match == null) SystemClock.sleep(100)
    }
    val rect = Rect(); requireNotNull(match) { "Native text missing: $text" }.getBoundsInScreen(rect)
    val now = SystemClock.uptimeMillis()
    val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, rect.exactCenterX(), rect.exactCenterY(), 0)
    val up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, rect.exactCenterX(), rect.exactCenterY(), 0)
    inst.uiAutomation.injectInputEvent(down, true); inst.uiAutomation.injectInputEvent(up, true)
    down.recycle(); up.recycle(); SystemClock.sleep(350)
  }

  private fun samplePdf(): ByteArray {
    val doc = PdfDocument()
    for (pageNo in 1..2) {
      val page = doc.startPage(PdfDocument.PageInfo.Builder(612, 792, pageNo).create())
      val canvas: Canvas = page.canvas
      val p = Paint(Paint.ANTI_ALIAS_FLAG)
      canvas.drawColor(Color.WHITE)
      p.color = Color.rgb(24, 90, 189); p.textSize = 13f
      canvas.drawText("PAGENOTE / ORIGINAL LEARNING SAMPLE", 44f, 54f, p)
      p.color = Color.rgb(30, 45, 65); p.textSize = 28f; p.isFakeBoldText = true
      canvas.drawText(if (pageNo == 1) "Ant Colony Optimization" else "Recall and explain", 44f, 108f, p)
      p.isFakeBoldText = false; p.textSize = 16f
      val lines = if (pageNo == 1) listOf(
        "How do local choices produce a good route?", "", "1. Start with several candidate routes.",
        "2. Prefer short edges with stronger pheromone.", "3. Compare the total route lengths.",
        "4. Evaporate old pheromone and reinforce good routes.", "", "Three ideas to connect:",
        "distance  /  shared experience  /  exploration")
      else listOf("Why does pheromone need to evaporate?", "", "Explain the trade-off in your own words.",
        "Then compare your answer with the page-linked note.")
      lines.forEachIndexed { i, line -> canvas.drawText(line, 44f, 155f + i * 31f, p) }
      p.color = Color.rgb(235, 243, 252); canvas.drawRect(44f, 555f, 568f, 700f, p)
      p.color = Color.rgb(24, 90, 189); p.textSize = 18f
      canvas.drawText("Try it on this page", 62f, 586f, p)
      p.color = Color.rgb(50, 70, 90); p.textSize = 15f
      canvas.drawText("Draw a shape, undo it, then restore it.", 62f, 620f, p)
      canvas.drawText("Use the note to create a review card.", 62f, 650f, p)
      p.textSize = 11f; canvas.drawText("Synthetic material. No account, private notes or paid AI output.", 44f, 750f, p)
      doc.finishPage(page)
    }
    return ByteArrayOutputStream().use { out -> doc.writeTo(out); doc.close(); out.toByteArray() }
  }
}
