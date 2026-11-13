package io.pagenote.android

import android.graphics.Bitmap
import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.pagenote.feature.audio.AudioCapsule
import io.pagenote.feature.pen.PenCanvasGeometry
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenPageStore
import io.pagenote.feature.pen.PenSide
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenRecognizedShape
import io.pagenote.feature.pen.PenSelectionRegion
import io.pagenote.feature.pen.PenStroke
import io.pagenote.feature.pen.PenStyle
import io.pagenote.core.reader.PdfPageText
import io.pagenote.core.reader.PdfTextSelection
import kotlinx.serialization.json.JsonArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import java.util.concurrent.atomic.AtomicReference

internal suspend fun <T : Any> withCancellationSafeContextResult(
  context: CoroutineContext,
  dispose: (T) -> Unit,
  block: suspend () -> T,
): T {
  val pendingResult = AtomicReference<T?>()
  try {
    val result = withContext(context) {
      block().also(pendingResult::set)
    }
    pendingResult.compareAndSet(result, null)
    return result
  } finally {
    pendingResult.getAndSet(null)?.let(dispose)
  }
}

@Composable
internal fun PdfContinuousPageContent(
  renderer: PdfDocumentSession,
  pageIndex: Int,
  renderMutex: Mutex,
  modifier: Modifier = Modifier,
  content: @Composable (pageSize: PdfPageSize, bitmap: Bitmap?, loading: Boolean) -> Unit,
) {
  val pageSize = remember(renderer, pageIndex) { renderer.pageSize(pageIndex) }
  var bitmap by remember(renderer, pageIndex) { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(renderer, pageIndex, renderMutex) {
    try {
      val rendered = withCancellationSafeContextResult(
        context = Dispatchers.IO,
        dispose = Bitmap::recycleAfterDrawFrames,
      ) {
        renderMutex.withLock {
          currentCoroutineContext().ensureActive()
          renderer.render(pageIndex)
        }
      }
      bitmap = rendered
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      // Keep the page placeholder in its measured position if this page cannot render.
    }
  }

  DisposableEffect(bitmap) {
    val rendered = bitmap
    onDispose { rendered?.recycleAfterDrawFrames() }
  }

  BoxWithConstraints(modifier.fillMaxWidth()) {
    val pageHeight = if (pageSize.widthPx > 0) {
      maxWidth * (pageSize.heightPx.toFloat() / pageSize.widthPx.toFloat())
    } else {
      1.dp
    }
    Box(Modifier.fillMaxWidth().height(pageHeight)) {
      content(pageSize, bitmap, bitmap == null)
    }
  }
}

internal class PdfContinuousReaderPageState(
  val context: Context,
  val renderer: PdfDocumentSession,
  val project: OpenedProject,
  val activePageIndex: Int,
  val request: PdfPageNavigationRequest,
  val renderMutex: Mutex,
  val penPageStore: PenPageStore,
  val annotationReload: Int,
  val penSaveMutex: Mutex,
  val richHtmlRenderer: RichHtmlAnnotationRenderer,
  val pendingRecognizedShapes: RecognizedShapePreviews,
  val annotationPreview: Pair<Int, PdfAnnotationOverlayItem>?,
  val selectedAnnotationBounds: PdfAnnotationBounds?,
  val selectedAnnotationResizable: Boolean,
  val penTool: PenCanvasTool,
  val penWritable: Boolean,
  val inputEnabled: Boolean,
  val gesturesEnabled: Boolean,
  val annotateMode: Boolean,
  val annotationDrawTool: PdfAnnotationDrawTool?,
  val annotationSaving: Boolean,
  val selectedPenIndex: Int?,
  val selectedPenIndices: Set<Int>,
  val selectedStrokeBoundsVisible: Boolean,
  val transform: PdfViewportTransform,
  val viewportBookmark: PdfViewportBookmark,
  val selectionEnabled: Boolean,
  val textSelectionEnabled: Boolean,
  val pageText: PdfPageText?,
  val textSelection: PdfTextSelection?,
  val selection: NormalizedOcrRegion?,
  val focusRect: NormalizedOcrRegion?,
  val audioCapsulesForPage: (Int) -> List<AudioCapsule>,
  val activeAudioCapsuleId: String?,
  val audioCapsuleTapEnabled: Boolean,
  val allowInkOutsidePage: Boolean,
  val penStyle: PenStyle,
)

internal interface PdfContinuousReaderPageEvent {
  data class ActivePageChanged(val page: Int) : PdfContinuousReaderPageEvent
  data class StrokeAdded(val page: Int, val stroke: PenStroke) : PdfContinuousReaderPageEvent
  data class ShapeRecognized(val page: Int, val shape: PenRecognizedShape, val stroke: PenStroke) : PdfContinuousReaderPageEvent
  data class StrokeErased(val page: Int, val index: Int) : PdfContinuousReaderPageEvent
  data class AreaEraseCommitted(val page: Int, val items: List<PenStroke>) : PdfContinuousReaderPageEvent
  data class NativeAreaEraseSegment(val page: Int, val start: PenPoint, val end: PenPoint, val width: Float) : PdfContinuousReaderPageEvent
  data class NativeAreaEraseFinished(val page: Int, val changed: Boolean) : PdfContinuousReaderPageEvent
  data class SelectedStrokeChanged(val page: Int, val selected: Int?) : PdfContinuousReaderPageEvent
  data class SelectionRegionCommitted(val page: Int, val region: PenSelectionRegion) : PdfContinuousReaderPageEvent
  data class AnnotationSelectionTouch(val page: Int, val event: MotionEvent, val geometry: PenCanvasGeometry, val pointer: Int) : PdfContinuousReaderPageEvent
  data class TransformChanged(val transform: PdfViewportTransform, val bounds: PdfViewportBounds) : PdfContinuousReaderPageEvent
  data class BoundsChanged(val page: Int, val bounds: PdfViewportBounds) : PdfContinuousReaderPageEvent
  data class TextSelectionChanged(val page: Int, val selection: PdfTextSelection?) : PdfContinuousReaderPageEvent
  data class SelectionChanged(val page: Int, val selection: NormalizedOcrRegion?) : PdfContinuousReaderPageEvent
  data class AudioCapsuleTap(val page: Int, val tap: PdfSelectionPoint) : PdfContinuousReaderPageEvent
  data class AnnotateTap(val page: Int, val tap: PdfSelectionPoint, val bounds: PdfViewportBounds, val transform: PdfViewportTransform) : PdfContinuousReaderPageEvent
  data class AnnotationDrag(val page: Int, val start: PdfSelectionPoint, val end: PdfSelectionPoint, val finished: Boolean) : PdfContinuousReaderPageEvent
}

internal class PdfContinuousReaderPageCallbacks(
  val dispatch: (PdfContinuousReaderPageEvent) -> Boolean,
)

@Composable
internal fun PdfContinuousReaderPane(
  pageStateProvider: () -> PdfContinuousReaderPageState?,
  pageCallbacks: PdfContinuousReaderPageCallbacks?,
  modifier: Modifier = Modifier,
) {
  val state = pageStateProvider() ?: return
  val callbacks = pageCallbacks ?: return
  PdfContinuousPageList(
    pageCount = state.renderer.pageCount,
    request = state.request,
    onActivePageChanged = { page -> callbacks.dispatch(PdfContinuousReaderPageEvent.ActivePageChanged(page)) },
    modifier = modifier,
    pageContent = { pageIndex ->
      PdfContinuousReaderPage(state, callbacks, pageIndex)
    },
  )
}

@Composable
private fun PdfContinuousReaderPage(
  state: PdfContinuousReaderPageState,
  callbacks: PdfContinuousReaderPageCallbacks,
  pageIndex: Int,
) {
  val targetPage = pageIndex + 1
  val active = pageIndex == state.activePageIndex
  var annotationLoad by remember(state.renderer, pageIndex) {
    mutableStateOf(AnnotationOverlayLoad(JsonArray(emptyList()), emptyList(), emptyMap(), emptyMap()))
  }
  var viewportBounds by remember(state.renderer, pageIndex) { mutableStateOf(PdfViewportBounds.ZERO) }

  LaunchedEffect(
    state.project.manifest.id,
    state.project.sourceDirectoryUri,
    state.project.annotationsUri,
    state.renderer,
    targetPage,
    state.annotationReload,
  ) {
    val loaded = withCancellationSafeContextResult(
      context = Dispatchers.IO,
      dispose = AnnotationOverlayLoad::recycleBitmaps,
    ) {
      runCatching {
        loadPdfAnnotationOverlay(
          context = state.context,
          project = state.project,
          page = targetPage,
          penSaveMutex = state.penSaveMutex,
          richHtmlRenderer = state.richHtmlRenderer,
        )
      }.getOrElse {
        if (it is CancellationException) throw it
        AnnotationOverlayLoad(JsonArray(emptyList()), emptyList(), emptyMap(), emptyMap())
      }
    }
    val previous = annotationLoad
    annotationLoad = loaded
    previous.images.values.forEach(Bitmap::recycleAfterDrawFrames)
    previous.htmlBitmaps.values.forEach(Bitmap::recycleAfterDrawFrames)
  }

  DisposableEffect(annotationLoad) {
    val loaded = annotationLoad
    onDispose {
      loaded.images.values.forEach(Bitmap::recycleAfterDrawFrames)
      loaded.htmlBitmaps.values.forEach(Bitmap::recycleAfterDrawFrames)
    }
  }

  val annotations = annotationLoad.items + state.pendingRecognizedShapes.forPage(targetPage) +
    listOfNotNull(state.annotationPreview?.takeIf { it.first == targetPage }?.second)
  val strokes = state.penPageStore.getItems(PenSide.DOCUMENT, targetPage)
  val pageTransform = state.viewportBookmark.toTransform(viewportBounds)

  PdfContinuousPageContent(
    renderer = state.renderer,
    pageIndex = pageIndex,
    renderMutex = state.renderMutex,
    modifier = Modifier.fillMaxWidth(),
  ) { _, bitmap, loading ->
    PdfPageViewport(
      bitmap = bitmap,
      sourceName = state.project.sourceName,
      page = pageIndex,
      documentKey = state.project.sourceUri.toString(),
      annotations = annotations,
      annotationImages = annotationLoad.images,
      annotationHtmlBitmaps = annotationLoad.htmlBitmaps,
      selectedAnnotationBounds = state.selectedAnnotationBounds.takeIf { active },
      selectedAnnotationResizable = state.selectedAnnotationResizable && active,
      loading = loading,
      strokes = strokes,
      tool = state.penTool,
      inputEnabled = state.inputEnabled && (active || state.penTool != PenCanvasTool.SELECT),
      stylusOnlyInput = true,
      onStrokeAdded = { stroke -> callbacks.dispatch(PdfContinuousReaderPageEvent.StrokeAdded(targetPage, stroke)) },
      onShapeRecognized = { shape, stroke -> callbacks.dispatch(PdfContinuousReaderPageEvent.ShapeRecognized(targetPage, shape, stroke)) },
      onStrokeErased = { index -> callbacks.dispatch(PdfContinuousReaderPageEvent.StrokeErased(targetPage, index)) },
      onAreaEraseCommitted = { _, items -> callbacks.dispatch(PdfContinuousReaderPageEvent.AreaEraseCommitted(targetPage, items)) },
      onNativeAreaEraseSegment = { start, end, width ->
        callbacks.dispatch(PdfContinuousReaderPageEvent.NativeAreaEraseSegment(targetPage, start, end, width))
      },
      onNativeAreaEraseFinished = { changed -> callbacks.dispatch(PdfContinuousReaderPageEvent.NativeAreaEraseFinished(targetPage, changed)) },
      selectedStrokeIndex = state.selectedPenIndex.takeIf { active },
      selectedStrokeIndices = state.selectedPenIndices.takeIf { active }.orEmpty(),
      selectedStrokeBoundsVisible = state.selectedStrokeBoundsVisible,
      onSelectedStrokeChanged = { selected -> callbacks.dispatch(PdfContinuousReaderPageEvent.SelectedStrokeChanged(targetPage, selected)) },
      onSelectionRegionCommitted = { region -> callbacks.dispatch(PdfContinuousReaderPageEvent.SelectionRegionCommitted(targetPage, region)) },
      onAnnotationSelectionTouch = { event, geometry, pointer ->
        callbacks.dispatch(PdfContinuousReaderPageEvent.AnnotationSelectionTouch(targetPage, event, geometry, pointer))
      },
      transform = pageTransform,
      onTransformChanged = { transform -> callbacks.dispatch(PdfContinuousReaderPageEvent.TransformChanged(transform, viewportBounds)) },
      onBoundsChanged = { bounds ->
        viewportBounds = bounds
        callbacks.dispatch(PdfContinuousReaderPageEvent.BoundsChanged(targetPage, bounds))
      },
      pageText = state.pageText.takeIf { active },
      textSelectionEnabled = state.textSelectionEnabled && active,
      textSelection = state.textSelection.takeIf { active },
      onTextSelectionChanged = { selection -> callbacks.dispatch(PdfContinuousReaderPageEvent.TextSelectionChanged(targetPage, selection)) },
      selectionEnabled = state.selectionEnabled && active,
      gesturesEnabled = state.gesturesEnabled,
      selection = state.selection.takeIf { active },
      onSelectionChanged = { selection -> callbacks.dispatch(PdfContinuousReaderPageEvent.SelectionChanged(targetPage, selection)) },
      focusRect = state.focusRect.takeIf { active },
      audioCapsules = state.audioCapsulesForPage(targetPage),
      activeAudioCapsuleId = state.activeAudioCapsuleId,
      audioCapsuleTapEnabled = state.audioCapsuleTapEnabled,
      onAudioCapsuleTap = { tap -> callbacks.dispatch(PdfContinuousReaderPageEvent.AudioCapsuleTap(targetPage, tap)) },
      annotateTapEnabled = state.annotateMode && active,
      onAnnotateTap = { tap ->
        callbacks.dispatch(PdfContinuousReaderPageEvent.AnnotateTap(targetPage, tap, viewportBounds, pageTransform))
      },
      annotationDrawTool = state.annotationDrawTool.takeIf { active && !state.annotationSaving },
      onAnnotationDrag = { start, end, finished ->
        callbacks.dispatch(PdfContinuousReaderPageEvent.AnnotationDrag(targetPage, start, end, finished))
      },
      allowInkOutsidePage = state.allowInkOutsidePage,
      penStyle = state.penStyle,
      modifier = Modifier.fillMaxSize(),
    )
  }
}

private fun AnnotationOverlayLoad.recycleBitmaps() {
  images.values.forEach(Bitmap::recycleAfterDrawFrames)
  htmlBitmaps.values.forEach(Bitmap::recycleAfterDrawFrames)
}
