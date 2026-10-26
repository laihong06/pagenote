package io.pagenote.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenStyle

internal val READER_PEN_RAIL_WIDTH = 44.dp

@Composable
internal fun ReaderPenRail(
  tool: PenCanvasTool,
  drawTool: PdfAnnotationDrawTool?,
  style: PenStyle,
  enabled: Boolean,
  selectionEnabled: Boolean,
  onTool: (PenCanvasTool) -> Unit,
  onShape: (PdfAnnotationDrawTool) -> Unit,
  onColor: (String) -> Unit,
  onWidth: (Double) -> Unit,
  onCustomColor: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var shapeMenu by remember { mutableStateOf(false) }
  var styleMenu by remember { mutableStateOf(false) }
  val ink = Color(android.graphics.Color.parseColor(style.color))
  Column(
    modifier.width(READER_PEN_RAIL_WIDTH).fillMaxHeight()
      .background(Color(0xFF1B1E22)).verticalScroll(rememberScrollState())
      .padding(vertical = 6.dp).testTag("reader_pen_rail"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    listOf(
      PenCanvasTool.PEN to R.string.pen,
      PenCanvasTool.BALLPOINT to R.string.pen_ballpoint,
      PenCanvasTool.HIGHLIGHTER to R.string.highlighter,
      PenCanvasTool.LASER to R.string.pen_laser,
      PenCanvasTool.ERASER to R.string.eraser,
    ).forEach { (choice, label) ->
      val name = stringResource(label)
      val active = tool == choice && drawTool == null
      RailAction(
        name, "reader_pen_${choice.name.lowercase()}", active, enabled,
        onClick = { if (active) styleMenu = !styleMenu else { styleMenu = false; onTool(choice) } },
      ) { PenIllustration(choice, ink, active) }
    }
    RailDivider()
    RailIcon(Icons.Outlined.NearMe, stringResource(R.string.reader_menu_select_tool),
      "reader_pen_select", tool == PenCanvasTool.SELECT && drawTool == null,
      selectionEnabled, { onTool(PenCanvasTool.SELECT) })
    Box {
      RailIcon(Icons.Outlined.Category, stringResource(R.string.reader_menu_shape),
        "reader_pen_shapes", drawTool != null, enabled, { shapeMenu = !shapeMenu })
      DropdownMenu(expanded = shapeMenu, onDismissRequest = { shapeMenu = false }) {
        listOf(
          Triple(PdfAnnotationDrawTool.LINE, Icons.Outlined.Remove, R.string.reader_menu_line),
          Triple(PdfAnnotationDrawTool.ARROW, Icons.Outlined.NorthEast, R.string.reader_menu_arrow),
          Triple(PdfAnnotationDrawTool.RECTANGLE, Icons.Outlined.CropSquare, R.string.annotation_shape_rectangle),
          Triple(PdfAnnotationDrawTool.ELLIPSE, Icons.Outlined.Circle, R.string.annotation_shape_ellipse),
        ).forEach { (shape, icon, label) ->
          DropdownMenuItem(
            text = { Text(stringResource(label)) },
            leadingIcon = { Icon(icon, null) },
            modifier = Modifier.testTag("reader_pen_shape_${shape.name.lowercase()}"),
            onClick = { shapeMenu = false; onShape(shape) },
          )
        }
      }
    }
    Box {
      RailIcon(Icons.Outlined.Tune, stringResource(R.string.pen_style_title),
        "reader_pen_style", styleMenu, enabled, { styleMenu = !styleMenu })
      DropdownMenu(
        expanded = styleMenu,
        onDismissRequest = { styleMenu = false },
        modifier = Modifier.width(252.dp).testTag("reader_pen_style_menu"),
      ) {
        Text(stringResource(R.string.reader_menu_width), Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          PenStyle.WIDTH_OPTIONS.forEach { width ->
            TextButton(
              modifier = Modifier.weight(1f).testTag("reader_pen_width_$width"),
              contentPadding = PaddingValues(4.dp),
              onClick = { onWidth(width) },
              colors = ButtonDefaults.textButtonColors(
                containerColor = if (style.width == width) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
              ),
            ) { Text(width.toString()) }
          }
        }
        Text(stringResource(R.string.reader_menu_color), Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
        PenStyle.COLOR_SWATCHES.chunked(4).forEach { row ->
          Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            row.forEach { (name, color) ->
              ColorSwatch(color, name, style.color == color, enabled, onColor)
            }
          }
        }
        DropdownMenuItem(
          text = { Text(stringResource(R.string.pen_custom_color)) },
          leadingIcon = { Icon(Icons.Outlined.Add, null) },
          onClick = { styleMenu = false; onCustomColor() },
        )
      }
    }
    RailDivider()
    PenStyle.WIDTH_OPTIONS.take(3).forEach { width ->
      val label = stringResource(R.string.pen_width_value, width.toString())
      RailAction(label, "reader_pen_preset_$width", style.width == width, enabled, 28, { onWidth(width) }) {
        Canvas(Modifier.size(20.dp)) {
          drawLine(Color.White, Offset(2.dp.toPx(), size.height / 2), Offset(size.width - 2.dp.toPx(), size.height / 2), width.toFloat() * 0.8.dp.toPx())
        }
      }
    }
    RailDivider()
    listOf("#212121", "#185abd", "#dc3545", "#2e7d32", "#f9a825").forEach { color ->
      val name = PenStyle.COLOR_SWATCHES.first { it.second == color }.first
      ColorSwatch(color, name, style.color == color, enabled, onColor)
    }
    RailIcon(Icons.Outlined.Add, stringResource(R.string.pen_custom_color), "reader_pen_custom_color", false, enabled, onCustomColor)
  }
}

@Composable
private fun ColorSwatch(color: String, name: String, active: Boolean, enabled: Boolean, onColor: (String) -> Unit) {
  val label = stringResource(R.string.pen_color_value, name)
  Box(
    Modifier.size(36.dp, 30.dp).clickable(enabled) { onColor(color) }
      .semantics { contentDescription = label; selected = active }
      .testTag("reader_pen_color_${color.drop(1)}"),
    contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.size(20.dp).background(Color(android.graphics.Color.parseColor(color)), RoundedCornerShape(4.dp))
      .border(if (active) 2.dp else 1.dp, if (active) Color.White else Color.Gray, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
      if (active) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
    }
  }
}

@Composable
private fun RailIcon(icon: ImageVector, label: String, tag: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
  RailAction(label, tag, active, enabled, onClick = onClick) {
    Icon(icon, null, tint = if (enabled) Color(0xFFD9DFE0) else Color.Gray, modifier = Modifier.size(21.dp))
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RailAction(label: String, tag: String, active: Boolean, enabled: Boolean, height: Int = 40, onClick: () -> Unit, content: @Composable () -> Unit) {
  TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
    Box(
      Modifier.width(READER_PEN_RAIL_WIDTH).height(height.dp)
        .background(if (active) Color(0xFF353E44) else Color.Transparent)
        .clickable(enabled = enabled, onClick = onClick)
        .semantics { contentDescription = label; selected = active }.testTag(tag),
      contentAlignment = Alignment.Center,
    ) {
      content()
      if (active) Box(Modifier.align(Alignment.CenterStart).width(2.dp).fillMaxHeight().background(Color(0xFF63C9D3)))
    }
  }
}

@Composable
private fun RailDivider() = HorizontalDivider(Modifier.padding(horizontal = 6.dp, vertical = 5.dp), color = Color(0xFF42484B))

@Composable
private fun PenIllustration(tool: PenCanvasTool, ink: Color, active: Boolean) {
  Canvas(Modifier.fillMaxSize()) {
    withTransform({ scale(size.width / 44f, size.height / 40f, Offset.Zero); translate(if (active) 0f else -5f, 0f) }) {
      val slim = tool == PenCanvasTool.BALLPOINT
      val top = if (slim) 15f else 13f
      val height = if (slim) 10f else 14f
      drawRect(Brush.verticalGradient(listOf(Color(0xFFB0B7BE), Color(0xFF3A424C), Color(0xFF747D86)), top, top + height), Offset(-8f, top), Size(31f, height))
      drawRect(Color(0xFF303B44), Offset(23f, top), Size(10f, height))
      drawRect(if (tool == PenCanvasTool.HIGHLIGHTER) Color(0xFFB3ED6C) else ink, Offset(22f, top), Size(3f, height))
      when (tool) {
        PenCanvasTool.ERASER -> drawRoundRect(Color(0xFFDF9AA3), Offset(31f, top), Size(12f, height), CornerRadius(4f))
        PenCanvasTool.LASER -> drawRoundRect(Color(0xFFED5666), Offset(32f, 16f), Size(9f, 8f), CornerRadius(3f))
        PenCanvasTool.HIGHLIGHTER -> drawPath(Path().apply { moveTo(33f, 12f); lineTo(43f, 14f); lineTo(40f, 28f); lineTo(33f, 28f); close() }, Color(0xFFB3ED6C))
        else -> {
          drawPath(Path().apply { moveTo(33f, top); lineTo(44f, 20f); lineTo(33f, top + height); close() }, Color(0xFFD4E1E9))
          if (!slim) drawLine(Color(0xFF566D7E), Offset(34f, 20f), Offset(43f, 20f), 1f)
        }
      }
    }
  }
}
