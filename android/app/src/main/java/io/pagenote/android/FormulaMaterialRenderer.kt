package io.pagenote.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * 公式素材渲染数据层（移植批次 5）。复用应用既有的 JLatexMath 依赖
 * （ru.noties:jlatexmath-android，经 markwon ext-latex 引入，NoteMarkdownRenderer
 * 同源）把 LaTeX 行渲染为位图——对应 touchnotes `FormulaMaterial`（公式在画布上
 * 是一张按 latexTexts 逐行堆叠的位图）。
 *
 * 流水线：JLatexMathDrawable 测量每行内在尺寸 → [FormulaMaterialLayout]（touchnotes
 * 布局语义）→ ARGB_8888 位图逐行 draw。渲染失败（非法 LaTeX 等）该行回退
 * [fallback] 位图（对齐 NoteMarkdownRenderer 的 FormulaFallbackDrawable 姿态），
 * 不使整个素材失败。
 *
 * 位图后续（存为项目附件并落 image 项 + latex 扩展字段，见 FormulaAnnotation）
 * 由画布接线批次完成；本类只负责 LaTeX → Bitmap。
 */
internal class FormulaMaterialRenderer {
  /**
   * 渲染 [latexLines]。[textSizePx] 为 JLatexMath 文字号（px）；
   * [textColor] 为 ARGB 颜色；[targetHeight] ≤0 时不整体缩放（见
   * [FormulaMaterialLayout.layout]）。全部行渲染失败返回 null。
   */
  fun render(
    latexLines: List<String>,
    textSizePx: Float,
    textColor: Int,
    targetHeight: Int = -1,
    fallback: (latex: String, textColor: Int) -> Bitmap? = { _, _ -> null },
  ): Bitmap? {
    if (latexLines.isEmpty() || latexLines.size > FormulaMaterialLayout.MAX_LINES) return null
    if (!textSizePx.isFinite() || textSizePx <= 0f) return null

    val lines = mutableListOf<Pair<String, JLatexMathDrawable?>>()
    latexLines.forEach { latex ->
      val drawable = runCatching {
        JLatexMathDrawable.builder(latex)
          .textSize(textSizePx)
          .color(textColor)
          .align(JLatexMathDrawable.ALIGN_CENTER)
          .background(Color.TRANSPARENT)
          .build()
      }.getOrNull()
      if (drawable == null || drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
        lines.add(latex to null)
      } else {
        lines.add(latex to drawable)
      }
    }
    val sizes = lines.map { (latex, drawable) ->
      drawable?.let { it.intrinsicWidth to it.intrinsicHeight }
        ?: fallbackSize(latex, textColor, fallback)
    }
    val block = FormulaMaterialLayout.layout(sizes, targetHeight) ?: return null

    val bitmap = Bitmap.createBitmap(block.width.coerceAtLeast(1), block.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.SRC)
    var drewAny = false
    block.lines.forEachIndexed { index, lineLayout ->
      val (latex, drawable) = lines[index]
      val left = lineLayout.x
      val top = lineLayout.y
      val width = lineLayout.width
      val height = lineLayout.height
      if (drawable != null) {
        drawable.setBounds(left, top, left + width, top + height)
        drawable.draw(canvas)
        drewAny = true
      } else {
        val fallbackBitmap = fallback(latex, textColor)
        if (fallbackBitmap != null && !fallbackBitmap.isRecycled) {
          canvas.drawBitmap(fallbackBitmap, null, android.graphics.RectF(left.toFloat(), top.toFloat(), (left + width).toFloat(), (top + height).toFloat()), FALLBACK_PAINT)
          drewAny = true
        }
      }
    }
    if (!drewAny) {
      bitmap.recycle()
      return null
    }
    return bitmap
  }

  private fun fallbackSize(
    latex: String,
    textColor: Int,
    fallback: (String, Int) -> Bitmap?,
  ): Pair<Int, Int> {
    val bitmap = fallback(latex, textColor)
    return if (bitmap != null && !bitmap.isRecycled) bitmap.width to bitmap.height else 1 to 1
  }

  private companion object {
    val FALLBACK_PAINT = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
  }
}
