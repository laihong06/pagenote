package io.pagenote.android

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import io.pagenote.feature.audio.AudioCapsule
import io.pagenote.feature.audio.AudioCapsuleGeometry
import io.pagenote.feature.audio.AudioPlaybackPolicy
import kotlin.math.min

/**
 * 画布音频胶囊 overlay（移植批次 8）：按 width-1000 文档坐标绘制当前页的
 * 录音胶囊（坐标契约与 [PdfAnnotationOverlay] 一致）。绘制-only，点击命中
 * 由视口手势分支经 [AudioCapsuleGeometry.hitTest] 路由（避免拦截笔输入）。
 */
@Composable
internal fun AudioCapsuleOverlay(
  capsules: List<AudioCapsule>,
  activeCapsuleId: String?,
  modifier: Modifier = Modifier,
) {
  if (capsules.isEmpty()) return
  Canvas(modifier.fillMaxSize().testTag("audio_capsule_overlay")) {
    val pageWidthScale = size.width / 1000f
    // 从旧到新绘制（后放的在上层，与 hitTest 的"取最新"一致）。
    capsules.sortedBy(AudioCapsule::createdAtMillis).forEach { capsule ->
      val rect = AudioCapsuleGeometry.rect(capsule)
      val left = (rect[0] * pageWidthScale).toFloat()
      val top = (rect[1] * pageWidthScale).toFloat()
      val width = ((rect[2] - rect[0]) * pageWidthScale).toFloat()
      val height = ((rect[3] - rect[1]) * pageWidthScale).toFloat()
      if (width <= 0f || height <= 0f) return@forEach
      val isActive = capsule.id == activeCapsuleId
      val hasTranscript = capsule.hasTranscript
      val fill = when {
        isActive -> Color(0xFF245E59).copy(alpha = 0.92f)
        hasTranscript -> Color(0xFFC8E8E1).copy(alpha = 0.95f)
        else -> Color(0xFFFFF4D6).copy(alpha = 0.95f)
      }
      val border = when {
        isActive -> Color(0xFF073731)
        hasTranscript -> Color(0xFF245E59)
        else -> Color(0xFFC05040)
      }
      val corner = min(width, height) / 2f
      drawRoundRect(
        color = fill,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(corner, corner),
      )
      drawRoundRect(
        color = border,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(corner, corner),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
      )
      // 麦克风标记：圆头 + 短杆 + 底座（近似 touchnotes 胶囊图标语义）。
      val cx = left + height * 0.5f
      val headR = height * 0.16f
      val headCy = top + height * 0.34f
      drawCircle(border, radius = headR, center = Offset(cx, headCy))
      drawRoundRect(
        color = border,
        topLeft = Offset(cx - headR * 0.5f, headCy + headR),
        size = Size(headR, height * 0.22f),
        cornerRadius = CornerRadius(headR * 0.5f, headR * 0.5f),
      )
      drawLine(
        color = border,
        start = Offset(cx, headCy + headR + height * 0.22f + 2f),
        end = Offset(cx, top + height * 0.78f),
        strokeWidth = 1.5f,
      )
      // 时长标签（右侧留白区）。
      val text = AudioPlaybackPolicy.clockLabel(capsule.durationMs)
      val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isActive) android.graphics.Color.WHITE else border.toArgbColor()
        textSize = height * 0.34f
        textAlign = Paint.Align.LEFT
        isFakeBoldText = isActive
      }
      drawContext.canvas.nativeCanvas.drawText(
        text,
        cx + headR + height * 0.12f,
        top + height * 0.62f,
        textPaint,
      )
    }
  }
}

private fun Color.toArgbColor(): Int = toArgb()
