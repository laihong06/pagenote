package io.pagenote.android

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** In-window palette: only its handle intercepts drag events. */
@Composable
internal fun FloatingPenPalette(content: @Composable () -> Unit) {
  BoxWithConstraints(Modifier.fillMaxSize().testTag("floating_pen_host")) {
    val maxPaletteContentHeight = minOf(360.dp, (maxHeight - 100.dp).coerceAtLeast(120.dp))
    var expanded by rememberSaveable { mutableStateOf(false) }
    var x by rememberSaveable { mutableFloatStateOf(8f) }
    var y by rememberSaveable { mutableFloatStateOf(24f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var placed by rememberSaveable { mutableStateOf(false) }
    val maxX = (constraints.maxWidth - size.width).coerceAtLeast(0).toFloat()
    val maxY = (constraints.maxHeight - size.height).coerceAtLeast(0).toFloat()
    LaunchedEffect(maxX, maxY, size) {
      x = x.coerceIn(0f, maxX)
      y = if (!placed && size != IntSize.Zero) maxY * 0.8f else y.coerceIn(0f, maxY)
      if (size != IntSize.Zero) placed = true
    }
    Surface(
      modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
        .onSizeChanged { size = it }.testTag("floating_pen_palette"),
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = 6.dp,
      tonalElevation = 3.dp,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
          Modifier.width(48.dp).height(48.dp).testTag("floating_pen_handle")
            .pointerInput(maxX, maxY) {
              detectDragGestures(
                onDragEnd = { x = if (x < maxX / 2f) 0f else maxX },
              ) { change, amount ->
                change.consume()
                x = (x + amount.x).coerceIn(0f, maxX)
                y = (y + amount.y).coerceIn(0f, maxY)
              }
            },
          contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.DragHandle, stringResource(R.string.floating_pen_drag)) }
        IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp).testTag("floating_pen_toggle")) {
          Icon(if (expanded) Icons.Outlined.Close else Icons.Outlined.Edit,
            stringResource(if (expanded) R.string.floating_pen_collapse else R.string.floating_pen_expand))
        }
        if (expanded) {
          Box(Modifier.heightIn(max = maxPaletteContentHeight)) { content() }
        }
      }
    }
  }
}

@Composable
internal fun FloatingReaderPenTools(
  tool: io.pagenote.feature.pen.PenCanvasTool,
  drawTool: PdfAnnotationDrawTool?,
  style: io.pagenote.feature.pen.PenStyle,
  enabled: Boolean,
  selectionEnabled: Boolean,
  onTool: (io.pagenote.feature.pen.PenCanvasTool) -> Unit,
  onShape: (PdfAnnotationDrawTool) -> Unit,
  onColor: (String) -> Unit,
  onWidth: (Double) -> Unit,
  onCustomColor: () -> Unit,
) {
  FloatingPenPalette {
    ReaderPenRail(tool, drawTool, style, enabled, selectionEnabled, onTool, onShape, onColor, onWidth, onCustomColor)
  }
}
