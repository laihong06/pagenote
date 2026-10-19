package io.pagenote.android

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.Markwon

internal class MermaidPreviewHost(
  context: Context,
  coordinator: MermaidRenderCoordinator,
  textColor: Int,
) : FrameLayout(context) {
  private var boundRenderer: Markwon? = null
  private var boundMarkdown: String? = null
  private val mermaidLayoutObserver = object : ViewTreeObserver.OnPreDrawListener {
    override fun onPreDraw(): Boolean {
      val text = markdownView.text as? Spanned ?: return true
      val layout = markdownView.layout ?: return true
      val needsRemeasure = text.getSpans(0, text.length, AsyncDrawableSpan::class.java).any { span ->
        if (!span.drawable.destination.startsWith("${MermaidSourceCodec.SCHEME}:")) return@any false
        val height = span.drawable.bounds.height()
        val start = text.getSpanStart(span)
        if (height <= 0 || start < 0) return@any false
        val line = layout.getLineForOffset(start)
        layout.getLineBottom(line) - layout.getLineTop(line) < height
      }
      if (!needsRemeasure) return true

      markdownView.setText(SpannableStringBuilder(text))
      markdownView.requestLayout()
      return false
    }
  }

  val markdownView = TextView(context).apply {
    textSize = 16f
    gravity = Gravity.TOP or Gravity.START
    setTextColor(textColor)
    setPadding(12, 10, 12, 10)
  }

  init {
    addView(
      markdownView,
      LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
    )
    coordinator.attachTo(this)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    viewTreeObserver.addOnPreDrawListener(mermaidLayoutObserver)
  }

  override fun onDetachedFromWindow() {
    if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(mermaidLayoutObserver)
    super.onDetachedFromWindow()
  }

  fun setMarkdown(renderer: Markwon, markdown: String, textColor: Int, baseFontSizeSp: Float = 16f) {
    markdownView.setTextColor(textColor)
    markdownView.textSize = baseFontSizeSp
    if (boundRenderer !== renderer || boundMarkdown != markdown) {
      boundRenderer = renderer
      boundMarkdown = markdown
      renderer.setMarkdown(markdownView, markdown)
    }
  }
}
