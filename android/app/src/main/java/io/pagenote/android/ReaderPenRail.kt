package io.pagenote.android

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
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
import kotlin.math.roundToInt

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
  var expanded by rememberSaveable { mutableStateOf(true) }
  var shapeMenu by remember { mutableStateOf(false) }
  var styleMenu by remember { mutableStateOf(false) }
  var penBoxMenu by remember { mutableStateOf(false) }
  val ink = Color(android.graphics.Color.parseColor(style.color))
  Column(
    modifier.width(if (expanded) READER_PEN_RAIL_WIDTH else 32.dp).fillMaxHeight()
      .background(MaterialTheme.colorScheme.surfaceContainerLow).verticalScroll(rememberScrollState())
      .padding(vertical = 6.dp).testTag("reader_pen_rail"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    IconButton(onClick = { expanded = !expanded; shapeMenu = false; styleMenu = false; penBoxMenu = false },
      modifier = Modifier.size(32.dp).testTag(if (expanded) "reader_pen_collapse" else "reader_pen_expand")) {
      Icon(if (expanded) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight,
        stringResource(if (expanded) R.string.reader_pen_collapse_action else R.string.reader_pen_expand_action),
        tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (expanded) {
    Box {
      RailIcon(Icons.Outlined.Inventory2, stringResource(R.string.reader_pen_box_title),
        "reader_pen_box", penBoxMenu, enabled, { penBoxMenu = true })
      DropdownMenu(expanded = penBoxMenu, onDismissRequest = { penBoxMenu = false },
        modifier = Modifier.width(280.dp).testTag("reader_pen_box_panel")) {
        Text(stringResource(R.string.reader_pen_box_title), Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.reader_pen_box_tools), Modifier.padding(horizontal = 14.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
        listOf(
          listOf(PenCanvasTool.PEN to R.string.pen, PenCanvasTool.BALLPOINT to R.string.pen_ballpoint),
          listOf(PenCanvasTool.HIGHLIGHTER to R.string.highlighter, PenCanvasTool.ERASER to R.string.eraser),
        ).forEach { choices ->
          Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            choices.forEach { (choice, label) ->
              TextButton(onClick = { onTool(choice); penBoxMenu = false },
                modifier = Modifier.weight(1f).testTag("reader_pen_box_${choice.name.lowercase()}")
                  .semantics { selected = tool == choice && drawTool == null },
                colors = ButtonDefaults.textButtonColors(containerColor = if (tool == choice && drawTool == null) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                enabled = enabled) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Box(Modifier.size(44.dp, 32.dp)) { PenIllustration(choice, ink, tool == choice && drawTool == null) }
                  Text(stringResource(label), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }
        }
        PenWidthControl(style.width, enabled, "reader_pen_box_width_slider", onWidth)
        Text(stringResource(R.string.reader_menu_color), Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
        PenStyle.COLOR_SWATCHES.chunked(4).forEach { row ->
          Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            row.forEach { (name, color) ->
              Box(Modifier.size(44.dp).clickable(enabled = enabled) { onColor(color); penBoxMenu = false }
                .testTag("reader_pen_box_color_${color.drop(1)}")
                .semantics { contentDescription = name; selected = style.color == color }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(24.dp).background(Color(android.graphics.Color.parseColor(color)), RoundedCornerShape(4.dp))
                  .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                  if (style.color == color) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
              }
            }
          }
        }
      }
    }
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
        onLongClick = { if (!active) onTool(choice); styleMenu = true },
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
        PenWidthControl(style.width, enabled, "reader_pen_style_width_slider", onWidth)
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
    val lineColor = MaterialTheme.colorScheme.onSurface
    PenStyle.WIDTH_OPTIONS.take(3).forEach { width ->
      val label = stringResource(R.string.pen_width_value, width.toString())
      RailAction(label, "reader_pen_preset_$width", style.width == width, enabled, 28, onClick = { onWidth(width) }) {
        Canvas(Modifier.size(20.dp)) {
          drawLine(lineColor, Offset(2.dp.toPx(), size.height / 2), Offset(size.width - 2.dp.toPx(), size.height / 2), width.toFloat() * 0.8.dp.toPx())
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
}

@Composable
internal fun PenWidthControl(width: Double, enabled: Boolean, tag: String, onWidth: (Double) -> Unit) {
  var draft by remember(width) { mutableFloatStateOf(width.toFloat().coerceIn(0.1f, 20f)) }
  fun rounded(value: Float): Double = (value.coerceIn(0.1f, 20f) * 10).roundToInt() / 10.0
  Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
    Text("${stringResource(R.string.reader_menu_width)}: ${rounded(draft)}", style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = { draft = (draft - 0.1f).coerceAtLeast(0.1f); onWidth(rounded(draft)) },
        enabled = enabled && draft > 0.1f, modifier = Modifier.size(36.dp)) {
        Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.pen_width_decrease))
      }
      Slider(value = draft, onValueChange = { draft = it },
        onValueChangeFinished = { onWidth(rounded(draft)) }, valueRange = 0.1f..20f,
        enabled = enabled, modifier = Modifier.weight(1f).testTag(tag))
      IconButton(onClick = { draft = (draft + 0.1f).coerceAtMost(20f); onWidth(rounded(draft)) },
        enabled = enabled && draft < 20f, modifier = Modifier.size(36.dp)) {
        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.pen_width_increase))
      }
    }
  }
}

@Composable
private fun ColorSwatch(color: String, name: String, active: Boolean, enabled: Boolean, onColor: (String) -> Unit) {
  val label = stringResource(R.string.pen_color_value, name)
  val swatch = Color(android.graphics.Color.parseColor(color))
  val checkColor = if (swatch.luminance() > 0.45f) Color.Black else Color.White
  Box(
    Modifier.size(36.dp, 30.dp).clickable(enabled) { onColor(color) }
      .semantics { contentDescription = label; selected = active }
      .testTag("reader_pen_color_${color.drop(1)}"),
    contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.size(20.dp).background(swatch, RoundedCornerShape(4.dp))
      .border(if (active) 2.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
      if (active) Icon(Icons.Outlined.Check, null, tint = checkColor, modifier = Modifier.size(15.dp))
    }
  }
}

@Composable
private fun RailIcon(icon: ImageVector, label: String, tag: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
  RailAction(label, tag, active, enabled, onClick = onClick) {
    Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline, modifier = Modifier.size(21.dp))
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun RailAction(label: String, tag: String, active: Boolean, enabled: Boolean, height: Int = 40, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
  TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
    Box(
      Modifier.width(READER_PEN_RAIL_WIDTH).height(height.dp)
        .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
        .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
        .semantics { contentDescription = label; selected = active }.testTag(tag),
      contentAlignment = Alignment.Center,
    ) {
      content()
      if (active) Box(Modifier.align(Alignment.CenterStart).width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
  }
}

@Composable
private fun RailDivider() = HorizontalDivider(Modifier.padding(horizontal = 6.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
internal fun PenIllustration(tool: PenCanvasTool, ink: Color, active: Boolean) {
  Canvas(Modifier.fillMaxSize().clipToBounds()) {
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
