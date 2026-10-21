package io.pagenote.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import io.noties.markwon.Markwon
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.RenderProps
import io.noties.markwon.SpanFactory
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.html.tag.UnderlineHandler
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import org.commonmark.node.Heading
import androidx.compose.ui.graphics.toArgb

internal object NoteMarkdownRenderer {
  fun createEditor(
    context: Context,
    sourceDirectoryUri: Uri?,
    textColor: Int = Color.BLACK,
    palette: MarkdownContentPalette? = null,
  ): MarkwonEditor =
    MarkwonEditor.builder(base(context, sourceDirectoryUri, textColor, palette = palette).build())
      .useEditHandler(UnderlineEditHandler())
      .build()

  fun createPreview(
    context: Context,
    sourceDirectoryUri: Uri?,
    textColor: Int,
    mermaidCoordinator: MermaidRenderCoordinator? = null,
    baseFontSizeSp: Float = 16f,
    palette: MarkdownContentPalette? = null,
  ): Markwon {
    val displayMetrics = context.resources.displayMetrics
    val latexTextSize = TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_SP,
      baseFontSizeSp,
      displayMetrics,
    )
    val theme = if (Color.luminance(textColor) >= 0.5f) {
      Prism4jThemeDarkula.create()
    } else {
      Prism4jThemeDefault.create()
    }
    val builder = base(context, sourceDirectoryUri, textColor, mermaidCoordinator)
      .usePlugin(MarkwonInlineParserPlugin.create())
      .usePlugin(
        JLatexMathPlugin.create(
          latexTextSize,
          JLatexMathPlugin.BuilderConfigure { builder ->
            builder.inlinesEnabled(true)
            builder.theme().textColor(textColor)
            builder.errorHandler { latex, _ -> FormulaFallbackDrawable(context, latex, textColor) }
          },
        ),
      )
      .usePlugin(SyntaxHighlightPlugin.create(Prism4j(GrammarLocatorDef()), theme))
    if (palette != null) builder.usePlugin(palettePlugin(palette))
    return builder.build()
  }

  private fun base(
    context: Context,
    sourceDirectoryUri: Uri?,
    textColor: Int,
    mermaidCoordinator: MermaidRenderCoordinator? = null,
    palette: MarkdownContentPalette? = null,
  ): Markwon.Builder {
    val builder = Markwon.builder(context)
      .usePlugin(TablePlugin.create(context))
      .usePlugin(StrikethroughPlugin.create())
      .usePlugin(TaskListPlugin.create(context))
      .usePlugin(
        HtmlPlugin.create { plugin ->
          plugin.excludeDefaults(true).addHandler(UnderlineHandler())
        },
      )
      .usePlugin(
        ImagesPlugin.create()
          .addSchemeHandler(
            NoteAssetSchemeHandler(context, context.contentResolver, sourceDirectoryUri, palette?.text?.toArgb() ?: textColor),
          )
          .apply {
            if (mermaidCoordinator != null) {
              addSchemeHandler(
                MermaidImageSchemeHandler(context, mermaidCoordinator, palette?.text?.toArgb() ?: textColor),
              )
              addMediaDecoder(MermaidSvgMediaDecoder(context) { mermaidCoordinator.renderWidthPx - 24 })
            }
          }
          .addSchemeHandler(OfflineRemoteImageSchemeHandler(context, palette?.text?.toArgb() ?: textColor))
          .placeholderProvider { drawable ->
            if (Uri.parse(drawable.destination).scheme == MermaidSourceCodec.SCHEME) {
              NoteImagePlaceholderDrawable(
                context,
                context.getString(R.string.note_rich_mermaid_loading),
                palette?.text?.toArgb() ?: textColor,
              )
            } else {
              null
            }
          }
          .defaultMediaDecoder(NoteImageMediaDecoder(context, textColor))
          .errorHandler { destination, _ ->
            NoteImagePlaceholderDrawable(
              context,
              context.getString(R.string.note_rich_image_missing, noteImageSourceSummary(destination)),
              palette?.text?.toArgb() ?: textColor,
            )
          },
      )
    if (palette != null) builder.usePlugin(palettePlugin(palette))
    return builder
  }

  private fun palettePlugin(palette: MarkdownContentPalette) = object : AbstractMarkwonPlugin() {
    override fun configureTheme(builder: MarkwonTheme.Builder) {
      builder
        .linkColor(palette.link.toArgb())
        .blockQuoteColor(palette.rule.toArgb())
        .headingBreakColor(palette.rule.toArgb())
        .thematicBreakColor(palette.rule.toArgb())
        .codeTextColor(palette.codeText.toArgb())
        .codeBlockTextColor(palette.codeText.toArgb())
        .codeBackgroundColor(palette.codeBackground.toArgb())
        .codeBlockBackgroundColor(palette.codeBackground.toArgb())
      if (palette.serif) builder.headingTypeface(Typeface.SERIF)
    }

    override fun configureSpansFactory(builder: io.noties.markwon.MarkwonSpansFactory.Builder) {
      builder.appendFactory(
        Heading::class.java,
        SpanFactory { _: MarkwonConfiguration, _: RenderProps -> ForegroundColorSpan(palette.heading.toArgb()) },
      )
    }
  }
}

internal class FormulaFallbackDrawable(
  context: Context,
  val source: String,
  textColor: Int,
) : Drawable() {
  private val padding = (8f * context.resources.displayMetrics.density).toInt()
  private val radius = 4f * context.resources.displayMetrics.density
  private val text = context.getString(R.string.note_rich_formula_error, source)
  private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    color = textColor
    textSize = TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_SP,
      14f,
      context.resources.displayMetrics,
    )
  }
  private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = if (Color.luminance(textColor) >= 0.5f) Color.rgb(78, 34, 34) else Color.rgb(255, 235, 238)
  }
  private val layout = StaticLayout.Builder
    .obtain(text, 0, text.length, textPaint, (320f * context.resources.displayMetrics.density).toInt())
    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
    .setIncludePad(true)
    .build()

  override fun getIntrinsicWidth(): Int {
    var width = 0f
    for (line in 0 until layout.lineCount) width = maxOf(width, layout.getLineWidth(line))
    return width.toInt() + padding * 2
  }

  override fun getIntrinsicHeight(): Int = layout.height + padding * 2

  override fun draw(canvas: Canvas) {
    val bounds = bounds
    canvas.drawRoundRect(RectF(bounds), radius, radius, backgroundPaint)
    canvas.save()
    canvas.translate((bounds.left + padding).toFloat(), (bounds.top + padding).toFloat())
    layout.draw(canvas)
    canvas.restore()
  }

  override fun setAlpha(alpha: Int) {
    textPaint.alpha = alpha
    backgroundPaint.alpha = alpha
    invalidateSelf()
  }

  override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
    textPaint.colorFilter = colorFilter
    backgroundPaint.colorFilter = colorFilter
    invalidateSelf()
  }

  @Deprecated("Deprecated in Android")
  override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
