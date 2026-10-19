package io.pagenote.feature.pen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class PenCanvasTool { SELECT, PEN, HIGHLIGHTER, ERASER }

/** Native ink layer. Persistence and page-scoped history remain with the caller. */
class PenCanvasView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
) : View(context, attrs) {
  private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }
  private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(0, 122, 204)
    style = Paint.Style.STROKE
    strokeWidth = 3f
  }
  private val activePoints = mutableListOf<PenPoint>()
  private var internalStrokes: List<PenStroke> = emptyList()
  private var activePointerId = INVALID_POINTER
  private var selectionPointerId = INVALID_POINTER
  private var annotationSelectionPointerId = INVALID_POINTER
  private var gestureTool: PenCanvasTool? = null
  private var eraseModeLatched = false
  private var activeColor = "#dc3545"
  private var activeWidth = 3.0

  var selectedTool: PenCanvasTool = PenCanvasTool.PEN
    set(value) {
      field = value
      invalidate()
    }

  var penColor: String
    get() = activeColor
    set(value) {
      activeColor = value
    }

  var penWidth: Double
    get() = activeWidth
    set(value) {
      require(value.isFinite() && value in PenValidation.MIN_WIDTH..PenValidation.MAX_WIDTH)
      activeWidth = value
    }

  var eraserRadiusPx: Float = 22f
    set(value) {
      require(value.isFinite() && value >= 0f)
      field = value
    }

  var onStrokeAdded: ((PenStroke) -> Unit)? = null
  var onStrokeErased: ((Int) -> Unit)? = null
  var onSelectionChanged: ((Int?) -> Unit)? = null
  var onAnnotationSelectionTouch: ((MotionEvent, Float, Int) -> Boolean)? = null
  var inputEnabled: Boolean = true
  var selectedIndex: Int? = null
    set(value) {
      field = value
      invalidate()
    }

  var strokes: List<PenStroke>
    get() = internalStrokes
    set(value) {
      internalStrokes = value.toList()
      invalidate()
    }

  init {
    isClickable = true
    isFocusable = true
    setWillNotDraw(false)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (width <= 0 || height <= 0) return
    val save = canvas.save()
    canvas.scale(width / 1000f, width / 1000f)
    internalStrokes.forEach { drawStroke(canvas, it) }
    selectedIndex?.let { index -> internalStrokes.getOrNull(index)?.let { drawSelection(canvas, it) } }
    gestureTool?.takeIf { it != PenCanvasTool.ERASER }?.let { tool ->
      if (activePoints.isNotEmpty()) {
        drawStroke(
          canvas,
          PenStroke(
            type = if (tool == PenCanvasTool.HIGHLIGHTER) "highlight" else "pen",
            color = activeColor,
            width = activeWidth,
            points = activePoints.toList(),
          ),
        )
      }
    }
    canvas.restoreToCount(save)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!inputEnabled) return false
    if (selectedTool == PenCanvasTool.SELECT) return handleSelectionTouch(event)
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> return beginGesture(event, event.actionIndex)
      MotionEvent.ACTION_POINTER_DOWN -> return activePointerId != INVALID_POINTER
      MotionEvent.ACTION_MOVE -> {
        if (activePointerId == INVALID_POINTER) return false
        val index = event.findPointerIndex(activePointerId)
        if (index < 0) return true
        updateGestureTool(event, index)
        if (gestureTool == PenCanvasTool.ERASER) {
          for (history in 0 until event.historySize) {
            eraseAt(
              event.getHistoricalX(index, history),
              event.getHistoricalY(index, history),
            )
          }
          eraseAt(event.getX(index), event.getY(index))
        } else {
          for (history in 0 until event.historySize) {
            addPoint(
              event.getHistoricalX(index, history),
              event.getHistoricalY(index, history),
              event.getHistoricalPressure(index, history),
            )
          }
          addPoint(event.getX(index), event.getY(index), event.getPressure(index))
        }
        invalidate()
        return true
      }
      MotionEvent.ACTION_BUTTON_PRESS -> {
        val index = event.findPointerIndex(activePointerId)
        if (index >= 0) {
          updateGestureTool(event, index)
          if (gestureTool == PenCanvasTool.ERASER) eraseAt(event.getX(index), event.getY(index))
        }
        return activePointerId != INVALID_POINTER
      }
      MotionEvent.ACTION_BUTTON_RELEASE -> {
        val index = event.findPointerIndex(activePointerId)
        if (index >= 0) updateGestureTool(event, index)
        return activePointerId != INVALID_POINTER
      }
      MotionEvent.ACTION_POINTER_UP -> {
        if (event.getPointerId(event.actionIndex) == activePointerId) resetGesture()
        return activePointerId != INVALID_POINTER
      }
      MotionEvent.ACTION_UP -> {
        if (activePointerId == INVALID_POINTER) return false
        val index = event.findPointerIndex(activePointerId)
        if (index >= 0) {
          updateGestureTool(event, index)
          if (gestureTool == PenCanvasTool.ERASER) {
            eraseAt(event.getX(index), event.getY(index))
          } else {
            addPoint(event.getX(index), event.getY(index), event.getPressure(index))
            finishStroke()
          }
        }
        resetGesture()
        invalidate()
        performClick()
        return true
      }
      MotionEvent.ACTION_CANCEL -> {
        val hadGesture = activePointerId != INVALID_POINTER
        resetGesture()
        invalidate()
        return hadGesture
      }
    }
    return activePointerId != INVALID_POINTER
  }

  override fun onGenericMotionEvent(event: MotionEvent): Boolean {
    if (!inputEnabled || selectedTool == PenCanvasTool.SELECT) return false
    val action = event.actionMasked
    if (action != MotionEvent.ACTION_BUTTON_PRESS && action != MotionEvent.ACTION_BUTTON_RELEASE) {
      return super.onGenericMotionEvent(event)
    }
    if (activePointerId == INVALID_POINTER) return super.onGenericMotionEvent(event)
    val index = event.findPointerIndex(activePointerId)
    if (index < 0 || event.getToolType(index) != MotionEvent.TOOL_TYPE_STYLUS) {
      return super.onGenericMotionEvent(event)
    }

    if (action == MotionEvent.ACTION_BUTTON_PRESS) {
      if (!hasEraserButton(event.buttonState)) return super.onGenericMotionEvent(event)
      updateGestureTool(event, index)
      eraseAt(event.getX(index), event.getY(index))
      invalidate()
      return true
    }

    if (!eraseModeLatched) return super.onGenericMotionEvent(event)
    updateGestureTool(event, index)
    return true
  }

  override fun performClick(): Boolean {
    super.performClick()
    return true
  }

  private fun beginGesture(event: MotionEvent, pointerIndex: Int): Boolean {
    if (!isSupportedTool(event.getToolType(pointerIndex))) return false
    activePointerId = event.getPointerId(pointerIndex)
    requestUnbufferedDispatch(event)
    val eraserInput = event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_ERASER ||
      (event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_STYLUS &&
        hasEraserButton(event.buttonState))
    eraseModeLatched = eraserInput || selectedTool == PenCanvasTool.ERASER
    gestureTool = if (eraseModeLatched) PenCanvasTool.ERASER else selectedTool
    if (gestureTool == PenCanvasTool.ERASER) {
      eraseAt(event.getX(pointerIndex), event.getY(pointerIndex))
    } else {
      addPoint(event.getX(pointerIndex), event.getY(pointerIndex), event.getPressure(pointerIndex))
      invalidate()
    }
    return true
  }

  private fun handleSelectionTouch(event: MotionEvent): Boolean {
    if (annotationSelectionPointerId != INVALID_POINTER) {
      onAnnotationSelectionTouch?.invoke(
        event,
        width.toFloat(),
        event.findPointerIndex(annotationSelectionPointerId),
      )
      when (event.actionMasked) {
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          annotationSelectionPointerId = INVALID_POINTER
          selectionPointerId = INVALID_POINTER
        }
        MotionEvent.ACTION_POINTER_UP -> {
          if (event.getPointerId(event.actionIndex) == annotationSelectionPointerId) {
            annotationSelectionPointerId = INVALID_POINTER
            selectionPointerId = INVALID_POINTER
          }
        }
      }
      return true
    }
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val pointerIndex = event.actionIndex
        if (!isSupportedTool(event.getToolType(pointerIndex))) return false
        if (onAnnotationSelectionTouch?.invoke(event, width.toFloat(), pointerIndex) == true) {
          annotationSelectionPointerId = event.getPointerId(pointerIndex)
          selectionPointerId = annotationSelectionPointerId
          selectedIndex = null
          onSelectionChanged?.invoke(null)
          requestUnbufferedDispatch(event)
          invalidate()
          return true
        }
        selectionPointerId = event.getPointerId(pointerIndex)
        requestUnbufferedDispatch(event)
        val radius = 18f * resources.displayMetrics.density
        val nextSelection = PenCanvasGeometry(width.toFloat(), height.toFloat())
          .hitTest(internalStrokes, event.getX(pointerIndex), event.getY(pointerIndex), radius)
        if (selectedIndex != nextSelection) {
          selectedIndex = nextSelection
          onSelectionChanged?.invoke(nextSelection)
        }
        invalidate()
        return true
      }
      MotionEvent.ACTION_MOVE -> return selectionPointerId != INVALID_POINTER
      MotionEvent.ACTION_POINTER_DOWN -> return selectionPointerId != INVALID_POINTER
      MotionEvent.ACTION_UP -> {
        if (selectionPointerId == INVALID_POINTER) return false
        selectionPointerId = INVALID_POINTER
        performClick()
        return true
      }
      MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
        val hadSelectionGesture = selectionPointerId != INVALID_POINTER
        onAnnotationSelectionTouch?.invoke(event, width.toFloat(), event.actionIndex)
        selectionPointerId = INVALID_POINTER
        return hadSelectionGesture
      }
    }
    return selectionPointerId != INVALID_POINTER
  }

  private fun drawSelection(canvas: Canvas, stroke: PenStroke) {
    val points = stroke.points
    if (points.isEmpty()) return
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val inset = (stroke.width.toFloat() / 2f) + SELECTION_INSET
    val left = minX - inset
    val top = minY - inset
    val right = maxX + inset
    val bottom = maxY + inset
    val minimumSize = SELECTION_MIN_SIZE
    val adjustedLeft = if (right - left < minimumSize) left - (minimumSize - (right - left)) / 2f else left
    val adjustedTop = if (bottom - top < minimumSize) top - (minimumSize - (bottom - top)) / 2f else top
    val adjustedRight = if (right - left < minimumSize) right + (minimumSize - (right - left)) / 2f else right
    val adjustedBottom = if (bottom - top < minimumSize) bottom + (minimumSize - (bottom - top)) / 2f else bottom
    canvas.drawRect(adjustedLeft, adjustedTop, adjustedRight, adjustedBottom, selectionPaint)
  }

  private fun isSupportedTool(toolType: Int): Boolean =
    toolType == MotionEvent.TOOL_TYPE_FINGER ||
      toolType == MotionEvent.TOOL_TYPE_STYLUS ||
      toolType == MotionEvent.TOOL_TYPE_ERASER

  private fun updateGestureTool(event: MotionEvent, pointerIndex: Int) {
    val inputTool = event.getToolType(pointerIndex)
    val eraserInput = inputTool == MotionEvent.TOOL_TYPE_ERASER ||
      (inputTool == MotionEvent.TOOL_TYPE_STYLUS &&
        hasEraserButton(event.buttonState))
    if (eraserInput) eraseModeLatched = true
    val nextTool = if (eraseModeLatched || selectedTool == PenCanvasTool.ERASER) {
      PenCanvasTool.ERASER
    } else {
      selectedTool
    }
    if (gestureTool != nextTool) {
      activePoints.clear()
      gestureTool = nextTool
    }
  }

  private fun addPoint(xPx: Float, yPx: Float, pressure: Float) {
    val point = PenCanvasGeometry(width.toFloat(), height.toFloat())
      .toDocumentPoint(xPx, yPx, pressure) ?: return
    if (activePoints.lastOrNull() != point) activePoints.add(point)
  }

  private fun eraseAt(xPx: Float, yPx: Float) {
    val index = PenCanvasGeometry(width.toFloat(), height.toFloat())
      .hitTest(internalStrokes, xPx, yPx, eraserRadiusPx) ?: return
    internalStrokes = internalStrokes.toMutableList().also { it.removeAt(index) }
    onStrokeErased?.invoke(index)
    invalidate()
  }

  private fun finishStroke() {
    if (activePoints.isEmpty()) return
    val tool = gestureTool ?: return
    val stroke = PenStroke(
      type = if (tool == PenCanvasTool.HIGHLIGHTER) "highlight" else "pen",
      color = activeColor,
      width = activeWidth,
      points = activePoints.toList(),
    )
    PenValidation.validate(stroke, 1)
    internalStrokes = internalStrokes + stroke
    onStrokeAdded?.invoke(stroke)
  }

  private fun resetGesture() {
    activePointerId = INVALID_POINTER
    gestureTool = null
    eraseModeLatched = false
    activePoints.clear()
  }

  private fun hasEraserButton(buttonState: Int): Boolean = buttonState and ERASER_BUTTONS != 0

  private fun drawStroke(canvas: Canvas, stroke: PenStroke) {
    val color = runCatching { Color.parseColor(stroke.color) }.getOrDefault(Color.BLACK)
    val isHighlight = stroke.type == "highlight"
    val points = stroke.points
    if (points.isEmpty()) return
    drawPaint.color = color
    drawPaint.alpha = if (isHighlight) HIGHLIGHT_ALPHA else Color.alpha(color)
    drawPaint.style = Paint.Style.STROKE
    drawPaint.strokeWidth = stroke.width.toFloat()

    when (stroke.type) {
      "rect" -> {
        if (points.size >= 2) {
          drawPaint.style = Paint.Style.STROKE
          canvas.drawRect(
            min(points.first().x, points.last().x),
            min(points.first().y, points.last().y),
            max(points.first().x, points.last().x),
            max(points.first().y, points.last().y),
            drawPaint,
          )
        }
      }
      "ellipse" -> {
        if (points.size >= 2) {
          canvas.drawOval(
            min(points.first().x, points.last().x),
            min(points.first().y, points.last().y),
            max(points.first().x, points.last().x),
            max(points.first().y, points.last().y),
            drawPaint,
          )
        }
      }
      "line", "arrow" -> {
        if (points.size >= 2) {
          val first = points.first()
          val last = points.last()
          canvas.drawLine(first.x, first.y, last.x, last.y, drawPaint)
          if (stroke.type == "arrow") drawArrowHead(canvas, first, last, drawPaint)
        }
      }
      "pen", "highlight" -> drawFreehand(canvas, points, stroke.width.toFloat(), isHighlight)
      else -> Unit
    }
  }

  private fun drawFreehand(canvas: Canvas, points: List<PenPoint>, width: Float, highlight: Boolean) {
    drawPaint.style = Paint.Style.STROKE
    drawPaint.alpha = if (highlight) HIGHLIGHT_ALPHA else 255
    if (points.size == 1) {
      drawPaint.style = Paint.Style.FILL
      canvas.drawCircle(points[0].x, points[0].y, width * points[0].pressure / 2f, drawPaint)
      return
    }
    for (index in 1 until points.size) {
      val previous = points[index - 1]
      val current = points[index]
      drawPaint.strokeWidth = width * (previous.pressure + current.pressure) / 2f
      val path = Path().apply {
        moveTo(previous.x, previous.y)
        lineTo(current.x, current.y)
      }
      canvas.drawPath(path, drawPaint)
    }
  }

  private fun drawArrowHead(canvas: Canvas, start: PenPoint, end: PenPoint, paint: Paint) {
    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble()).toFloat()
    val headLength = (paint.strokeWidth * 4f).coerceIn(12f, 40f)
    val spread = 0.55f
    val path = Path().apply {
      moveTo(end.x, end.y)
      lineTo(end.x - headLength * cos(angle - spread), end.y - headLength * sin(angle - spread))
      moveTo(end.x, end.y)
      lineTo(end.x - headLength * cos(angle + spread), end.y - headLength * sin(angle + spread))
    }
    canvas.drawPath(path, paint)
  }

  private companion object {
    const val INVALID_POINTER = -1
    const val HIGHLIGHT_ALPHA = 89
    const val ERASER_BUTTONS = MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_STYLUS_SECONDARY
    const val SELECTION_INSET = 8f
    const val SELECTION_MIN_SIZE = 24f
  }
}
