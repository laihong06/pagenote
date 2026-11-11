package io.pagenote.android

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenStyle
import kotlin.math.roundToInt
import kotlin.math.abs
import java.util.UUID

/** The box lives in the reader's coordinate system and never reserves canvas space. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FloatingPenBox(
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
  onRestoreStyle: (String, Double) -> Unit = { _, _ -> },
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val preferences = remember { context.getSharedPreferences("reader_floating_pen_box", 0) }
  var edge by remember { mutableStateOf(runCatching { PenBoxEdge.valueOf(preferences.getString("edge", "LEFT")!!) }.getOrDefault(PenBoxEdge.LEFT)) }
  var fraction by remember { mutableFloatStateOf(preferences.getFloat("fraction", .15f).coerceIn(0f, 1f)) }
  var boxSize by remember { mutableStateOf(IntSize.Zero) }
  var dragging by remember { mutableStateOf(false) }
  var dragX by remember { mutableFloatStateOf(0f) }
  var dragY by remember { mutableFloatStateOf(0f) }
  var collapsed by remember { mutableStateOf(preferences.getBoolean("collapsed", false)) }
  var locked by remember { mutableStateOf(preferences.getBoolean("locked", false)) }
  var settingsOpen by remember { mutableStateOf(false) }
  var shapesOpen by remember { mutableStateOf(false) }
  var generalStyleOpen by remember { mutableStateOf(false) }
  var reordering by remember { mutableStateOf(false) }
  val handleLabel = stringResource(if (collapsed) R.string.floating_pen_expand else R.string.floating_pen_handle)
  val selectLabel = stringResource(R.string.reader_menu_select_tool)
  val shapeLabel = stringResource(R.string.reader_menu_shape)
  val styleLabel = stringResource(R.string.pen_style_title)
  val defaultTools = remember { listOf(PenCanvasTool.PEN, PenCanvasTool.BALLPOINT, PenCanvasTool.HIGHLIGHTER, PenCanvasTool.LASER) }
  fun slotTool(id: String): PenCanvasTool? = defaultTools.firstOrNull { it.name == id.substringBefore('|') }
  var orderedSlots by remember {
    val stored = preferences.getString("order", "").orEmpty().split(',').filter { slotTool(it) != null }.distinct()
    mutableStateOf(stored.ifEmpty { defaultTools.map { it.name } })
  }
  var selectedSlot by remember {
    val saved = preferences.getString("selected", tool.name)?.takeIf { it in orderedSlots }
    mutableStateOf(saved?.takeIf { slotTool(it) == tool } ?: orderedSlots.firstOrNull { slotTool(it) == tool } ?: saved ?: orderedSlots.first())
  }
  var initialized by remember { mutableStateOf(false) }
  var previousTool by remember { mutableStateOf(tool) }
  LaunchedEffect(Unit) {
    val choice = slotTool(selectedSlot)
    if (choice == tool && drawTool == null) {
      val color = preferences.getString("${selectedSlot}_color", style.color) ?: style.color
      val width = preferences.getFloat("${selectedSlot}_width", style.width.toFloat()).toDouble()
      onRestoreStyle(color, width)
    }
    initialized = true
  }
  LaunchedEffect(initialized, tool, selectedSlot, style.color, style.width) {
    if (initialized && previousTool == tool && tool != PenCanvasTool.SELECT && slotTool(selectedSlot) == tool) {
      preferences.edit().putString("${selectedSlot}_color", style.color)
        .putFloat("${selectedSlot}_width", style.width.toFloat()).apply()
    }
  }
  LaunchedEffect(initialized, tool) {
    if (initialized && previousTool != tool && tool != PenCanvasTool.SELECT && tool != PenCanvasTool.ERASER) {
      val matching = selectedSlot.takeIf { slotTool(it) == tool } ?: orderedSlots.firstOrNull { slotTool(it) == tool } ?: tool.name.also {
        orderedSlots = orderedSlots + it
        preferences.edit().putString("order", orderedSlots.joinToString(",")).apply()
      }
      selectedSlot = matching
      preferences.edit().putString("selected", matching).apply()
      val color = preferences.getString("${matching}_color", style.color) ?: style.color
      val width = preferences.getFloat("${matching}_width", style.width.toFloat()).toDouble()
      onRestoreStyle(color, width)
    }
    if (initialized) previousTool = tool
  }
  fun selectSlot(id: String) {
    val choice = slotTool(id) ?: return
    if (id == selectedSlot && choice == tool && drawTool == null) return
    val color = preferences.getString("${id}_color", style.color) ?: style.color
    val width = preferences.getFloat("${id}_width", style.width.toFloat()).toDouble()
    selectedSlot = id
    preferences.edit().putString("selected", id).apply()
    onTool(choice)
    onColor(color)
    onWidth(width)
  }
  val vertical = edge == PenBoxEdge.LEFT || edge == PenBoxEdge.RIGHT
  val density = LocalDensity.current
  BoxWithConstraints(modifier.fillMaxSize()) {
    val parentWidth = with(density) { maxWidth.toPx() }
    val parentHeight = with(density) { maxHeight.toPx() }
    val width = boxSize.width.toFloat()
    val height = boxSize.height.toFloat()
    val limitX = (parentWidth - width).coerceAtLeast(0f)
    val limitY = (parentHeight - height).coerceAtLeast(0f)
    val restingX = when (edge) {
      PenBoxEdge.LEFT -> 0f
      PenBoxEdge.RIGHT -> limitX
      else -> PenBoxGeometry.restoreAlongEdge(fraction, parentWidth, width)
    }
    val restingY = when (edge) {
      PenBoxEdge.TOP -> 0f
      PenBoxEdge.BOTTOM -> limitY
      else -> PenBoxGeometry.restoreAlongEdge(fraction, parentHeight, height)
    }
    val animatedX = remember { Animatable(restingX) }
    val animatedY = remember { Animatable(restingY) }
    LaunchedEffect(dragging, dragX, restingX, limitX) {
      if (dragging) animatedX.snapTo(dragX.coerceIn(0f, limitX))
      else animatedX.animateTo(restingX, tween(300))
    }
    LaunchedEffect(dragging, dragY, restingY, limitY) {
      if (dragging) animatedY.snapTo(dragY.coerceIn(0f, limitY))
      else animatedY.animateTo(restingY, tween(300))
    }
    val x = if (dragging) dragX.coerceIn(0f, limitX) else animatedX.value.coerceIn(0f, limitX)
    val y = if (dragging) dragY.coerceIn(0f, limitY) else animatedY.value.coerceIn(0f, limitY)
    val currentPosition by rememberUpdatedState(x to y)
    val shape = RoundedCornerShape(16.dp)
    val listOverflows = if (vertical) (orderedSlots.size * 40 + 188).dp > maxHeight else (orderedSlots.size * 40 + 188).dp > maxWidth
    val dragModifier = Modifier.pointerInput(locked, edge, restingX, restingY, parentWidth, parentHeight, boxSize, listOverflows) {
      try {
      if (!locked) awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val startX = currentPosition.first
        val startY = currentPosition.second
        val downX = startX + down.position.x
        val downY = startY + down.position.y
        val startedOnHandle = if (vertical) down.position.y < 40.dp.toPx() else down.position.x < 40.dp.toPx()
        var moving = false
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          val change = event.changes.firstOrNull { it.id == down.id } ?: break
          val currentX = (if (moving) dragX else startX) + change.position.x
          val currentY = (if (moving) dragY else startY) + change.position.y
          val deltaX = currentX - downX
          val deltaY = currentY - downY
          val dragAcrossRail = if (vertical) abs(deltaX) > 28.dp.toPx() else abs(deltaY) > 28.dp.toPx()
          val canMoveBox = !listOverflows || startedOnHandle || dragAcrossRail
          if (!moving && !reordering && canMoveBox && (abs(deltaX) > 28.dp.toPx() || abs(deltaY) > 28.dp.toPx())) {
            moving = true
            dragging = true
          }
          if (moving && change.pressed) {
            dragX = (startX + deltaX).coerceIn(0f, limitX)
            dragY = (startY + deltaY).coerceIn(0f, limitY)
            change.consume()
          }
          if (!change.pressed) {
            if (moving) {
              edge = PenBoxGeometry.nearestEdge(dragX, dragY, width, height, parentWidth, parentHeight)
              fraction = if (edge == PenBoxEdge.LEFT || edge == PenBoxEdge.RIGHT) {
                if (parentHeight > 0f) (dragY / parentHeight).coerceIn(0f, 1f) else 0f
              } else if (parentWidth > 0f) (dragX / parentWidth).coerceIn(0f, 1f) else 0f
              preferences.edit().putString("edge", edge.name).putFloat("fraction", fraction).apply()
              dragging = false
              change.consume()
            }
            break
          }
        }
      }
      } finally {
        dragging = false
      }
    }
    val content: @Composable () -> Unit = {
      Box(
        Modifier.size(36.dp)
          .combinedClickable(onClick = { collapsed = !collapsed; preferences.edit().putBoolean("collapsed", collapsed).apply() }, onLongClick = { locked = !locked; preferences.edit().putBoolean("locked", locked).apply() })
          .semantics { contentDescription = handleLabel }
          .testTag("floating_pen_box_handle"),
        contentAlignment = Alignment.Center,
      ) { Icon(if (locked) Icons.Outlined.Lock else if (collapsed) Icons.Outlined.ChevronRight else Icons.Outlined.DragIndicator, null, tint = Color(0xff626262), modifier = Modifier.size(22.dp)) }
      if (!collapsed) {
        orderedSlots.forEachIndexed { index, slotId ->
          val choice = slotTool(slotId) ?: return@forEachIndexed
          val label = stringResource(when (choice) {
            PenCanvasTool.PEN -> R.string.pen
            PenCanvasTool.BALLPOINT -> R.string.pen_ballpoint
            PenCanvasTool.HIGHLIGHTER -> R.string.highlighter
            PenCanvasTool.LASER -> R.string.pen_laser
            else -> R.string.eraser
          })
          val active = slotId == selectedSlot && choice == tool && drawTool == null
          val slotColor = if (active) style.color else preferences.getString("${slotId}_color", style.color) ?: style.color
          Box {
            Box(Modifier.size(40.dp)
              .pointerInput(orderedSlots, vertical) {
                var displacement = 0f
                detectDragGesturesAfterLongPress(
                  onDragStart = { displacement = 0f; reordering = true },
                  onDrag = { change, delta -> change.consume(); displacement += if (vertical) delta.y else delta.x },
                  onDragEnd = {
                    val target = (index + (displacement / 40.dp.toPx()).roundToInt()).coerceIn(0, orderedSlots.lastIndex)
                    orderedSlots = PenBoxGeometry.moveItem(orderedSlots, index, target)
                    preferences.edit().putString("order", orderedSlots.joinToString(",")).apply()
                    reordering = false
                  },
                  onDragCancel = { reordering = false },
                )
              }
              .combinedClickable(enabled = enabled, onClick = { if (active) settingsOpen = true else selectSlot(slotId) })
              .semantics { contentDescription = label; selected = active }.testTag("reader_pen_${choice.name.lowercase()}"), contentAlignment = Alignment.Center) {
              if (choice != PenCanvasTool.ERASER) {
                val background = when (choice) {
                  PenCanvasTool.PEN -> if (vertical) R.drawable.reference_pen_horizontal else R.drawable.reference_pen_vertical
                  PenCanvasTool.BALLPOINT -> if (vertical) R.drawable.reference_pencil_horizontal else R.drawable.reference_pencil_vertical
                  PenCanvasTool.LASER -> if (vertical) R.drawable.reference_laser_horizontal else R.drawable.reference_laser_vertical
                  else -> if (vertical) R.drawable.reference_marker_horizontal else R.drawable.reference_marker_vertical
                }
                val overlay = when (choice) {
                  PenCanvasTool.PEN -> if (vertical) R.drawable.reference_pen_color_horizontal else R.drawable.reference_pen_color_vertical
                  PenCanvasTool.BALLPOINT -> if (vertical) R.drawable.reference_pencil_color_horizontal else R.drawable.reference_pencil_color_vertical
                  PenCanvasTool.LASER -> if (vertical) R.drawable.reference_laser_color_horizontal else R.drawable.reference_laser_color_vertical
                  else -> if (vertical) R.drawable.reference_marker_color_horizontal else R.drawable.reference_marker_color_vertical
                }
                Box(Modifier.size(if (vertical) 32.dp else 16.dp, if (vertical) 16.dp else 32.dp)
                  .graphicsLayer {
                    rotationZ = if (edge == PenBoxEdge.RIGHT || edge == PenBoxEdge.TOP) 180f else 0f
                    val inset = if (active) 0f else 8.dp.toPx()
                    when (edge) {
                      PenBoxEdge.LEFT -> translationX = -inset
                      PenBoxEdge.RIGHT -> translationX = inset
                      PenBoxEdge.TOP -> translationY = -inset
                      PenBoxEdge.BOTTOM -> translationY = inset
                    }
                  }) {
                  Image(painterResource(background), null, Modifier.fillMaxSize())
                  Image(painterResource(overlay), null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(Color(android.graphics.Color.parseColor(slotColor))))
                }
              } else {
                Box(Modifier.size(32.dp).graphicsLayer { rotationZ = if (vertical) 0f else 90f }) {
                  PenIllustration(choice, Color(android.graphics.Color.parseColor(slotColor)), active)
                }
              }
            }
            if (active) DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }, modifier = Modifier.width(252.dp).testTag("reader_pen_style_menu")) {
              PenWidthControl(style.width, enabled, "reader_pen_style_width_slider", onWidth)
              Text(stringResource(R.string.reader_menu_color), Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
              PenStyle.COLOR_SWATCHES.chunked(4).forEach { colors ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                  colors.forEach { (name, color) ->
                    Box(Modifier.size(40.dp).combinedClickable(enabled = enabled, onClick = { onColor(color) }).semantics { contentDescription = name; selected = style.color == color }.testTag("reader_pen_color_${color.drop(1)}"), contentAlignment = Alignment.Center) {
                      Box(Modifier.size(22.dp).background(Color(android.graphics.Color.parseColor(color)), RoundedCornerShape(4.dp)))
                    }
                  }
                }
              }
              DropdownMenuItem(text = { Text(stringResource(R.string.pen_custom_color)) }, onClick = { settingsOpen = false; onCustomColor() })
              DropdownMenuItem(text = { Text(stringResource(R.string.floating_pen_duplicate)) }, modifier = Modifier.testTag("floating_pen_duplicate"), onClick = {
                val copyId = "${choice.name}|${UUID.randomUUID()}"
                preferences.edit().putString("${copyId}_color", style.color).putFloat("${copyId}_width", style.width.toFloat()).apply()
                orderedSlots = orderedSlots.toMutableList().apply { add(index + 1, copyId) }
                preferences.edit().putString("order", orderedSlots.joinToString(",")).apply()
                settingsOpen = false
                selectSlot(copyId)
              })
              if (orderedSlots.size > 1) DropdownMenuItem(text = { Text(stringResource(R.string.floating_pen_remove)) }, modifier = Modifier.testTag("floating_pen_remove"), onClick = {
                orderedSlots = orderedSlots.filterNot { it == slotId }
                preferences.edit().putString("order", orderedSlots.joinToString(",")).remove("${slotId}_color").remove("${slotId}_width").apply()
                settingsOpen = false
                selectSlot(orderedSlots.first())
              })
            }
          }
        }
        Box {
          val eraserLabel = stringResource(R.string.eraser)
          Box(Modifier.size(40.dp).combinedClickable(enabled = enabled, onClick = {
            if (tool == PenCanvasTool.ERASER) settingsOpen = true else onTool(PenCanvasTool.ERASER)
          }).semantics { contentDescription = eraserLabel; selected = tool == PenCanvasTool.ERASER }.testTag("reader_pen_eraser"), contentAlignment = Alignment.Center) {
            Box(Modifier.size(32.dp).graphicsLayer { rotationZ = if (vertical) 0f else 90f }) {
              PenIllustration(PenCanvasTool.ERASER, Color(android.graphics.Color.parseColor(style.color)), tool == PenCanvasTool.ERASER)
            }
          }
          if (tool == PenCanvasTool.ERASER) DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }, modifier = Modifier.width(252.dp).testTag("reader_pen_style_menu")) {
            PenWidthControl(style.width, enabled, "reader_pen_style_width_slider", onWidth)
          }
        }
        Box(Modifier.size(36.dp).combinedClickable(enabled = selectionEnabled, onClick = { onTool(PenCanvasTool.SELECT) }).semantics { contentDescription = selectLabel; selected = tool == PenCanvasTool.SELECT }.testTag("reader_pen_select"), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.NearMe, null, modifier = Modifier.size(22.dp)) }
        Box {
          Box(Modifier.size(36.dp).combinedClickable(enabled = enabled, onClick = { generalStyleOpen = true }).semantics { contentDescription = styleLabel }.testTag("reader_pen_style"), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Tune, null, modifier = Modifier.size(22.dp)) }
          DropdownMenu(expanded = generalStyleOpen, onDismissRequest = { generalStyleOpen = false }, modifier = Modifier.width(252.dp).testTag("reader_pen_style_menu")) {
            PenWidthControl(style.width, enabled, "reader_pen_style_width_slider", onWidth)
            Text(stringResource(R.string.reader_menu_color), Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
            PenStyle.COLOR_SWATCHES.chunked(4).forEach { colors ->
              Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                colors.forEach { (name, color) ->
                  Box(Modifier.size(40.dp).combinedClickable(enabled = enabled, onClick = { onColor(color) }).semantics { contentDescription = name; selected = style.color == color }.testTag("reader_pen_color_${color.drop(1)}"), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(22.dp).background(Color(android.graphics.Color.parseColor(color)), RoundedCornerShape(4.dp)))
                  }
                }
              }
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.pen_custom_color)) }, onClick = { generalStyleOpen = false; onCustomColor() })
          }
        }
        Box {
          Box(Modifier.size(36.dp).combinedClickable(enabled = enabled, onClick = { shapesOpen = true }).semantics { contentDescription = shapeLabel }.testTag("reader_pen_shapes"), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Category, null, modifier = Modifier.size(22.dp)) }
          DropdownMenu(expanded = shapesOpen, onDismissRequest = { shapesOpen = false }) {
            listOf(PdfAnnotationDrawTool.LINE to R.string.reader_menu_line, PdfAnnotationDrawTool.ARROW to R.string.reader_menu_arrow, PdfAnnotationDrawTool.RECTANGLE to R.string.annotation_shape_rectangle, PdfAnnotationDrawTool.ELLIPSE to R.string.annotation_shape_ellipse).forEach { (choice, label) ->
              DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { shapesOpen = false; onShape(choice) }, modifier = Modifier.testTag("reader_pen_shape_${choice.name.lowercase()}"))
            }
          }
        }
      }
    }
    if (vertical) {
      Column(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.heightIn(max = maxHeight).onSizeChanged { boxSize = it }.then(dragModifier).shadow(6.dp, shape).background(MaterialTheme.colorScheme.surface, shape).verticalScroll(rememberScrollState()).padding(2.dp).testTag("floating_pen_box"), horizontalAlignment = Alignment.CenterHorizontally) { content() }
    } else {
      Row(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.widthIn(max = maxWidth).onSizeChanged { boxSize = it }.then(dragModifier).shadow(6.dp, shape).background(MaterialTheme.colorScheme.surface, shape).horizontalScroll(rememberScrollState()).padding(2.dp).testTag("floating_pen_box"), verticalAlignment = Alignment.CenterVertically) { content() }
    }
  }
}
