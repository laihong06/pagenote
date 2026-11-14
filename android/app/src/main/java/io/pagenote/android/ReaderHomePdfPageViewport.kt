package io.pagenote.android

import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.pagenote.core.reader.PdfPageText
import io.pagenote.core.reader.PdfTextSelection
import io.pagenote.feature.audio.AudioCapsule
import io.pagenote.feature.pen.PenCanvasGeometry
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenRecognizedShape
import io.pagenote.feature.pen.PenSelectionRegion
import io.pagenote.feature.pen.PenStroke
import io.pagenote.feature.pen.PenStyle

internal class ReaderHomePdfPageViewportState(
  val bitmap: Bitmap?,
  val sourceName: String,
  val documentKey: String,
  val page: Int,
  val annotations: List<PdfAnnotationOverlayItem>,
  val selectedAnnotationBounds: PdfAnnotationBounds?,
  val selectedAnnotationResizable: Boolean,
  val annotationImages: Map<String, Bitmap>,
  val annotationHtmlBitmaps: Map<Int, Bitmap>,
  val loading: Boolean,
  val strokes: List<PenStroke>,
  val tool: PenCanvasTool,
  val inputEnabled: Boolean,
  val selectedStrokeIndex: Int?,
  val selectedStrokeIndices: Set<Int>,
  val selectedStrokeBoundsVisible: Boolean,
  val transform: PdfViewportTransform,
  val selectionEnabled: Boolean,
  val textSelectionEnabled: Boolean,
  val pageText: PdfPageText?,
  val textSelection: PdfTextSelection?,
  val gesturesEnabled: Boolean,
  val allowInkOutsidePage: Boolean,
  val pageSwipesEnabled: Boolean,
  val selection: NormalizedOcrRegion?,
  val focusRect: NormalizedOcrRegion?,
  val audioCapsules: List<AudioCapsule>,
  val activeAudioCapsuleId: String?,
  val audioCapsuleTapEnabled: Boolean,
  val annotateTapEnabled: Boolean,
  val annotationDrawTool: PdfAnnotationDrawTool?,
  val penStyle: PenStyle,
)

internal class ReaderHomePdfPageViewportCallbacks(
  val onStrokeAdded: (PenStroke) -> Unit,
  val onShapeRecognized: (PenRecognizedShape, PenStroke) -> Boolean,
  val onStrokeErased: (Int) -> Unit,
  val onAreaEraseCommitted: (Int, List<PenStroke>) -> Unit,
  val onNativeAreaEraseSegment: (PenPoint, PenPoint, Float) -> Unit,
  val onNativeAreaEraseFinished: (Boolean) -> Unit,
  val onSelectedStrokeChanged: (Int?) -> Unit,
  val onSelectionRegionCommitted: (PenSelectionRegion) -> Unit,
  val onAnnotationSelectionTouch: (MotionEvent, PenCanvasGeometry, Int) -> Boolean,
  val onTransformChanged: (PdfViewportTransform) -> Unit,
  val onBoundsChanged: (PdfViewportBounds) -> Unit,
  val onTextSelectionChanged: (PdfTextSelection?) -> Unit,
  val onPageSwipe: (PdfPageSwipeDirection) -> Unit,
  val onSelectionChanged: (NormalizedOcrRegion?) -> Unit,
  val onAudioCapsuleTap: (PdfSelectionPoint) -> Unit,
  val onAnnotateTap: (PdfSelectionPoint) -> Unit,
  val onAnnotationDrag: (PdfSelectionPoint, PdfSelectionPoint, Boolean) -> Unit,
)

@Composable
internal fun ReaderHomePdfPageViewport(
  pageState: ReaderHomePdfPageViewportState?,
  callbacks: ReaderHomePdfPageViewportCallbacks,
  modifier: Modifier = Modifier,
) {
  val state = pageState ?: return
  PdfPageViewport(
    bitmap = state.bitmap,
    sourceName = state.sourceName,
    documentKey = state.documentKey,
    page = state.page,
    annotations = state.annotations,
    selectedAnnotationBounds = state.selectedAnnotationBounds,
    selectedAnnotationResizable = state.selectedAnnotationResizable,
    annotationImages = state.annotationImages,
    annotationHtmlBitmaps = state.annotationHtmlBitmaps,
    loading = state.loading,
    strokes = state.strokes,
    tool = state.tool,
    inputEnabled = state.inputEnabled,
    onStrokeAdded = callbacks.onStrokeAdded,
    onShapeRecognized = callbacks.onShapeRecognized,
    onStrokeErased = callbacks.onStrokeErased,
    onAreaEraseCommitted = callbacks.onAreaEraseCommitted,
    onNativeAreaEraseSegment = callbacks.onNativeAreaEraseSegment,
    onNativeAreaEraseFinished = callbacks.onNativeAreaEraseFinished,
    selectedStrokeIndex = state.selectedStrokeIndex,
    selectedStrokeIndices = state.selectedStrokeIndices,
    onSelectedStrokeChanged = callbacks.onSelectedStrokeChanged,
    onSelectionRegionCommitted = callbacks.onSelectionRegionCommitted,
    selectedStrokeBoundsVisible = state.selectedStrokeBoundsVisible,
    onAnnotationSelectionTouch = callbacks.onAnnotationSelectionTouch,
    transform = state.transform,
    onTransformChanged = callbacks.onTransformChanged,
    onBoundsChanged = callbacks.onBoundsChanged,
    selectionEnabled = state.selectionEnabled,
    textSelectionEnabled = state.textSelectionEnabled,
    pageText = state.pageText,
    textSelection = state.textSelection,
    onTextSelectionChanged = callbacks.onTextSelectionChanged,
    gesturesEnabled = state.gesturesEnabled,
    allowInkOutsidePage = state.allowInkOutsidePage,
    pageSwipesEnabled = state.pageSwipesEnabled,
    onPageSwipe = callbacks.onPageSwipe,
    selection = state.selection,
    onSelectionChanged = callbacks.onSelectionChanged,
    focusRect = state.focusRect,
    audioCapsules = state.audioCapsules,
    activeAudioCapsuleId = state.activeAudioCapsuleId,
    audioCapsuleTapEnabled = state.audioCapsuleTapEnabled,
    onAudioCapsuleTap = callbacks.onAudioCapsuleTap,
    annotateTapEnabled = state.annotateTapEnabled,
    onAnnotateTap = callbacks.onAnnotateTap,
    annotationDrawTool = state.annotationDrawTool,
    onAnnotationDrag = callbacks.onAnnotationDrag,
    penStyle = state.penStyle,
    modifier = modifier,
  )
}
