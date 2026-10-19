package io.pagenote.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import kotlin.math.ceil
import kotlin.math.max

internal class NoteImagePlaceholderDrawable(
  context: Context,
  val message: String,
  textColor: Int,
) : Drawable() {
  private val density = context.resources.displayMetrics.density
  private val padding = dp(8)
  private val maxWidth = dp(280)
  private val text = if (message.length > MAX_MESSAGE_LENGTH) {
    message.take(MAX_MESSAGE_LENGTH - 3) + "..."
  } else {
    message
  }
  private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    color = if (Color.luminance(textColor) < 0.5f) Color.rgb(82, 58, 12) else Color.rgb(255, 231, 170)
    textSize = TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_SP,
      13f,
      context.resources.displayMetrics,
    )
  }
  private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = if (Color.luminance(textColor) < 0.5f) Color.rgb(255, 246, 220) else Color.rgb(72, 57, 27)
  }
  private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = if (Color.luminance(textColor) < 0.5f) Color.rgb(225, 188, 111) else Color.rgb(145, 116, 57)
    style = Paint.Style.STROKE
    strokeWidth = dp(1).toFloat()
  }
  private val radius = dp(4).toFloat()
  private val layout = StaticLayout.Builder
    .obtain(text, 0, text.length, textPaint, maxWidth - padding * 2)
    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
    .setIncludePad(true)
    .build()
  private val contentWidth = (0 until layout.lineCount)
    .maxOfOrNull { ceil(layout.getLineWidth(it).toDouble()).toInt() }
    ?: 0

  override fun getIntrinsicWidth(): Int = max(dp(120), contentWidth + padding * 2)

  override fun getIntrinsicHeight(): Int = layout.height + padding * 2

  override fun draw(canvas: Canvas) {
    val bounds = bounds
    val rect = RectF(bounds)
    canvas.drawRoundRect(rect, radius, radius, backgroundPaint)
    canvas.drawRoundRect(rect, radius, radius, borderPaint)
    canvas.save()
    canvas.translate((bounds.left + padding).toFloat(), (bounds.top + padding).toFloat())
    layout.draw(canvas)
    canvas.restore()
  }

  override fun setAlpha(alpha: Int) {
    textPaint.alpha = alpha
    backgroundPaint.alpha = alpha
    borderPaint.alpha = alpha
    invalidateSelf()
  }

  override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
    textPaint.colorFilter = colorFilter
    backgroundPaint.colorFilter = colorFilter
    borderPaint.colorFilter = colorFilter
    invalidateSelf()
  }

  @Deprecated("Deprecated in Android")
  override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

  private fun dp(value: Int): Int = (value * density).toInt()

  private companion object {
    const val MAX_MESSAGE_LENGTH = 256
  }
}
