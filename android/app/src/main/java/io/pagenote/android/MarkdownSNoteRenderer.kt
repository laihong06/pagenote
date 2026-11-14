package io.pagenote.android

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.noties.markwon.Markwon
import io.pagenote.feature.notes.MarkdownSBlockParser
import io.pagenote.feature.notes.MarkdownSStateEditor

internal class MarkdownSNoteRenderer(context: Context) : ScrollView(context) {
  private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
  private var boundMarkdown: String? = null
  private var boundRenderer: Markwon? = null
  private var currentMarkdown: String = ""
  private var onMarkdownChanged: (String, String) -> Unit = { _, _ -> }
  private var foregroundColor = android.graphics.Color.BLACK
  private var backgroundColorValue = android.graphics.Color.TRANSPARENT
  private var fontSize = 16f
  private var serif = false
  private var writable = false

  init {
    isFillViewport = true
    addView(content, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    tag = "markdown_s_native_reader"
  }

  fun setMarkdown(
    renderer: Markwon,
    markdown: String,
    textColor: Int,
    backgroundColor: Int,
    baseFontSizeSp: Float,
    serif: Boolean,
    writable: Boolean,
    displayLimit: Int = markdown.length,
    onMarkdownChanged: (before: String, after: String) -> Unit,
  ) {
    this.onMarkdownChanged = onMarkdownChanged
    val shouldRender = boundRenderer !== renderer || boundMarkdown != markdown ||
      foregroundColor != textColor || backgroundColorValue != backgroundColor || fontSize != baseFontSizeSp ||
      this.serif != serif || this.writable != writable
    if (!shouldRender) return
    val previousScrollY = scrollY
    boundRenderer = renderer
    boundMarkdown = markdown
    currentMarkdown = markdown
    foregroundColor = textColor
    backgroundColorValue = backgroundColor
    fontSize = baseFontSizeSp
    this.serif = serif
    this.writable = writable
    setBackgroundColor(backgroundColor)
    content.removeAllViews()
    val blocks = MarkdownSBlockParser.parse(markdown)
    var cursor = 0
    blocks.forEach { block ->
      if (block.fenceStart < cursor) return@forEach
      if (block.fenceStart >= displayLimit) return@forEach
      addMarkdownText(renderer, markdown.substring(cursor, block.fenceStart))
      val state = block.parseResult.state
      if (block.closed && state != null && state.kind == "genetic-lab") {
        val lab = MarkdownSGeneticLabView(context) { expected, next ->
          runCatching {
            val updated = MarkdownSStateEditor.replace(currentMarkdown, expected.id, expected, next)
            val before = currentMarkdown
            currentMarkdown = updated
            boundMarkdown = updated
            onMarkdownChanged(before, updated)
          }.onFailure { error ->
            // A stale or ambiguous block is intentionally not written; the parent can render it again.
            boundMarkdown = null
            announceForAccessibility(error.message ?: "组件状态未保存，请重新载入。")
          }
        }
        lab.bind(state, foregroundColor, backgroundColorValue, writable)
        content.addView(lab, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
          topMargin = dp(10)
          bottomMargin = dp(10)
        })
      } else {
        addMarkdownText(renderer, markdown.substring(block.fenceStart, minOf(block.fenceEnd, displayLimit)))
      }
      cursor = block.fenceEnd
    }
    if (cursor < displayLimit) addMarkdownText(renderer, markdown.substring(cursor, displayLimit.coerceAtMost(markdown.length)))
    if (previousScrollY > 0) {
      val observer = viewTreeObserver
      val restoreScroll = object : android.view.ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
          if (observer.isAlive) observer.removeOnPreDrawListener(this)
          val maximumScrollY = (content.height - height + paddingBottom).coerceAtLeast(0)
          scrollTo(scrollX, previousScrollY.coerceAtMost(maximumScrollY))
          return true
        }
      }
      observer.addOnPreDrawListener(restoreScroll)
      content.requestLayout()
    }
  }

  fun dispose() {
    clearRenderedContent()
  }

  override fun onDetachedFromWindow() {
    clearRenderedContent()
    super.onDetachedFromWindow()
  }

  private fun clearRenderedContent() {
    content.children().forEach { (it as? MarkdownSGeneticLabView)?.dispose() }
    content.removeAllViews()
    boundMarkdown = null
    boundRenderer = null
  }

  private fun addMarkdownText(renderer: Markwon, markdown: String) {
    if (markdown.isEmpty()) return
    val text = TextView(context).apply {
      textSize = fontSize
      setTextColor(foregroundColor)
      setBackgroundColor(backgroundColorValue)
      typeface = if (serif) Typeface.SERIF else Typeface.DEFAULT
      setPadding(dp(12), dp(8), dp(12), dp(8))
      gravity = android.view.Gravity.TOP or android.view.Gravity.START
    }
    renderer.setMarkdown(text, NotePreviewRewrite.forPreview(markdown))
    content.addView(text, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

  private fun LinearLayout.children(): List<android.view.View> = (0 until childCount).map(::getChildAt)
}
