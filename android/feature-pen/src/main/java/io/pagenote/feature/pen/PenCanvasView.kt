package io.pagenote.feature.pen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.DashPathEffect
import android.util.AttributeSet
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.hypot

enum class PenCanvasTool(val persistedType: String?) {
  SELECT(null), PEN("pen"), BALLPOINT("pen"), HIGHLIGHTER("highlight"), LASER(null), ERASER(null);

  fun pointPressure(pressure: Float): Float = if (this == BALLPOINT || this == LASER) 1f else pressure
}

/** Native ink layer. Persistence and page-scoped history remain with the caller. */
class PenCanvasView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
) : View(context, attrs) {
  private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(0, 122, 204)
    style = Paint.Style.STROKE
    strokeWidth = 3f
  }
  private val selectionLassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(0, 122, 204)
    style = Paint.Style.STROKE
    strokeWidth = 2.5f
    pathEffect = DashPathEffect(floatArrayOf(7f, 5f), 0f)
  }
  private val eraserOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.DKGRAY
    style = Paint.Style.STROKE
    strokeWidth = resources.displayMetrics.density.coerceAtLeast(1f)
  }
  private var eraserPosition: Pair<Float, Float>? = null
  private val activePoints = mutableListOf<PenPoint>()
  private val shapeGeneration = AtomicLong()
  internal var shapeRecognizer: ((List<PenPoint>) -> PenRecognizedShape?)? = null
  private var cachedGeometry: PenCanvasGeometry? = null
  private var cachedGeometryWidth = -1
  private var cachedGeometryHeight = -1
  private var cachedGeometryTransform: PenCanvasPageTransform? = null
  private val shapeHandler = Handler(Looper.getMainLooper())
  private var internalStrokes: List<PenStroke> = emptyList()
  private val strokeCache = PenStrokeCache()
  private val liveStrokeCache = PenLiveStrokeCache()
  private val predictor = PenPredictor(
    densityPx = resources.displayMetrics.density,
    // Context.display throws on non-visual contexts (API 30+), so the safe
    // fallback must catch instead of relying on a null-safe call.
    refreshRateHz = runCatching { context.display?.refreshRate ?: 60f }.getOrDefault(60f),
  )
  private var activePointerId = INVALID_POINTER
  private var selectionPointerId = INVALID_POINTER
  private var annotationSelectionPointerId = INVALID_POINTER
  private val selectionLassoPoints = mutableListOf<PenPoint>()
  private var selectionLassoActive = false
  private var gestureTool: PenCanvasTool? = null
  private var activePointerToolType = MotionEvent.TOOL_TYPE_UNKNOWN
  private var shapeRecognitionCancelled = false
  private var shapePreview: PenRecognizedShape? = null
  private var shapeDwellAnchor: Pair<Float, Float>? = null
  private var shapeDwellTask: Runnable? = null
  private val referenceShapeSampler = ReferenceShapeSampler()
  private var eraseModeLatched = false
  private var eraseOriginal: List<PenStroke>? = null
  private var lastErasePoint: PenPoint? = null
  private var activeColor = "#dc3545"
  private var activeWidth = 3.0
  private var laserTrail: List<PenPoint> = emptyList()
  private var laserColor = "#dc3545"
  private var laserWidth = 3.0
  private val clearLaser = Runnable { laserTrail = emptyList(); invalidate() }

  /** Reset unfinished edits before this view is reused for a different page or project. */
  var contentIdentity: Any? = null
    set(value) {
      if (field != value) {
        discardAreaErase()
        cancelActiveGesture()
      }
      field = value
    }

  var pageTransform: PenCanvasPageTransform? = null
    set(value) {
      if (field != value) clearLaserTrail()
      field = value
      invalidate()
    }

  var selectedTool: PenCanvasTool = PenCanvasTool.PEN
    set(value) {
      if (field != value) clearLaserTrail()
      if (field != value && value != PenCanvasTool.PEN && activePointerId != INVALID_POINTER) {
        clearShapeRecognition(cancelled = true)
      }
      if (field != value && value != PenCanvasTool.ERASER) eraserPosition = null
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
  var onShapeRecognized: ((PenRecognizedShape, PenStroke) -> Boolean)? = null
  var onStrokeErased: ((Int) -> Unit)? = null
  var onAreaEraseCommitted: ((List<PenStroke>) -> Unit)? = null
  var onNativeAreaEraseSegment: ((PenPoint, PenPoint, Float) -> Unit)? = null
  var onNativeAreaEraseFinished: ((Boolean) -> Unit)? = null
  var onSelectionChanged: ((Int?) -> Unit)? = null
  var onSelectionRegionCommitted: ((PenSelectionRegion) -> Unit)? = null
  var onAnnotationSelectionTouch: ((MotionEvent, PenCanvasGeometry, Int) -> Boolean)? = null
  var inputEnabled: Boolean = true
    set(value) {
      field = value
      if (!value && activePointerId != INVALID_POINTER) cancelActiveGesture()
      if (!value && eraserPosition != null) {
        eraserPosition = null
        invalidate()
      }
    }
  var viewportTransformInProgress: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        invalidate()
      }
    }
  var selectedIndex: Int? = null
    set(value) {
      field = value
      invalidate()
    }

  var strokes: List<PenStroke>
    get() = internalStrokes
    set(value) {
      if (eraseOriginal != null) return
      internalStrokes = value.toList()
      strokeCache.setStrokes(internalStrokes)
      invalidate()
    }

  init {
    isClickable = true
    isFocusable = true
    setWillNotDraw(false)
  }
  /** In continuous PDF readers, fingers scroll the document and only a pen writes ink. */
  var stylusOnlyInput: Boolean = false

  var selectedStrokeIndices: Set<Int> = emptySet()
    set(value) {
      field = value
      invalidate()
    }

  var selectedStrokeBoundsVisible: Boolean = true
    set(value) {
      field = value
      invalidate()
    }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (width <= 0 || height <= 0) return
    val geometry = geometry()
    if (!geometry.isValid) return
    strokeCache.drawInto(canvas, geometry, width, height, viewportTransformInProgress)
    val save = canvas.save()
    canvas.translate(geometry.pageOriginX, geometry.pageOriginY)
    canvas.scale(geometry.pixelsPerDocumentUnit, geometry.pixelsPerDocumentUnit)
    if (laserTrail.isNotEmpty()) {
      PenStrokePainter.drawLaser(canvas, laserTrail, laserColor, laserWidth, geometry)
    }
    if (selectedStrokeBoundsVisible) {
      val selectedStrokes = (selectedStrokeIndices + listOfNotNull(selectedIndex))
        .mapNotNull(internalStrokes::getOrNull)
      if (selectedStrokes.size > 1) drawSelectionBounds(canvas, selectedStrokes)
      else selectedStrokes.firstOrNull()?.let { drawSelection(canvas, it) }
    }
    if (selectionLassoActive && selectionLassoPoints.isNotEmpty()) {
      val lasso = Path().apply {
        val first = selectionLassoPoints.first()
        moveTo(first.x, first.y)
        selectionLassoPoints.drop(1).forEach { lineTo(it.x, it.y) }
        if (selectionLassoPoints.size >= 3) close()
      }
      canvas.drawPath(lasso, selectionLassoPaint)
    }
    gestureTool?.takeIf { it != PenCanvasTool.ERASER }?.let { tool ->
      if (activePoints.isNotEmpty()) {
        val preview = shapePreview
        if (tool == PenCanvasTool.LASER) {
          PenStrokePainter.drawLaser(canvas, activePoints, activeColor, activeWidth, geometry)
        } else if (preview != null) {
          val isSegmentPath = preview.type == "polyline" || preview.type == "curve" || preview.type == "closedPath"
          PenStrokePainter.drawStroke(
            canvas,
            PenStroke(
              if (isSegmentPath) "pen" else preview.type,
              activeColor, activeWidth, preview.points,
              extraFields = when (preview.type) {
                "polyline" -> mapOf(PenStroke.POLYLINE_FIELD to true)
                "curve" -> mapOf(PenStroke.CURVE_FIELD to true)
                "closedPath" -> mapOf(PenStroke.POLYLINE_FIELD to true)
                else -> emptyMap()
              },
            ),
            geometry,
          )
        } else if (tool == PenCanvasTool.PEN || tool == PenCanvasTool.BALLPOINT) {
          liveStrokeCache.draw(canvas, activePoints, activeColor, activeWidth)
        } else {
          PenStrokePainter.drawStroke(
            canvas,
            PenStroke(tool.persistedType ?: "pen", activeColor, activeWidth, activePoints.toList()),
            geometry,
          )
        }
      }
    }
    predictedDocumentPoint()?.let { predicted ->
      activePoints.lastOrNull()?.let { last ->
        PenStrokePainter.drawSegment(canvas, last, predicted, activeColor, activeWidth.toFloat())
      }
    }
    canvas.restoreToCount(save)
    eraserPosition?.let { (x, y) -> canvas.drawCircle(x, y, eraserRadiusPx, eraserOutlinePaint) }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!inputEnabled) return false
    if (stylusOnlyInput) {
      val pointerIndex = if (activePointerId != INVALID_POINTER) {
        event.findPointerIndex(activePointerId)
      } else {
        event.actionIndex
      }
      if (pointerIndex >= 0) {
        val toolType = event.getToolType(pointerIndex)
        if (toolType != MotionEvent.TOOL_TYPE_STYLUS && toolType != MotionEvent.TOOL_TYPE_ERASER) {
          if (activePointerId != INVALID_POINTER) {
            resetGesture(commitNativeAreaErase = false)
            invalidate()
          }
          return false
        }
      }
    }
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
            val hx = event.getHistoricalX(index, history)
            val hy = event.getHistoricalY(index, history)
            val hp = event.getHistoricalPressure(index, history)
            predictor.onMove(hx, hy, hp)
            addPoint(hx, hy, hp)
          }
          val x = event.getX(index)
          val y = event.getY(index)
          val p = event.getPressure(index)
          predictor.onMove(x, y, p)
          addPoint(x, y, p)
          updateShapeRecognition(x, y)
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
            cancelShapePreviewIfMoved(event.getX(index), event.getY(index))
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
        discardAreaErase()
        resetGesture(commitNativeAreaErase = false)
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

  override fun onHoverEvent(event: MotionEvent): Boolean {
    val pointerType = event.getToolType(0)
    if (inputEnabled && (pointerType == MotionEvent.TOOL_TYPE_ERASER ||
      (selectedTool == PenCanvasTool.ERASER && pointerType == MotionEvent.TOOL_TYPE_STYLUS))) {
      when (event.actionMasked) {
        MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> eraserPosition = event.x to event.y
        MotionEvent.ACTION_HOVER_EXIT -> eraserPosition = null
        else -> return super.onHoverEvent(event)
      }
      invalidate()
      return true
    }
    return super.onHoverEvent(event)
  }

  override fun performClick(): Boolean {
    super.performClick()
    return true
  }

  fun cancelActiveGesture() {
    clearLaserTrail()
    if (eraserPosition != null) {
      eraserPosition = null
      invalidate()
    }
    if (annotationSelectionPointerId != INVALID_POINTER) cancelAnnotationSelectionGesture()
    selectionPointerId = INVALID_POINTER
    annotationSelectionPointerId = INVALID_POINTER
    selectionLassoActive = false
    selectionLassoPoints.clear()
    if (activePointerId == INVALID_POINTER) {
      invalidate()
      return
    }
    discardAreaErase()
    resetGesture(commitNativeAreaErase = false)
    invalidate()
  }

  private fun cancelAnnotationSelectionGesture() {
    val now = android.os.SystemClock.uptimeMillis()
    val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
    try {
      onAnnotationSelectionTouch?.invoke(cancel, geometry(), 0)
    } finally {
      cancel.recycle()
    }
  }

  override fun onDetachedFromWindow() {
    discardAreaErase()
    cancelActiveGesture()
    cancelShapeDwell()
    strokeCache.clear()
    super.onDetachedFromWindow()
  }

  private fun discardAreaErase() {
    eraseOriginal?.let { original ->
      internalStrokes = original
      strokeCache.setStrokes(original)
      eraseOriginal = null
      lastErasePoint = null
    }
  }

  private fun clearLaserTrail() {
    shapeHandler.removeCallbacks(clearLaser)
    laserTrail = emptyList()
    invalidate()
  }

  private fun beginGesture(event: MotionEvent, pointerIndex: Int): Boolean {
    if (!isSupportedTool(event.getToolType(pointerIndex))) return false
    clearLaserTrail()
    activePointerId = event.getPointerId(pointerIndex)
    activePointerToolType = event.getToolType(pointerIndex)
    requestUnbufferedDispatch(event)
    predictor.onDown()
    val eraserInput = event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_ERASER ||
      (event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_STYLUS &&
        hasEraserButton(event.buttonState))
    eraseModeLatched = eraserInput || selectedTool == PenCanvasTool.ERASER
    gestureTool = if (eraseModeLatched) PenCanvasTool.ERASER else selectedTool
    shapeRecognitionCancelled = activePointerToolType != MotionEvent.TOOL_TYPE_STYLUS ||
      gestureTool != PenCanvasTool.PEN
    shapePreview = null
    shapeDwellAnchor = null
    referenceShapeSampler.begin(event.getX(pointerIndex), event.getY(pointerIndex))
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
        geometry(),
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
        if (onAnnotationSelectionTouch?.invoke(event, geometry(), pointerIndex) == true) {
          annotationSelectionPointerId = event.getPointerId(pointerIndex)
          selectionPointerId = annotationSelectionPointerId
          selectedIndex = null
          requestUnbufferedDispatch(event)
          invalidate()
          return true
        }
        selectionPointerId = event.getPointerId(pointerIndex)
        requestUnbufferedDispatch(event)
        val radius = 18f * resources.displayMetrics.density
        val nextSelection = geometry()
          .hitTest(internalStrokes, event.getX(pointerIndex), event.getY(pointerIndex), radius)
        selectionLassoPoints.clear()
        selectionLassoActive = nextSelection == null
        if (selectionLassoActive) {
          selectedIndex = null
          onSelectionChanged?.invoke(null)
          addSelectionLassoPoint(event.getX(pointerIndex), event.getY(pointerIndex))
        } else if (selectedIndex != nextSelection) {
          selectedIndex = nextSelection
          onSelectionChanged?.invoke(nextSelection)
        }
        invalidate()
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        if (selectionPointerId == INVALID_POINTER) return false
        if (selectionLassoActive) {
          val index = event.findPointerIndex(selectionPointerId)
          if (index >= 0) {
            for (history in 0 until event.historySize) {
              addSelectionLassoPoint(event.getHistoricalX(index, history), event.getHistoricalY(index, history))
            }
            addSelectionLassoPoint(event.getX(index), event.getY(index))
            invalidate()
          }
        }
        return true
      }
      MotionEvent.ACTION_POINTER_DOWN -> return selectionPointerId != INVALID_POINTER
      MotionEvent.ACTION_UP -> {
        if (selectionPointerId == INVALID_POINTER) return false
        if (selectionLassoActive) {
          val index = event.findPointerIndex(selectionPointerId)
          if (index >= 0) addSelectionLassoPoint(event.getX(index), event.getY(index))
          val minimumBoundsSize = geometry().documentUnitsForPixels(2f * resources.displayMetrics.density)
          PenSelectionRegion.create(
            selectionLassoPoints,
            minimumWidth = minimumBoundsSize.toDouble(),
            minimumHeight = minimumBoundsSize.toDouble(),
          )?.let { onSelectionRegionCommitted?.invoke(it) }
          selectionLassoActive = false
          selectionLassoPoints.clear()
          invalidate()
        }
        selectionPointerId = INVALID_POINTER
        performClick()
        return true
      }
      MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
        val hadSelectionGesture = selectionPointerId != INVALID_POINTER
        onAnnotationSelectionTouch?.invoke(event, geometry(), event.actionIndex)
        selectionPointerId = INVALID_POINTER
        selectionLassoActive = false
        selectionLassoPoints.clear()
        invalidate()
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
    drawSelectionBounds(canvas, minX - inset, minY - inset, maxX + inset, maxY + inset)
  }

  private fun drawSelectionBounds(canvas: Canvas, strokes: List<PenStroke>) {
    val points = strokes.flatMap { it.points }
    if (points.isEmpty()) return
    val inset = (strokes.maxOfOrNull { it.width.toFloat() / 2f } ?: 0f) + SELECTION_INSET
    drawSelectionBounds(
      canvas,
      points.minOf { it.x } - inset,
      points.minOf { it.y } - inset,
      points.maxOf { it.x } + inset,
      points.maxOf { it.y } + inset,
    )
  }

  private fun drawSelectionBounds(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
    val minimumSize = SELECTION_MIN_SIZE
    val adjustedLeft = if (right - left < minimumSize) left - (minimumSize - (right - left)) / 2f else left
    val adjustedTop = if (bottom - top < minimumSize) top - (minimumSize - (bottom - top)) / 2f else top
    val adjustedRight = if (right - left < minimumSize) right + (minimumSize - (right - left)) / 2f else right
    val adjustedBottom = if (bottom - top < minimumSize) bottom + (minimumSize - (bottom - top)) / 2f else bottom
    canvas.drawRect(adjustedLeft, adjustedTop, adjustedRight, adjustedBottom, selectionPaint)
  }

  private fun addSelectionLassoPoint(xPx: Float, yPx: Float) {
    val point = geometry().toDocumentPoint(xPx, yPx, 1f, clampToPage = false) ?: return
    if (selectionLassoPoints.lastOrNull()?.let { it.x == point.x && it.y == point.y } != true) {
      selectionLassoPoints += point
    }
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
      clearShapeRecognition(cancelled = true)
      activePoints.clear()
      gestureTool = nextTool
    }
  }

  private fun updateShapeRecognition(xPx: Float, yPx: Float) {
    if (shapeRecognitionCancelled || activePointerToolType != MotionEvent.TOOL_TYPE_STYLUS ||
      gestureTool != PenCanvasTool.PEN || selectedTool != PenCanvasTool.PEN
    ) {
      return
    }
    val anchor = shapeDwellAnchor
    if (shapePreview != null) {
      if (anchor != null && distancePx(anchor, xPx, yPx) > stationaryRadiusPx()) {
        clearShapeRecognition(cancelled = true)
        invalidate()
      }
      return
    }
    val sampled = referenceShapeSampler.move(xPx, yPx, 5f * resources.displayMetrics.density)
    if (sampled) {
      // GraphUtils applies its minimums in sampled screen coordinates per shape branch. A
      // generic document-space path-length gate here incorrectly rejects short valid lines.
      cancelShapeDwell()
      shapeDwellAnchor = xPx to yPx
      val task = Runnable {
        shapeDwellTask = null
        if (inputEnabled && activePointerId != INVALID_POINTER && !shapeRecognitionCancelled &&
          activePointerToolType == MotionEvent.TOOL_TYPE_STYLUS &&
          gestureTool == PenCanvasTool.PEN && selectedTool == PenCanvasTool.PEN
        ) {
          val generation = shapeGeneration.get()
          val points = activePoints.toList()
          val screenPoints = referenceShapeSampler.points()
          val canvasGeometry = geometry()
          val density = resources.displayMetrics.density
          val recognitionStrokeWidth = activeWidth.toFloat() * 1.3f
          val recognize = shapeRecognizer
          shapeExecutor.execute {
            if (generation != shapeGeneration.get()) return@execute
            val result = runCatching {
              if (recognize != null) recognize(points)
              else ReferenceOpenShapeRecognizer.recognize(
                screenPoints,
                canvasGeometry,
                density,
                strokeWidth = recognitionStrokeWidth,
              )
                ?: ReferenceClosedShapeRecognizer.recognize(screenPoints, canvasGeometry, density)
            }.getOrNull()
            shapeHandler.post {
              if (generation == shapeGeneration.get() && inputEnabled && activePointerId != INVALID_POINTER &&
                !shapeRecognitionCancelled && activePointerToolType == MotionEvent.TOOL_TYPE_STYLUS &&
                gestureTool == PenCanvasTool.PEN && selectedTool == PenCanvasTool.PEN
              ) {
                shapePreview = result
                invalidate()
              }
            }
          }
        }
      }
      shapeDwellTask = task
      shapeHandler.postDelayed(task, SHAPE_DWELL_MS)
    }
  }

  private fun cancelShapePreviewIfMoved(xPx: Float, yPx: Float) {
    val anchor = shapeDwellAnchor ?: return
    if (shapePreview != null && distancePx(anchor, xPx, yPx) > stationaryRadiusPx()) {
      clearShapeRecognition(cancelled = true)
    }
  }

  private fun clearShapeRecognition(cancelled: Boolean) {
    cancelShapeDwell()
    shapeDwellAnchor = null
    shapePreview = null
    shapeRecognitionCancelled = cancelled
  }

  private fun cancelShapeDwell() {
    shapeDwellTask?.let(shapeHandler::removeCallbacks)
    shapeDwellTask = null
    shapeGeneration.incrementAndGet()
  }

  private fun stationaryRadiusPx() = 4f * resources.displayMetrics.density

  private fun distancePx(anchor: Pair<Float, Float>, x: Float, y: Float) =
    hypot(anchor.first - x, anchor.second - y)

  private fun addPoint(xPx: Float, yPx: Float, pressure: Float) {
    val point = geometry()
      .toDocumentPoint(xPx, yPx, (gestureTool ?: selectedTool).pointPressure(pressure)) ?: return
    if (activePoints.lastOrNull() != point) activePoints.add(point)
  }

  private fun eraseAt(xPx: Float, yPx: Float) {
    eraserPosition = xPx to yPx
    invalidate()
    if (onAreaEraseCommitted == null) {
      val index = geometry().hitTest(internalStrokes, xPx, yPx, eraserRadiusPx) ?: return
      internalStrokes = internalStrokes.toMutableList().also { it.removeAt(index) }
      strokeCache.setStrokes(internalStrokes)
      onStrokeErased?.invoke(index)
      invalidate()
      return
    }
    val geometry = geometry()
    val point = geometry.toDocumentPoint(xPx, yPx, 1f) ?: run { lastErasePoint = null; return }
    val radius = geometry.documentUnitsForPixels(eraserRadiusPx)
    if (!radius.isFinite()) return
    onNativeAreaEraseSegment?.invoke(lastErasePoint ?: point, point, radius)
    val next = PenAreaEraser.erase(internalStrokes, lastErasePoint ?: point, point, radius)
    lastErasePoint = point
    if (next != internalStrokes) {
      if (eraseOriginal == null) eraseOriginal = internalStrokes
      internalStrokes = next
      strokeCache.setStrokes(next)
      selectedIndex = null
      onSelectionChanged?.invoke(null)
      invalidate()
    }
  }

  private fun finishStroke() {
    if (activePoints.isEmpty()) return
    val tool = gestureTool ?: return
    if (tool == PenCanvasTool.LASER) {
      laserTrail = activePoints.toList()
      laserColor = activeColor
      laserWidth = activeWidth
      shapeHandler.postDelayed(clearLaser, 750L)
      return
    }
    val stroke = PenStroke(
      type = tool.persistedType ?: return,
      color = activeColor,
      width = activeWidth,
      points = activePoints.toList(),
    )
    PenValidation.validate(stroke, 1)
    val shape = shapePreview
    if (shape?.type == "polyline" || shape?.type == "curve" || shape?.type == "closedPath") {
      val field = if (shape.type == "curve") PenStroke.CURVE_FIELD else PenStroke.POLYLINE_FIELD
      val segmentPath = stroke.copy(points = shape.points, extraFields = mapOf(field to true))
      PenValidation.validate(segmentPath, 1)
      internalStrokes = internalStrokes + segmentPath
      strokeCache.appendStroke(segmentPath, geometry())
      onStrokeAdded?.invoke(segmentPath)
      return
    }
    val consumed = shape != null && runCatching {
      onShapeRecognized?.invoke(shape, stroke) == true
    }.getOrDefault(false)
    if (!consumed) {
      internalStrokes = internalStrokes + stroke
      strokeCache.appendStroke(stroke, geometry())
      onStrokeAdded?.invoke(stroke)
    }
  }

  private fun resetGesture(commitNativeAreaErase: Boolean = true) {
    if (eraseOriginal != null) onAreaEraseCommitted?.invoke(internalStrokes.toList())
    onNativeAreaEraseFinished?.invoke(commitNativeAreaErase)
    eraseOriginal = null
    lastErasePoint = null
    predictor.onUp()
    cancelShapeDwell()
    activePointerId = INVALID_POINTER
    activePointerToolType = MotionEvent.TOOL_TYPE_UNKNOWN
    gestureTool = null
    eraseModeLatched = false
    eraserPosition = null
    activePoints.clear()
    liveStrokeCache.clear()
    shapePreview = null
    shapeDwellAnchor = null
    shapeRecognitionCancelled = false
  }

  private fun predictedDocumentPoint(): PenPoint? {
    if (shapePreview != null) return null
    val tool = gestureTool ?: return null
    if (tool != PenCanvasTool.PEN && tool != PenCanvasTool.BALLPOINT &&
      tool != PenCanvasTool.HIGHLIGHTER
    ) {
      return null
    }
    if (activePointerId == INVALID_POINTER || activePoints.isEmpty()) return null
    val prediction = predictor.predict() ?: return null
    return geometry().toDocumentPoint(
      prediction.x,
      prediction.y,
      activePoints.last().pressure,
    )
  }

  private fun hasEraserButton(buttonState: Int): Boolean = buttonState and ERASER_BUTTONS != 0

  private fun geometry(): PenCanvasGeometry {
    val current = cachedGeometry
    if (current != null && cachedGeometryWidth == width && cachedGeometryHeight == height &&
      cachedGeometryTransform == pageTransform
    ) return current
    return PenCanvasGeometry(width.toFloat(), height.toFloat(), pageTransform).also {
      cachedGeometry = it
      cachedGeometryWidth = width
      cachedGeometryHeight = height
      cachedGeometryTransform = pageTransform
    }
  }

  private companion object {
    // A cancelled recognition may still be executing; let the next canvas
    // progress without creating an unbounded number of worker threads.
    val shapeExecutor = Executors.newFixedThreadPool(2) { task -> Thread(task, "pen-shape-recognition").apply { isDaemon = true } }
    const val INVALID_POINTER = -1
    const val ERASER_BUTTONS = MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_STYLUS_SECONDARY
    const val SELECTION_INSET = 8f
    const val SELECTION_MIN_SIZE = 24f
    // TouchEventManager.TIME_PAINT_SHAPE_AUTO in the supplied reference.
    const val SHAPE_DWELL_MS = 400L
  }
}
