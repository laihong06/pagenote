package io.pagenote.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenStyle

internal class ReaderHomeFloatingPenBoxCallbacks(
  val onTool: (PenCanvasTool) -> Unit,
  val onShape: (PdfAnnotationDrawTool) -> Unit,
  val onColor: (String) -> Unit,
  val onWidth: (Double) -> Unit,
  val onCustomColor: () -> Unit,
  val onRestoreStyle: (String, Double) -> Unit,
)

@Composable
internal fun ReaderHomeFloatingPenBox(
  tool: PenCanvasTool,
  drawTool: PdfAnnotationDrawTool?,
  style: PenStyle,
  enabled: Boolean,
  selectionEnabled: Boolean,
  callbacks: ReaderHomeFloatingPenBoxCallbacks,
  modifier: Modifier = Modifier,
) {
  FloatingPenBox(
    tool = tool,
    drawTool = drawTool,
    style = style,
    enabled = enabled,
    selectionEnabled = selectionEnabled,
    onTool = callbacks.onTool,
    onShape = callbacks.onShape,
    onColor = callbacks.onColor,
    onWidth = callbacks.onWidth,
    onCustomColor = callbacks.onCustomColor,
    onRestoreStyle = callbacks.onRestoreStyle,
    modifier = modifier,
  )
}
