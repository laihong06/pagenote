package io.pagenote.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import io.pagenote.agent.MissingModelConfigException
import io.pagenote.agent.OkHttpChatClient
import io.pagenote.agent.PageRequest
import io.pagenote.agent.PageTutor
import io.pagenote.agent.PageTutorRequest
import io.pagenote.agent.PageTutorResult
import io.pagenote.agent.AgentEvent
import io.pagenote.agent.AgentRunner
import io.pagenote.agent.LocalToolDispatcher
import io.pagenote.agent.ModelEndpoint
import io.pagenote.agent.SerialPages
import io.pagenote.core.project.PageNotes
import io.pagenote.core.project.NoteVersion
import io.pagenote.core.reader.PdfPageText
import io.pagenote.core.reader.PageRegion
import io.pagenote.core.reader.PdfTextSelection
import io.pagenote.core.reader.PdfTextSelectionMapper
import io.pagenote.feature.annotations.ReviewCreate
import io.pagenote.feature.notes.NotePage
import io.pagenote.feature.notes.PageNoteConflictException
import io.pagenote.feature.notes.PageNoteEditor
import io.pagenote.feature.notes.NoteDiagnostic
import io.pagenote.feature.notes.NoteRichContent
import io.pagenote.feature.ocr.OfflineOcrEngine
import io.pagenote.feature.ocr.OcrPolicy
import io.pagenote.feature.ocr.OcrResult
import io.pagenote.feature.ocr.OcrResultMapper
import io.pagenote.feature.ocr.OcrResultPolicy
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenCanvasGeometry
import io.pagenote.feature.pen.PenCanvasPageTransform
import io.pagenote.feature.pen.PenCanvasView
import io.pagenote.feature.pen.PenClipboardCodec
import io.pagenote.feature.pen.PenPageStore
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenRecognizedShape
import io.pagenote.feature.pen.PenSide
import io.pagenote.feature.pen.PenStyle
import io.pagenote.feature.pen.PenStroke
import io.pagenote.feature.pen.PenValidation
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val MAX_ANNOTATION_PAGE_IMAGE_PIXELS = 4_000_000L

private data class PdfAnnotationSelectionGesture(
  val sourceIndex: Int,
  val startX: Double,
  val startY: Double,
  val originalItem: JsonObject,
  val originalPage: JsonArray,
  val bounds: PdfAnnotationBounds,
  val resizing: Boolean,
  val moved: Boolean = false,
)

class MainActivity : ComponentActivity() {
  private lateinit var readerSession: ReaderSessionViewModel
  internal var readerShortcutHandler: ((KeyEvent) -> Boolean)? = null
  internal var markdownImagePickerForTesting: (((Uri?) -> Unit) -> Unit)? = null
  internal var pdfImagePickerForTesting: (((Uri?) -> Unit) -> Unit)? = null
  internal var noteExportPickerForTesting: ((String, String, (Uri?) -> Unit) -> Unit)? = null
  internal var noteImportPickerForTesting: ((Array<String>, (Uri?) -> Unit) -> Unit)? = null
  internal var newProjectDirectoryPickerForTesting: (((Uri?) -> Unit) -> Unit)? = null
  internal var newProjectPdfPickerForTesting: ((Array<String>, (Uri?) -> Unit) -> Unit)? = null
  internal var projectAttachmentPickerForTesting: ((Array<String>, (Uri?) -> Unit) -> Unit)? = null

  override fun onCreate(savedInstanceState: android.os.Bundle?) {
    super.onCreate(savedInstanceState)
    readerSession = ViewModelProvider(this)[ReaderSessionViewModel::class.java]
    readerSession.initializeRecovery(applicationContext)
    setContent {
      ReaderSettingsHost(readerSession)
    }
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (readerShortcutHandler?.invoke(event) == true) return true
    return super.dispatchKeyEvent(event)
  }

  override fun onSaveInstanceState(outState: android.os.Bundle) {
    readerSession.saveCheckpoint()
    super.onSaveInstanceState(outState)
  }

  override fun onStop() {
    readerSession.flushDraftRecovery()
    super.onStop()
  }
}

@Composable
private fun ReaderSettingsHost(readerSession: ReaderSessionViewModel) {
  val context = LocalContext.current
  val activityResultRegistryOwner = context as ActivityResultRegistryOwner
  val systemConfiguration = LocalConfiguration.current
  val settingsStore = remember(context.applicationContext) {
    readerSettingsStore(context.applicationContext)
  }
  val modelSettingsStore = remember(context.applicationContext) {
    agentModelSettingsStore(context.applicationContext)
  }
  var retryAttempt by remember { mutableIntStateOf(0) }
  val loadState by produceState<ReaderSettingsLoadState>(
    initialValue = ReaderSettingsLoadState.Loading,
    settingsStore,
    retryAttempt,
  ) {
    value = ReaderSettingsLoadState.Loading
    settingsStore.settings.asReaderSettingsLoadStates().collect { value = it }
  }
  val systemThemeMode = resolveReaderTheme("system", isSystemInDarkTheme())
  val settings = (loadState as? ReaderSettingsLoadState.Loaded)?.settings

  if (settings == null) {
    MaterialTheme(colorScheme = readerColorScheme(systemThemeMode)) {
      Surface(modifier = Modifier.fillMaxSize()) {
        ReaderSettingsLoadScreen(loadState) { retryAttempt += 1 }
      }
    }
    return
  }

  val systemLocale = systemConfiguration.locales.get(0) ?: Locale.getDefault()
  val locale = resolveReaderLocale(settings.language, systemLocale)
  val localizedConfiguration = remember(systemConfiguration, settings.language, locale) {
    Configuration(systemConfiguration).apply {
      if (settings.language != "system") {
        setLocale(locale)
        setLayoutDirection(locale)
      }
    }
  }
  val localizedContext = remember(context, localizedConfiguration) {
    context.createConfigurationContext(localizedConfiguration)
  }
  val themeMode = resolveReaderTheme(settings.theme, isSystemInDarkTheme())

  CompositionLocalProvider(
    LocalConfiguration provides localizedConfiguration,
    LocalContext provides localizedContext,
    LocalActivityResultRegistryOwner provides activityResultRegistryOwner,
  ) {
    MaterialTheme(colorScheme = readerColorScheme(themeMode)) {
      Surface(modifier = Modifier.fillMaxSize()) {
        ReaderHome(
          settings = settings,
          settingsStore = settingsStore,
          modelSettingsStore = modelSettingsStore,
          readerSession = readerSession,
        )
      }
    }
  }
}

private data class ReaderProjectData(
  val project: OpenedProject,
  val noteText: String,
  val renderer: PdfDocumentSession?,
  val markdownSource: String?,
  val noteWritable: Boolean,
  val penStore: ProjectPenStore,
  val penPageStore: PenPageStore,
  val penWritable: Boolean,
  val restoredPageIndex: Int,
  val checkpoint: ReaderSessionCheckpoint?,
  val draftRecovery: NoteDraftRecoveryRecord?,
  val missingAssets: List<String>,
)

private class PersistedProjectPermissionMissingException : SecurityException()

private data class PendingMarkdownImageInsertion(
  val project: OpenedProject,
  val projectId: String,
  val sourceDirectoryUri: Uri,
  val pageIndex: Int,
  val editor: EditText,
  val markdown: String,
  val selectionStart: Int,
  val selectionEnd: Int,
  val fallbackDisplayName: String? = null,
  val altTextOverride: String? = null,
)

private data class PendingPdfImageInsertion(
  val project: OpenedProject,
  val projectId: String,
  val sourceDirectoryUri: Uri,
  val renderer: PdfDocumentSession,
  val pageIndex: Int,
  val visibleTopY: Double,
  val style: PenStyle,
)

private data class PendingNoteImportContext(
  val projectId: String,
  val sourceDirectoryUri: Uri,
  val pageIndex: Int,
  val markdown: String,
  val selectionStart: Int,
  val selectionEnd: Int,
)

private data class PendingNoteImportBackup(
  val context: PendingNoteImportContext,
  val pages: Map<Int, String>,
)

private data class NoteImportWriteResult(
  val markdown: String,
  val recoveryCopyName: String?,
)

private data class AnnotationOverlayLoad(
  val page: JsonArray,
  val items: List<PdfAnnotationOverlayItem>,
  val images: Map<String, Bitmap>,
  val htmlBitmaps: Map<Int, Bitmap>,
)

internal fun Bitmap.recycleAfterDrawFrames() {
  if (isRecycled) return
  if (Looper.myLooper() != Looper.getMainLooper()) {
    if (!Handler(Looper.getMainLooper()).post { recycleAfterDrawFrames() } && !isRecycled) recycle()
    return
  }
  val choreographer = Choreographer.getInstance()
  var remainingFrames = 2
  val callback = object : Choreographer.FrameCallback {
    override fun doFrame(frameTimeNanos: Long) {
      remainingFrames--
      if (remainingFrames > 0) {
        choreographer.postFrameCallback(this)
      } else if (!isRecycled) {
        recycle()
      }
    }
  }
  choreographer.postFrameCallback(callback)
}

@Composable
private fun ReaderHome(
  settings: ReaderSettings,
  settingsStore: ReaderSettingsStore,
  modelSettingsStore: AgentModelSettingsStore,
  readerSession: ReaderSessionViewModel,
) {
  val context = LocalContext.current
  val appVersion = remember(context) {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
  }
  val systemClipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
  var clipboardRevision by remember { mutableIntStateOf(0) }
  DisposableEffect(systemClipboard) {
    val listener = ClipboardManager.OnPrimaryClipChangedListener { clipboardRevision++ }
    systemClipboard?.addPrimaryClipChangedListener(listener)
    onDispose { systemClipboard?.removePrimaryClipChangedListener(listener) }
  }
  val recentProjectStore = remember(context) { recentProjectsStore(context.applicationContext) }
  val recentProjects by produceState(
    initialValue = emptyList<RecentProjectEntry>(),
    key1 = recentProjectStore,
  ) {
    recentProjectStore.recentProjects.collect { value = it }
  }
  val densityScale = LocalDensity.current.density
  val activityResultOwner = LocalActivityResultRegistryOwner.current
  val scope = rememberCoroutineScope()
  val richHtmlRenderer = remember(context) { RichHtmlAnnotationRenderer(context) }
  DisposableEffect(richHtmlRenderer) {
    onDispose { richHtmlRenderer.close() }
  }
  val pageTutor = remember { PageTutor(OkHttpChatClient()) }
  val pagePrefetcher = remember { ReaderPagePrefetcher<PageTutorSourceText>() }
  val preparedOcrCache = remember(context) {
    ProjectPreparedCache(SafPreparedDocumentAccess(context.contentResolver))
  }
  var project by readerSession.project
  var renderer by readerSession.renderer
  var markdownSource by readerSession.markdownSource
  val isMarkdownReading = project != null && renderer == null && markdownSource != null
  var pageIndex by readerSession.pageIndex
  var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var viewportBounds by remember(project?.sourceUri, pageIndex) {
    mutableStateOf(PdfViewportBounds.ZERO)
  }
  val viewportTransform = readerSession.viewportBookmark.value.toTransform(viewportBounds)
  var notesMarkdown by readerSession.notesMarkdown
  val noteDraft by readerSession.noteDraft
  var noteBaseRevision by readerSession.noteBaseRevision
  var noteWritable by readerSession.noteWritable
  var noteStatus by readerSession.noteStatus
  val pendingNoteExport by readerSession.pendingNoteExport
  val isRestoringPendingNoteExport by readerSession.isRestoringPendingNoteExport
  val isHandlingNoteExportResult by readerSession.isHandlingNoteExportResult
  val isPendingNoteExportRecovered by readerSession.isPendingNoteExportRecovered
  var penStore by readerSession.penStore
  var penPageStore by readerSession.penPageStore
  var penStrokes by readerSession.penStrokes
  var penWritable by readerSession.penWritable
  var penTool by readerSession.penTool
  var selectedPenIndex by remember(project?.manifest?.id, project?.sourceDirectoryUri, pageIndex) {
    mutableStateOf<Int?>(null)
  }
  var penColor by rememberSaveable { mutableStateOf(PenStyle.DEFAULT.color) }
  var penWidth by rememberSaveable { mutableStateOf(PenStyle.DEFAULT.width) }
  var showPenColorPicker by remember { mutableStateOf(false) }
  var showPenWidthPicker by remember { mutableStateOf(false) }
  val penStyle = remember(penColor, penWidth) {
    PenStyle.create(penColor, penWidth) ?: PenStyle.DEFAULT
  }
  var penStatus by readerSession.penStatus
  var failedPenSave by readerSession.failedPenSave
  var pendingPenSaves by readerSession.pendingPenSaves
  val penSaveMutex = readerSession.penSaveMutex
  var ocrEngine by remember { mutableStateOf<OfflineOcrEngine?>(null) }
  var ocrResult by remember { mutableStateOf<OcrResult?>(null) }
  var ocrResultPage by remember { mutableIntStateOf(0) }
  var showOcrResult by remember { mutableStateOf(false) }
  var isOcrRunning by remember { mutableStateOf(false) }
  var readerOcrState by remember { mutableStateOf(ReaderOcrState()) }
  var pdfTextSelectionMode by remember { mutableStateOf(false) }
  var pdfTextSelection by remember { mutableStateOf<PdfTextSelection?>(null) }
  var pdfSelectionReviewDraft by remember { mutableStateOf<PdfSelectionReviewDraft?>(null) }
  var selectablePageText by remember(renderer, pageIndex) { mutableStateOf<PdfPageText?>(null) }
  var isSelectablePageTextLoading by remember(renderer, pageIndex) { mutableStateOf(false) }
  var regionOcrResult by remember { mutableStateOf<OcrResult?>(null) }
  var regionOcrContext by remember { mutableStateOf<PageTutorRegionContext?>(null) }
  var isOpening by readerSession.isOpening
  var isImageImporting by remember { mutableStateOf(false) }
  var pendingMarkdownImageInsertion by remember { mutableStateOf<PendingMarkdownImageInsertion?>(null) }
  var pendingPdfImageInsertion by remember { mutableStateOf<PendingPdfImageInsertion?>(null) }
  var showSettings by remember { mutableStateOf(false) }
  var splitLeftPercent by remember(settings.leftPercent) {
    mutableFloatStateOf(settings.leftPercent.toFloat())
  }
  var showRecentProjectsDialog by remember { mutableStateOf(false) }
  var helpTopic by remember { mutableStateOf<ReaderHelpTopic?>(null) }
  var helpDiagnostics by remember { mutableStateOf("") }
  var showProjectAttachments by remember { mutableStateOf(false) }
  var projectAttachments by remember { mutableStateOf(emptyList<ProjectAttachmentEntry>()) }
  var projectAttachmentsError by remember { mutableStateOf<String?>(null) }
  var isLoadingProjectAttachments by remember { mutableStateOf(false) }
  var isImportingProjectAttachment by remember { mutableStateOf(false) }
  var isDeletingProjectAttachment by remember { mutableStateOf(false) }
  var pendingProjectAttachmentImport by remember { mutableStateOf<Pair<String, Uri>?>(null) }
  var menuDrawerOpen by rememberSaveable { mutableStateOf(false) }
  var menuSidebarExpanded by rememberSaveable { mutableStateOf(true) }
  var showAgentModelSettings by remember { mutableStateOf(false) }
  var showPageTutor by remember { mutableStateOf(false) }
  var pageTutorRegionContext by remember { mutableStateOf<PageTutorRegionContext?>(null) }
  var pageTutorInstruction by remember {
    mutableStateOf(context.getString(R.string.page_tutor_default_instruction))
  }
  var pageTutorResult by remember { mutableStateOf<PageTutorResult?>(null) }
  var pageTutorResultInstruction by remember { mutableStateOf<String?>(null) }
  var pageTutorError by remember { mutableStateOf<String?>(null) }
  var isPageTutorRunning by remember { mutableStateOf(false) }
  val agentRunner = remember { AgentRunner(OkHttpChatClient()) }
  var showAgentTask by remember { mutableStateOf(false) }
  var showPageJumpDialog by remember { mutableStateOf(false) }
  var showHeadingLevelDialog by remember { mutableStateOf(false) }
  var showNoteFontSizeDialog by remember { mutableStateOf(false) }
  var pageJumpInput by remember { mutableStateOf("") }
  var isPageJumpRunning by remember { mutableStateOf(false) }
  var agentInstruction by remember { mutableStateOf("") }
  var agentPageRange by remember { mutableStateOf("") }
  var agentReport by remember { mutableStateOf<SerialPages.Report?>(null) }
  var agentEvent by remember { mutableStateOf<AgentEvent?>(null) }
  var agentError by remember { mutableStateOf<String?>(null) }
  var isAgentRunning by remember { mutableStateOf(false) }
  var isRendering by remember { mutableStateOf(false) }
  var isSaving by readerSession.isSaving
  val isPenSaving = pendingPenSaves > 0
  var errorMessage by readerSession.errorMessage
  var showNoteHistory by remember { mutableStateOf(false) }
  var showNoteImportConfirmation by remember { mutableStateOf(false) }
  var isImportingNotes by remember { mutableStateOf(false) }
  var pendingNoteImportContext by remember { mutableStateOf<PendingNoteImportContext?>(null) }
  var pendingNoteImportBackup by remember { mutableStateOf<PendingNoteImportBackup?>(null) }
  var noteEditRequest by remember { mutableIntStateOf(0) }
  var showNoteExportDialog by remember { mutableStateOf(false) }
  var showPendingNoteExportRecoveryDialog by remember { mutableStateOf(false) }
  var isPreparingNoteExport by remember { mutableStateOf(false) }
  val isExportingNotes = isPreparingNoteExport || isRestoringPendingNoteExport ||
    isHandlingNoteExportResult || (pendingNoteExport != null && !isPendingNoteExportRecovered)
  var annotateMode by remember { mutableStateOf(false) }
  var annotationTextToolMode by remember { mutableStateOf(false) }
  var annotateDraft by remember { mutableStateOf<Pair<Double, Double>?>(null) }
  var annotateText by remember { mutableStateOf("") }
  var annotateIsRegion by remember { mutableStateOf(false) }
  var annotationDrawTool by remember { mutableStateOf<PdfAnnotationDrawTool?>(null) }
  var annotationPreview by remember { mutableStateOf<Pair<Int, PdfAnnotationOverlayItem>?>(null) }
  var isAnnotationSaving by remember { mutableStateOf(false) }
  var showAnnotationShapePicker by remember { mutableStateOf(false) }
  var showAnnotations by remember { mutableStateOf(false) }
  var annotationOverlayItems by remember { mutableStateOf(emptyList<PdfAnnotationOverlayItem>()) }
  var annotationPageItems by remember(project?.manifest?.id, pageIndex) {
    mutableStateOf(JsonArray(emptyList()))
  }
  var annotationOverlayImages by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
  var annotationOverlayHtmlBitmaps by remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }
  var annotationOverlayReload by remember { mutableIntStateOf(0) }
  var selectedAnnotationIndex by remember(project?.manifest?.id, project?.sourceDirectoryUri, pageIndex) {
    mutableStateOf<Int?>(null)
  }
  var annotationGesture by remember(project?.manifest?.id, pageIndex) {
    mutableStateOf<PdfAnnotationSelectionGesture?>(null)
  }
  val annotationEditHistory = remember(project?.manifest?.id, project?.sourceDirectoryUri) {
    PdfAnnotationEditHistory()
  }
  val lastEditWasAnnotation = remember(project?.manifest?.id, project?.sourceDirectoryUri) {
    mutableMapOf<Int, Boolean>()
  }
  var showReviews by remember { mutableStateOf(false) }
  var noteSelection by remember { mutableStateOf<TextRange?>(null) }
  var markdownEditor by remember { mutableStateOf<EditText?>(null) }
  val markdownHistory = remember(project?.manifest?.id, pageIndex) {
    MarkdownEditHistory(noteDraft)
  }
  var markdownHistoryRevision by remember(project?.manifest?.id, pageIndex) {
    mutableIntStateOf(0)
  }
  var isApplyingMarkdownHistory by remember(project?.manifest?.id, pageIndex) {
    mutableStateOf(false)
  }
  var selectionReviewDraft by remember { mutableStateOf<NoteSelectionReviewDraft?>(null) }
  var showHandoff by remember { mutableStateOf(false) }
  val savedPageText = NotePage.pageAt(notesMarkdown, pageIndex + 1)
  val hasUnsavedChanges = noteDraft != savedPageText

  fun updateViewportTransform(transform: PdfViewportTransform) {
    val bounds = viewportBounds
    if (bounds.viewportWidthPx <= 0f || bounds.viewportHeightPx <= 0f ||
      bounds.pageWidthPx <= 0f || bounds.pageHeightPx <= 0f
    ) {
      return
    }
    readerSession.viewportBookmark.value = PdfViewportBookmark.fromTransform(transform, bounds)
  }

  fun selectAnnotationDrawTool(tool: PdfAnnotationDrawTool?) {
    annotationDrawTool = tool
    annotationPreview = null
    annotateMode = false
    annotationTextToolMode = false
    if (tool != null) selectedAnnotationIndex = null
  }

  fun handleAnnotationDrag(
    tool: PdfAnnotationDrawTool,
    start: PdfSelectionPoint,
    end: PdfSelectionPoint,
    finished: Boolean,
  ) {
    val points = mapPdfAnnotationDragToPoints(start, end, viewportBounds, viewportTransform)
    val draft = points?.let {
      PdfAnnotationVectorDraft.create(
        tool.type,
        it.first,
        it.second,
        color = penStyle.color,
        width = penStyle.width,
      )
    }
    val targetPage = pageIndex + 1
    annotationPreview = draft?.toOverlayItem()?.let { targetPage to it }
    if (!finished) return
    if (draft == null) {
      annotationPreview = null
      noteStatus = context.getString(R.string.annotation_drag_invalid)
      return
    }
    val targetProject = project ?: run {
      annotationPreview = null
      return
    }
    isAnnotationSaving = true
    noteStatus = context.getString(R.string.annotation_saving)
    scope.launch {
      var retainedRecoveryCopyName: String? = null
      try {
        penSaveMutex.withLock {
          val store = ProjectAnnotationStore(
            context.contentResolver,
            targetProject.sourceDirectoryUri,
            targetProject.annotationsUri,
          )
          val existing = withContext(Dispatchers.IO) {
            store.readPage("document", targetPage)
          }
          val merged = existing.toMutableList().apply { add(draft.toItem().toJsonElement()) }
          withContext(Dispatchers.IO) {
            store.writePage("document", targetPage, kotlinx.serialization.json.JsonArray(merged))
            retainedRecoveryCopyName = store.lastRetainedRecoveryCopyName
          }
        }
        annotationOverlayReload += 1
        noteStatus = retainedRecoveryCopyName?.let {
          context.getString(R.string.annotation_saved_recovery_copy, it)
        } ?: context.getString(R.string.annotation_saved)
      } catch (error: Exception) {
        noteStatus = context.getString(R.string.annotation_save_failed, error.message ?: "")
      } finally {
        if (annotationPreview?.first == targetPage) annotationPreview = null
        isAnnotationSaving = false
      }
    }
  }

  suspend fun recognizeAndCacheFullPage(
    activeRenderer: PdfDocumentSession,
    activeProject: OpenedProject,
    pageIndex: Int,
  ): OcrResult {
    val sourceRevision = activeRenderer.sourceRevision
    val sourceUri = activeProject.sourceDirectoryUri.toString()
    val documentId = activeProject.manifest.id
    val fullPage = PageRegion(0.0, 0.0, 1000.0, 1000.0)
    if (sourceRevision != null) {
      val cached = withContext(Dispatchers.IO) {
        preparedOcrCache.read(
          sourceUri,
          documentId,
          pageIndex + 1,
          sourceRevision,
          fullPage,
          OcrResultMapper.ENGINE,
        )
      }
      if (cached != null) return cached.result
    }

    val rendered = withContext(Dispatchers.IO) { activeRenderer.renderWithMetadata(pageIndex) }
    val bitmap = rendered.bitmap
    try {
      val engine = ocrEngine ?: OfflineOcrEngine.create(context).also { ocrEngine = it }
      return RegionOcrProcessor(preparedOcrCache).recognize(
        srcDirectoryUri = sourceUri,
        documentId = documentId,
        page = pageIndex + 1,
        sourceRevision = sourceRevision,
        selection = NormalizedOcrRegion(0, 0, 1000, 1000),
        imageWidth = bitmap.width,
        imageHeight = bitmap.height,
        renderScale = rendered.scale,
        engine = OcrResultMapper.ENGINE,
        notCachedWarning = context.getString(R.string.ocr_not_cached),
        recognize = {
          withTimeout(OcrPolicy.TIMEOUT_MS) {
            withContext(Dispatchers.Default) { engine.recognize(bitmap) }
          }
        },
        encodeImages = {
          val png = withContext(Dispatchers.IO) { bitmap.toPngBytes() }
          PreparedOcrImages(png, png)
        },
      ).result
    } finally {
      if (!bitmap.isRecycled) bitmap.recycle()
    }
  }

  fun createPdfSourceLoader(
    activeRenderer: PdfDocumentSession,
    activeProject: OpenedProject,
  ) = PageTutorSourceLoader(
    nativeText = { page ->
      withContext(Dispatchers.IO) { activeRenderer.extractText(page) }
    },
    searchablePdfText = { page ->
      withContext(Dispatchers.IO) { activeRenderer.extractSelectableText(page)?.text }
    },
    ocrText = { page ->
      recognizeAndCacheFullPage(activeRenderer, activeProject, page)
    },
    hasBitmap = { page ->
      withContext(Dispatchers.IO) { activeRenderer.hasRasterImageContent(page) }
    },
  )

  fun preparationIdentity(
    activeProject: OpenedProject,
    activeRenderer: PdfDocumentSession,
  ) = PagePreparationIdentity(
    projectId = "${activeProject.sourceDirectoryUri}:${activeProject.manifest.id}",
    sourceRevision = activeRenderer.sourceRevision
      ?: "session-${System.identityHashCode(activeRenderer)}",
  )

  suspend fun loadCurrentPageSource(
    identity: PagePreparationIdentity,
    loader: PageTutorSourceLoader,
    pageIndex: Int,
  ): PageTutorSourceText = pagePrefetcher.cached(identity, pageIndex)
    ?: loader.loadCurrent(pageIndex).also { pagePrefetcher.remember(identity, pageIndex, it) }

  suspend fun loadAgentPageSource(
    identity: PagePreparationIdentity,
    loader: PageTutorSourceLoader,
    pageIndex: Int,
  ): PageTutorSourceText = pagePrefetcher.cached(identity, pageIndex)
    ?: loader.loadNeighborForAgent(pageIndex).also { pagePrefetcher.remember(identity, pageIndex, it) }

  suspend fun loadNeighborPageSource(
    identity: PagePreparationIdentity,
    loader: PageTutorSourceLoader,
    pageIndex: Int,
  ): PageTutorSourceText? = pagePrefetcher.cached(identity, pageIndex)
    ?: loader.loadNeighbor(pageIndex)?.also { pagePrefetcher.remember(identity, pageIndex, it) }

  fun applyViewportZoom(zoomChange: Float) {
    val bounds = viewportBounds
    updateViewportTransform(PdfViewportTransform.applyGesture(
      current = viewportTransform,
      viewportWidthPx = bounds.viewportWidthPx,
      viewportHeightPx = bounds.viewportHeightPx,
      pageWidthPx = bounds.pageWidthPx,
      pageHeightPx = bounds.pageHeightPx,
      centroidX = bounds.viewportWidthPx / 2f,
      centroidY = bounds.viewportHeightPx / 2f,
      zoomChange = zoomChange,
      panX = 0f,
      panY = 0f,
    ))
  }

  fun fitViewportWidth() {
    updateViewportTransform(PdfViewportTransform.fitWidth(
      viewportWidthPx = viewportBounds.viewportWidthPx,
      pageWidthPx = viewportBounds.pageWidthPx,
    ))
  }

  suspend fun savePenPage(targetPage: Int, strokes: List<PenStroke>) {
    val store = penStore ?: return
    if (!penWritable) {
      penStatus = context.getString(R.string.readonly_pen_cannot_save)
      return
    }
    val snapshot = strokes.toList()
    pendingPenSaves += 1
    try {
      penSaveMutex.withLock {
        withContext(Dispatchers.IO) {
          store.writePage(PenSide.DOCUMENT, targetPage, snapshot)
        }
      }
      if (failedPenSave?.first == targetPage) failedPenSave = null
      lastEditWasAnnotation[targetPage] = false
      penStatus = store.lastRetainedRecoveryCopyName?.let {
        context.getString(R.string.pen_saved_recovery_copy, it)
      } ?: context.getString(R.string.pen_saved)
    } catch (error: Exception) {
      if (error is CancellationException) throw error
      failedPenSave = targetPage to snapshot
      penStatus = context.getString(R.string.pen_not_saved, error.message ?: "")
    } finally {
      pendingPenSaves -= 1
    }
  }

  fun applyPenStroke(stroke: PenStroke) {
    val targetPage = pageIndex + 1
    penPageStore.addStroke(PenSide.DOCUMENT, targetPage, stroke)
    val updated = penPageStore.getItems(PenSide.DOCUMENT, targetPage)
    selectedPenIndex = null
    penStrokes = updated
    readerSession.launchSessionTask { savePenPage(targetPage, updated) }
  }

  fun erasePenStroke(index: Int) {
    val targetPage = pageIndex + 1
    if (penPageStore.eraseAt(PenSide.DOCUMENT, targetPage, index)) {
      val updated = penPageStore.getItems(PenSide.DOCUMENT, targetPage)
      selectedPenIndex = selectedPenIndex?.let { selected ->
        when {
          selected == index -> null
          selected > index -> selected - 1
          else -> selected
        }
      }
      penStrokes = updated
      readerSession.launchSessionTask { savePenPage(targetPage, updated) }
    }
  }

  fun clearPenPage() {
    val targetPage = pageIndex + 1
    if (penPageStore.clearPage(PenSide.DOCUMENT, targetPage)) {
      selectedPenIndex = null
      penStrokes = emptyList()
      readerSession.launchSessionTask { savePenPage(targetPage, emptyList()) }
    }
  }

  fun annotationClipboardText(): String? = context.getSystemService(ClipboardManager::class.java)
    ?.primaryClip
    ?.takeIf { it.itemCount > 0 }
    ?.getItemAt(0)
    ?.text
    ?.toString()

  fun annotationClipboardHtml(): String? = context.getSystemService(ClipboardManager::class.java)
    ?.primaryClip
    ?.takeIf { it.itemCount > 0 }
    ?.getItemAt(0)
    ?.htmlText
    ?.toString()
    ?.takeIf { it.isNotBlank() && it.length <= RichHtmlAnnotationRenderer.MAX_HTML_CHARS }

  fun nativeAnnotationClipboardForPaste(): JsonObject? {
    val item = annotationClipboardText()?.let(PdfAnnotationClipboard::decode) ?: return null
    if (item["type"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() } == "image") {
      val assetName = item["url"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.let(PdfAnnotationImageReference::assetName) ?: return null
      val targetProject = project ?: return null
      val exists = runCatching {
        PdfAnnotationImageLoader(context.contentResolver, targetProject.sourceDirectoryUri).hasAsset(assetName)
      }.getOrDefault(false)
      if (!exists) return null
    }
    return item
  }

  fun selectedInkCanBeCopied(): Boolean =
    selectedPenIndex?.let { it in penStrokes.indices } == true ||
      selectedAnnotationIndex?.let { index ->
        annotationPageItems.getOrNull(index) is JsonObject &&
          PdfAnnotationClipboard.decode(
            PdfAnnotationClipboard.MARKER + annotationPageItems[index].toString(),
          ) != null
      } == true

  fun persistNativeAnnotationPage(
    targetPage: Int,
    before: JsonArray,
    after: JsonArray,
    selectedAfter: Int? = null,
    recordHistory: Boolean = true,
    appendItem: JsonElement? = null,
    transformLatest: ((JsonArray) -> JsonArray?)? = null,
    onFailure: (() -> Unit)? = null,
  ) {
    val targetProject = project ?: return
    val targetProjectId = targetProject.manifest.id
    var committedBefore = before
    var committedAfter = after
    var committedSelection = selectedAfter
    var shouldCommit = true
    var selectionWasStale = false
    isAnnotationSaving = true
    penStatus = context.getString(R.string.annotation_saving)
    scope.launch {
      try {
        penSaveMutex.withLock {
          if (readerSession.project.value?.manifest?.id != targetProjectId) {
            throw IllegalStateException(context.getString(R.string.annotation_project_changed))
          }
          val store = ProjectAnnotationStore(
              context.contentResolver,
              targetProject.sourceDirectoryUri,
              targetProject.annotationsUri,
          )
          if (appendItem != null) {
            // A freehand save may complete after the UI snapshot; append against the locked latest page.
            committedBefore = withContext(Dispatchers.IO) {
              store.readPage("document", targetPage)
            }
            committedAfter = JsonArray(committedBefore + appendItem)
            committedSelection = committedAfter.lastIndex
          } else if (transformLatest != null) {
            committedBefore = withContext(Dispatchers.IO) {
              store.readPage("document", targetPage)
            }
            val transformed = transformLatest(committedBefore)
            if (transformed == null) {
              shouldCommit = false
              selectionWasStale = true
              return@withLock
            }
            committedAfter = transformed
            if (committedAfter == committedBefore) {
              shouldCommit = false
              return@withLock
            }
          }
          if (shouldCommit) {
            withContext(Dispatchers.IO) { store.writePage("document", targetPage, committedAfter) }
          }
        }
        if (!shouldCommit) {
          if (readerSession.project.value?.manifest?.id == targetProjectId &&
            readerSession.pageIndex.intValue + 1 == targetPage
          ) {
            annotationPageItems = committedBefore
            annotationOverlayItems = PdfAnnotationOverlayItem.fromPage(committedBefore)
            selectedAnnotationIndex = if (selectionWasStale) null else committedSelection
            selectedPenIndex = null
            annotationOverlayReload++
          }
          if (selectionWasStale) penStatus = context.getString(R.string.pen_selection_required)
          return@launch
        }
        if (recordHistory) {
          annotationEditHistory.record(targetProjectId, targetPage, committedBefore, committedAfter)
        }
        lastEditWasAnnotation[targetPage] = true
        if (readerSession.project.value?.manifest?.id == targetProjectId &&
          readerSession.pageIndex.intValue + 1 == targetPage
        ) {
          annotationPageItems = committedAfter
          annotationOverlayItems = PdfAnnotationOverlayItem.fromPage(committedAfter)
          selectedAnnotationIndex = committedSelection
          selectedPenIndex = null
          annotationOverlayReload++
        }
        penStatus = context.getString(R.string.pen_annotation_saved)
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        onFailure?.invoke()
        if (readerSession.project.value?.manifest?.id == targetProjectId &&
          readerSession.pageIndex.intValue + 1 == targetPage
        ) {
          annotationPageItems = committedBefore
          annotationOverlayItems = PdfAnnotationOverlayItem.fromPage(committedBefore)
        }
        penStatus = context.getString(R.string.pen_annotation_save_failed, error.message ?: "")
      } finally {
        isAnnotationSaving = false
      }
    }
  }

  fun applyPenStyle(color: String? = null, width: Double? = null) {
    val nextStyle = PenStyle.create(color ?: penColor, width ?: penWidth) ?: return
    penColor = nextStyle.color
    penWidth = nextStyle.width

    val targetPage = pageIndex + 1
    val strokeIndex = selectedPenIndex?.takeIf { it in penStrokes.indices }
    if (strokeIndex != null) {
      if (!penWritable) return
      if (penPageStore.updateStyle(
          PenSide.DOCUMENT,
          targetPage,
          strokeIndex,
          color = color?.let { nextStyle.color },
          width = width?.let { nextStyle.width },
        )
      ) {
        val updated = penPageStore.getItems(PenSide.DOCUMENT, targetPage)
        penStrokes = updated
        selectedAnnotationIndex = null
        lastEditWasAnnotation[targetPage] = false
        readerSession.launchSessionTask { savePenPage(targetPage, updated) }
      }
      return
    }

    if (!penWritable) return
    val annotationIndex = selectedAnnotationIndex?.takeIf { it in annotationPageItems.indices } ?: return
    val selected = annotationPageItems[annotationIndex] as? JsonObject ?: return
    val selectedColor = color?.let { nextStyle.color }
    val selectedWidth = width?.let { nextStyle.width }
    val after = PdfAnnotationClipboard.updateSelectedStyle(
      annotationPageItems,
      annotationIndex,
      selected,
      color = selectedColor,
      width = selectedWidth,
    ) ?: run {
      selectedAnnotationIndex = null
      return
    }
    if (after == annotationPageItems) return
    persistNativeAnnotationPage(
      targetPage = targetPage,
      before = annotationPageItems,
      after = after,
      selectedAfter = annotationIndex,
      transformLatest = { latest ->
        PdfAnnotationClipboard.updateSelectedStyle(
          latest,
          annotationIndex,
          selected,
          color = selectedColor,
          width = selectedWidth,
        )
      },
    )
  }

  fun applyRecognizedShape(shape: PenRecognizedShape, sourceStroke: PenStroke): Boolean {
    if (project == null || !penWritable) return false
    val draft = PdfAnnotationVectorDraft.fromRecognizedShape(
      shape = shape,
      color = sourceStroke.color,
      width = sourceStroke.width,
    ) ?: return false
    val before = annotationPageItems
    val item = draft.toItem().toJsonElement()
    persistNativeAnnotationPage(
      targetPage = pageIndex + 1,
      before = before,
      after = JsonArray(before + item),
      selectedAfter = before.size,
      appendItem = item,
      onFailure = { applyPenStroke(sourceStroke) },
    )
    return true
  }

  fun undoNativeAnnotationPage(): Boolean {
    val targetProject = project ?: return false
    val targetPage = pageIndex + 1
    val restored = annotationEditHistory.undo(targetProject.manifest.id, targetPage) ?: return false
    persistNativeAnnotationPage(
      targetPage = targetPage,
      before = annotationPageItems,
      after = restored,
      selectedAfter = selectedAnnotationIndex?.takeIf { restored.getOrNull(it) is JsonObject },
      recordHistory = false,
      onFailure = { annotationEditHistory.redo(targetProject.manifest.id, targetPage) },
    )
    return true
  }

  fun redoNativeAnnotationPage(): Boolean {
    val targetProject = project ?: return false
    val targetPage = pageIndex + 1
    val restored = annotationEditHistory.redo(targetProject.manifest.id, targetPage) ?: return false
    persistNativeAnnotationPage(
      targetPage = targetPage,
      before = annotationPageItems,
      after = restored,
      selectedAfter = selectedAnnotationIndex?.takeIf { restored.getOrNull(it) is JsonObject },
      recordHistory = false,
      onFailure = { annotationEditHistory.undo(targetProject.manifest.id, targetPage) },
    )
    return true
  }

  fun undoPenPage() {
    val targetPage = pageIndex + 1
    val projectId = project?.manifest?.id
    val preferAnnotation = projectId != null && lastEditWasAnnotation[targetPage] == true
    if (preferAnnotation && undoNativeAnnotationPage()) return
    if (penPageStore.undo(PenSide.DOCUMENT, targetPage)) {
      selectedPenIndex = null
      selectedAnnotationIndex = null
      lastEditWasAnnotation[targetPage] = false
      val updated = penPageStore.getItems(PenSide.DOCUMENT, targetPage)
      penStrokes = updated
      readerSession.launchSessionTask { savePenPage(targetPage, updated) }
      return
    }
    if (undoNativeAnnotationPage()) lastEditWasAnnotation[targetPage] = true
  }

  fun redoPenPage() {
    val targetPage = pageIndex + 1
    val projectId = project?.manifest?.id
    val preferAnnotation = projectId != null && lastEditWasAnnotation[targetPage] == true
    if (preferAnnotation && redoNativeAnnotationPage()) return
    if (penPageStore.redo(PenSide.DOCUMENT, targetPage)) {
      selectedPenIndex = null
      selectedAnnotationIndex = null
      lastEditWasAnnotation[targetPage] = false
      val updated = penPageStore.getItems(PenSide.DOCUMENT, targetPage)
      penStrokes = updated
      readerSession.launchSessionTask { savePenPage(targetPage, updated) }
      return
    }
    if (redoNativeAnnotationPage()) lastEditWasAnnotation[targetPage] = true
  }

  fun handleNativeAnnotationSelectionTouch(
    event: MotionEvent,
    geometry: PenCanvasGeometry,
    pointerIndex: Int,
  ): Boolean {
    if (!geometry.isValid) return false
    if (pointerIndex !in 0 until event.pointerCount && event.actionMasked != MotionEvent.ACTION_CANCEL) return false
    val safePointerIndex = pointerIndex.takeIf { it in 0 until event.pointerCount } ?: 0
    val point = if (event.pointerCount > 0) {
      geometry.toDocumentPoint(
        event.getX(safePointerIndex),
        event.getY(safePointerIndex),
        pressure = null,
        clampToPage = false,
      )
    } else {
      null
    }
    val x = point?.x?.toDouble() ?: 0.0
    val y = point?.y?.toDouble() ?: 0.0
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val activeIndex = selectedAnnotationIndex
        val activeBounds = activeIndex?.let { index ->
          (annotationPageItems.getOrNull(index) as? JsonObject)?.let(PdfAnnotationClipboard::bounds)
        }
        val handleRadius = 24.0
        val resize = activeBounds?.let { bounds ->
          hypot(x - bounds.right - 5.0, y - bounds.bottom - 5.0) <= handleRadius
        } == true
        val hit = if (resize) activeIndex else PdfAnnotationClipboard.hitTest(
          annotationPageItems,
          x,
          y,
          tolerance = geometry.documentUnitsForPixels(18f * densityScale).toDouble().coerceAtLeast(0.1),
        )
        if (hit == null) {
          selectedAnnotationIndex = null
          annotationGesture = null
          return false
        }
        val item = annotationPageItems.getOrNull(hit) as? JsonObject ?: return false
        val bounds = PdfAnnotationClipboard.bounds(item) ?: return false
        selectedAnnotationIndex = hit
        selectedPenIndex = null
        annotationGesture = PdfAnnotationSelectionGesture(
          sourceIndex = hit,
          startX = x,
          startY = y,
          originalItem = item,
          originalPage = annotationPageItems,
          bounds = bounds,
          resizing = resize && item["type"]?.let { it.jsonPrimitive.content } != "text",
        )
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        val gesture = annotationGesture ?: return false
        val moved = kotlin.math.hypot(x - gesture.startX, y - gesture.startY) > 3.0
        if (!moved) return true
        val updated = if (gesture.resizing) {
          PdfAnnotationClipboard.resize(gesture.originalItem, gesture.bounds, x, y)
        } else {
          PdfAnnotationClipboard.translate(gesture.originalItem, x - gesture.startX, y - gesture.startY)
        } ?: return true
        val page = gesture.originalPage.toMutableList().apply { this[gesture.sourceIndex] = updated }
        annotationPageItems = JsonArray(page)
        annotationOverlayItems = PdfAnnotationOverlayItem.fromPage(annotationPageItems)
        annotationGesture = gesture.copy(moved = true)
        return true
      }
      MotionEvent.ACTION_UP -> {
        val gesture = annotationGesture
        annotationGesture = null
        if (gesture?.moved == true) {
          persistNativeAnnotationPage(
            targetPage = pageIndex + 1,
            before = gesture.originalPage,
            after = annotationPageItems,
            selectedAfter = gesture.sourceIndex,
          )
        }
        return gesture != null
      }
      MotionEvent.ACTION_CANCEL -> {
        val gesture = annotationGesture
        annotationGesture = null
        if (gesture?.moved == true) {
          annotationPageItems = gesture.originalPage
          annotationOverlayItems = PdfAnnotationOverlayItem.fromPage(gesture.originalPage)
        }
        return gesture != null
      }
    }
    return annotationGesture != null
  }

  fun copySelectedInk() {
    val stroke = selectedPenIndex?.let(penStrokes::getOrNull)
    val annotation = selectedAnnotationIndex?.let { annotationPageItems.getOrNull(it) as? JsonObject }
    if (stroke == null && annotation == null) {
      penStatus = context.getString(R.string.pen_selection_required)
      return
    }
    try {
      val clipboard = context.getSystemService(ClipboardManager::class.java)
      if (clipboard == null) {
        penStatus = context.getString(R.string.pen_clipboard_unavailable)
        return
      }
      val annotationType = annotation?.get("type")?.let {
        runCatching { it.jsonPrimitive.content }.getOrNull()
      }
      if (stroke == null && annotationType == "html") {
        val source = checkNotNull(annotation)
        val html = source["html"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
          ?: throw IllegalArgumentException("HTML annotation has no content")
        val targetProjectId = project?.manifest?.id
        val targetPage = pageIndex + 1
        scope.launch {
          try {
            val sanitized = withContext(Dispatchers.IO) {
              val rendered = richHtmlRenderer.render(html, widthCssPx = 500, heightCssPx = 1)
              try {
                rendered.sanitizedHtml
              } finally {
                rendered.bitmap.recycle()
              }
            }
            if (project?.manifest?.id != targetProjectId || pageIndex + 1 != targetPage) {
              penStatus = context.getString(R.string.annotation_html_stale)
              return@launch
            }
            val safeAnnotation = JsonObject(source + ("html" to JsonPrimitive(sanitized)))
            clipboard.setPrimaryClip(
              ClipData.newPlainText(
                context.getString(R.string.reader_menu_copy_ink),
                PdfAnnotationClipboard.encode(safeAnnotation),
              ),
            )
            penStatus = context.getString(R.string.pen_copied)
          } catch (error: Exception) {
            penStatus = context.getString(R.string.annotation_html_failed, error.message ?: "")
          }
        }
      } else {
        clipboard.setPrimaryClip(
          ClipData.newPlainText(
            context.getString(R.string.reader_menu_copy_ink),
            stroke?.let(PenClipboardCodec::encode) ?: PdfAnnotationClipboard.encode(checkNotNull(annotation)),
          ),
        )
        penStatus = context.getString(R.string.pen_copied)
      }
    } catch (_: Exception) {
      penStatus = context.getString(R.string.pen_copy_failed)
    }
  }

  fun clipboardInkForPaste(): PenStroke? {
    val text = context.getSystemService(ClipboardManager::class.java)
      ?.primaryClip
      ?.takeIf { it.itemCount > 0 }
      ?.getItemAt(0)
      ?.text
      ?.toString()
      ?: return null
    return PenClipboardCodec.decode(text, PenPoint(80f, 90f, PenValidation.DEFAULT_PRESSURE))
  }

  fun pasteInk() {
    val stroke = clipboardInkForPaste()
    if (stroke == null) {
      val item = nativeAnnotationClipboardForPaste()
      val targetProject = project
      val clipboardHtml = annotationClipboardHtml()
      val markedHtml = item?.takeIf {
        it["type"]?.let { type -> runCatching { type.jsonPrimitive.content }.getOrNull() } == "html"
      }
      if (clipboardHtml != null || markedHtml != null) {
        if (targetProject == null || !penWritable || markdownSource != null) {
          penStatus = context.getString(R.string.reader_menu_read_only)
          return
        }
        val targetProjectId = targetProject.manifest.id
        val targetSourceUri = targetProject.sourceDirectoryUri
        val targetPage = pageIndex + 1
        val sourceHtml = markedHtml?.get("html")?.let {
          runCatching { it.jsonPrimitive.content }.getOrNull()
        } ?: clipboardHtml
        if (sourceHtml.isNullOrBlank() || sourceHtml.length > RichHtmlAnnotationRenderer.MAX_HTML_CHARS) {
          penStatus = context.getString(R.string.annotation_html_failed, "HTML is empty or too large")
          return
        }
        scope.launch {
          var rendered: RenderedRichHtmlAnnotation? = null
          try {
            val safeRender = withContext(Dispatchers.IO) {
              richHtmlRenderer.render(sourceHtml, widthCssPx = 500, heightCssPx = null)
            }
            rendered = safeRender
            if (
              project?.manifest?.id != targetProjectId ||
              project?.sourceDirectoryUri != targetSourceUri ||
              pageIndex + 1 != targetPage || !penWritable
            ) {
              penStatus = context.getString(R.string.annotation_html_stale)
              return@launch
            }
            val pasted = if (markedHtml != null) {
              val safeItem = JsonObject(markedHtml + ("html" to JsonPrimitive(safeRender.sanitizedHtml)))
              PdfAnnotationClipboard.pasteAt(safeItem)
            } else {
              kotlinx.serialization.json.buildJsonObject {
                put("type", JsonPrimitive("html"))
                put("color", JsonPrimitive(penStyle.color))
                put("width", JsonPrimitive(penStyle.width.toDouble()))
                put("points", kotlinx.serialization.json.buildJsonArray {
                  add(kotlinx.serialization.json.buildJsonArray {
                    add(JsonPrimitive(80.0))
                    add(JsonPrimitive(90.0))
                  })
                  val height = maxOf(60, safeRender.contentHeightPx + 20).toDouble()
                  add(kotlinx.serialization.json.buildJsonArray {
                    add(JsonPrimitive(580.0))
                    add(JsonPrimitive(90.0 + height))
                  })
                })
                put("html", JsonPrimitive(safeRender.sanitizedHtml))
              }
            }
            if (pasted == null) {
              penStatus = context.getString(R.string.annotation_html_failed, "HTML annotation is invalid")
              return@launch
            }
            val before = annotationPageItems
            persistNativeAnnotationPage(
              targetPage = targetPage,
              before = before,
              after = JsonArray(before + pasted),
              selectedAfter = before.size,
            )
          } catch (error: Exception) {
            if (error is CancellationException) throw error
            penStatus = context.getString(R.string.annotation_html_failed, error.message ?: "")
          } finally {
            rendered?.bitmap?.let { if (!it.isRecycled) it.recycle() }
          }
        }
        return
      }
      val pasted = item?.let(PdfAnnotationClipboard::pasteAt)
      if (pasted == null || targetProject == null) {
        penStatus = context.getString(R.string.pen_clipboard_invalid)
        return
      }
      val before = annotationPageItems
      val after = JsonArray(before + pasted)
      persistNativeAnnotationPage(
        targetPage = pageIndex + 1,
        before = before,
        after = after,
        selectedAfter = after.lastIndex,
      )
      return
    }
    val targetPage = pageIndex + 1
    try {
      penPageStore.addStroke(PenSide.DOCUMENT, targetPage, stroke)
    } catch (_: Exception) {
      penStatus = context.getString(R.string.pen_paste_failed)
      return
    }
    val updated = penPageStore.getItems(PenSide.DOCUMENT, targetPage)
    selectedPenIndex = updated.lastIndex
    penStrokes = updated
    penStatus = context.getString(R.string.pen_pasted)
    readerSession.launchSessionTask { savePenPage(targetPage, updated) }
  }

  fun deleteSelectedInk() {
    val annotationIndex = selectedAnnotationIndex?.takeIf { it in annotationPageItems.indices }
    if (annotationIndex != null) {
      val before = annotationPageItems
      val after = JsonArray(before.filterIndexed { index, _ -> index != annotationIndex })
      selectedAnnotationIndex = null
      persistNativeAnnotationPage(
        targetPage = pageIndex + 1,
        before = before,
        after = after,
      )
      penStatus = context.getString(R.string.pen_deleted)
      return
    }
    val index = selectedPenIndex?.takeIf { it in penStrokes.indices } ?: return
    selectedPenIndex = null
    erasePenStroke(index)
    penStatus = context.getString(R.string.pen_deleted)
  }

  suspend fun saveCurrentNote(): Boolean {
    val activeProject = readerSession.project.value ?: return false
    val targetPage = readerSession.pageIndex.intValue + 1
    val draftToSave = readerSession.noteDraft.value
    val savedPage = NotePage.pageAt(readerSession.notesMarkdown.value, targetPage)
    if (!readerSession.noteWritable.value) {
      noteStatus = context.getString(R.string.note_read_only)
      return false
    }
    if (draftToSave == savedPage) {
      readerSession.clearCurrentDraftRecovery(targetPage)
      return true
    }

    val expectedRevision = readerSession.noteBaseRevision.value
    return readerSession.asyncSessionTask {
      isSaving = true
      noteStatus = null
      try {
        val (result, recoveryCopyName) = withContext(Dispatchers.IO) {
          val store = ProjectNoteStore(context.contentResolver, activeProject.notesUri)
          val latest = store.read()
          val edited = PageNoteEditor.replacePage(
            latestMarkdown = latest,
            page = targetPage,
            replacement = draftToSave,
            expectedRevision = expectedRevision,
          )
          val retainedCopy = store.write(edited.markdown, expectedOriginal = latest)
          edited to retainedCopy
        }
        notesMarkdown = result.markdown
        noteBaseRevision = result.pageRevision
        readerSession.refreshDraftRecoveryAfterSave(
          targetPage,
          NotePage.pageAt(result.markdown, targetPage),
        )
        noteStatus = recoveryCopyName?.let { name ->
          context.getString(R.string.note_saved_recovery_copy, name)
        } ?: context.getString(R.string.note_saved)
        withContext(Dispatchers.IO) {
          runCatching {
            NoteHistoryMirror.snapshotPage(
              context.filesDir, activeProject.manifest.id, targetPage,
              NotePage.pageAt(result.markdown, targetPage),
            )
          }
        }
        true
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (conflict: PageNoteConflictException) {
        noteStatus = conflict.message
        false
      } catch (conflict: NoteDocumentConflictException) {
        noteStatus = conflict.recoveryCopyName?.let { name ->
          context.getString(R.string.note_conflict_recovery_copy, name)
        } ?: context.getString(R.string.note_conflict)
        false
      } catch (recovery: NoteRecoveryCopyRetainedException) {
        noteStatus = context.getString(R.string.note_recovery_copy_retained, recovery.recoveryCopyName)
        false
      } catch (recovery: NoteWriteRecoveryException) {
        noteStatus = recovery.recoveryCopyName?.let { name ->
          context.getString(
            if (recovery.rollbackSucceeded) R.string.note_write_recovery_rollback
            else R.string.note_write_recovery_failed,
            name,
          )
        } ?: context.getString(R.string.note_save_failed, recovery.message ?: "")
        false
      } catch (error: Exception) {
        noteStatus = context.getString(R.string.note_save_failed, error.message ?: "")
        false
      } finally {
        isSaving = false
      }
    }.await()
  }

  fun reloadCurrentNote() {
    val activeProject = project ?: return
    readerSession.launchSessionTask {
      try {
        val latest = withContext(Dispatchers.IO) {
          ProjectNoteStore(context.contentResolver, activeProject.notesUri).read()
        }
        notesMarkdown = latest
        val freshPage = NotePage.pageAt(latest, pageIndex + 1)
        readerSession.setNoteDraft(freshPage)
        noteBaseRevision = PageNotes.revision(freshPage)
        readerSession.clearCurrentDraftRecovery()
        noteStatus = context.getString(R.string.note_reloaded)
      } catch (error: Exception) {
        noteStatus = context.getString(R.string.note_reload_failed, error.message ?: "")
      }
    }
  }

  suspend fun changePage(nextPage: Int): Boolean {
    val pageCount = renderer?.pageCount
      ?: markdownSource?.let { MarkdownSourcePages.pageCount(it, notesMarkdown) }
      ?: 0
    if (readerOcrState.inputLocked || pdfTextSelectionMode || isImageImporting || isExportingNotes ||
      nextPage !in 0 until pageCount
    ) {
      return false
    }
    if (nextPage == pageIndex) return true
    if (hasUnsavedChanges && !saveCurrentNote()) return false
    readerSession.clearCurrentDraftRecovery()
    noteStatus = null
    showOcrResult = false
    val targetPageNote = NotePage.pageAt(notesMarkdown, nextPage + 1)
    readerSession.setNoteDraft(targetPageNote)
    noteBaseRevision = PageNotes.revision(targetPageNote)
    penStrokes = penPageStore.getItems(PenSide.DOCUMENT, nextPage + 1)
    readerSession.viewportBookmark.value = PdfViewportBookmark.DEFAULT
    pageIndex = nextPage
    return true
  }

  fun recognizeCurrentPage() {
    if (readerOcrState.inputLocked || pdfTextSelectionMode) return
    if (isMarkdownReading) return
    val activeRenderer = renderer ?: return
    val targetPage = pageIndex
    showOcrResult = false
    scope.launch {
      isOcrRunning = true
      try {
        val result = withTimeout(OcrPolicy.TIMEOUT_MS) {
          val engine = ocrEngine ?: OfflineOcrEngine.create(context).also { ocrEngine = it }
          withIoOwnedResource(
            render = { activeRenderer.render(targetPage) },
            dispose = { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() },
            use = { bitmap -> engine.recognize(bitmap) },
          )
        }
        ocrResult = result
        ocrResultPage = targetPage
        showOcrResult = true
      } catch (timeout: TimeoutCancellationException) {
        ocrResult = OcrResult(
          engine = OcrResultMapper.ENGINE,
          status = "failed",
          warnings = listOf(context.getString(R.string.ocr_timeout)),
        )
        ocrResultPage = targetPage
        showOcrResult = true
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        ocrResult = OcrResult(
          engine = OcrResultMapper.ENGINE,
          status = "failed",
          warnings = listOf(error.message ?: context.getString(R.string.offline_ocr_failed)),
        )
        ocrResultPage = targetPage
        showOcrResult = true
      } finally {
        isOcrRunning = false
      }
    }
  }

  fun recognizeSelectedRegion(request: ReaderOcrState) {
    if (isMarkdownReading) return
    val activeRenderer = renderer ?: return
    val activeProject = project ?: return
    val targetPage = request.pageIndex ?: return
    val selection = request.selection ?: return
    val documentId = activeProject.manifest.id
    regionOcrContext = null
    scope.launch {
      var input: Bitmap? = null
      suspend fun finish(result: OcrResult) {
        if (renderer === activeRenderer && project?.manifest?.id == documentId &&
          pageIndex == targetPage && readerOcrState.mode == ReaderOcrMode.RECOGNIZING
        ) {
          regionOcrResult = result
          regionOcrContext = PageTutorRegionContext.fromResult(
            projectId = documentId,
            projectUri = activeProject.sourceDirectoryUri.toString(),
            pageIndex = targetPage,
            sourceRevision = activeRenderer.sourceRevision,
            region = selection,
            rendererIdentity = activeRenderer,
            result = result,
            label = context.getString(R.string.page_tutor_region_label),
            reviewWarning = context.getString(R.string.ocr_review),
          )
          readerOcrState = readerOcrState.showResult()
        }
      }
      try {
        val engine = ocrEngine ?: OfflineOcrEngine.create(context).also { ocrEngine = it }
        val renderedPage = withContext(Dispatchers.IO) {
          activeRenderer.renderWithMetadata(targetPage).also { input = it.bitmap }
        }
        val pageImage = input ?: error("OCR page image is unavailable")
        val regionEngine = selection.toEngineRegion()
        val processor = RegionOcrProcessor(
          ProjectPreparedCache(SafPreparedDocumentAccess(context.contentResolver)),
        )
        val outcome = processor.recognize(
          srcDirectoryUri = activeProject.sourceDirectoryUri.toString(),
          documentId = documentId,
          page = targetPage + 1,
          sourceRevision = activeRenderer.sourceRevision,
          selection = selection,
          imageWidth = pageImage.width,
          imageHeight = pageImage.height,
          renderScale = renderedPage.scale,
          engine = OcrResultMapper.ENGINE,
          notCachedWarning = context.getString(R.string.ocr_not_cached),
          recognize = {
            // Scope the recognition timeout to inference only so a slow SAF
            // write cannot discard a completed OCR result.
            withTimeout(OcrPolicy.TIMEOUT_MS) {
              withContext(Dispatchers.Default) { engine.recognize(pageImage, regionEngine) }
            }
          },
          encodeImages = {
            val pixels = regionEngine.toPixelRect(pageImage.width, pageImage.height)
            val regionImage = Bitmap.createBitmap(
              pageImage, pixels.left, pixels.top, pixels.width, pixels.height,
            )
            try {
              PreparedOcrImages(pageImage.toPngBytes(), regionImage.toPngBytes())
            } finally {
              regionImage.recycle()
            }
          },
        )
        finish(outcome.result)
      } catch (timeout: TimeoutCancellationException) {
        finish(OcrResult(
          engine = OcrResultMapper.ENGINE,
          status = "failed",
          warnings = listOf(context.getString(R.string.ocr_timeout)),
        ))
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        finish(OcrResult(
          engine = OcrResultMapper.ENGINE,
          status = "failed",
          warnings = listOf(error.message ?: context.getString(R.string.offline_ocr_failed)),
        ))
      } finally {
        input?.let { if (!it.isRecycled) it.recycle() }
      }
    }
  }

  fun explainCurrentPage(instruction: String) {
    val activeProject = project ?: return
    val targetPageIndex = pageIndex
    val activeMarkdown = markdownSource
    val activeRenderer = renderer
    if (activeRenderer == null && activeMarkdown == null) return
    val focusContext = pageTutorRegionContext
    fun focusIsCurrent(): Boolean = focusContext == null || (
      activeRenderer != null && renderer === activeRenderer &&
        project?.manifest?.id == activeProject.manifest.id && pageIndex == targetPageIndex &&
        focusContext.matches(
          activeProject.manifest.id,
          activeProject.sourceDirectoryUri.toString(),
          targetPageIndex,
          activeRenderer.sourceRevision,
          activeRenderer,
        )
      )
    if (!focusIsCurrent()) {
      pageTutorError = context.getString(R.string.page_tutor_region_stale)
      return
    }
    scope.launch {
      pageTutorInstruction = instruction
      isPageTutorRunning = true
      pageTutorError = null
      pageTutorResult = null
      pageTutorResultInstruction = null
      try {
        if (!focusIsCurrent()) {
          pageTutorError = context.getString(R.string.page_tutor_region_stale)
          return@launch
        }
        val endpoint = withContext(Dispatchers.IO) { modelSettingsStore.resolveEndpoint() }
        if (hasUnsavedChanges && !saveCurrentNote()) {
          pageTutorError = noteStatus ?: context.getString(R.string.note_save_failed, "")
          return@launch
        }
        if (!focusIsCurrent()) {
          pageTutorError = context.getString(R.string.page_tutor_region_stale)
          return@launch
        }

        val currentNote = noteDraft
        val expectedRevision = noteBaseRevision
        val totalPages = if (activeMarkdown != null) {
          MarkdownSourcePages.pageCount(activeMarkdown, notesMarkdown)
        } else {
          activeRenderer!!.pageCount
        }
        val currentSource: PageTutorSourceText
        val neighbors: List<PageRequest.PageMaterial>
        if (focusContext != null) {
          currentSource = PageTutorSourceText(
            text = "",
            usedOcr = true,
            warnings = focusContext.focus.warnings,
          )
          neighbors = emptyList()
        } else if (activeMarkdown != null) {
          val text = MarkdownSourcePages.pageAt(activeMarkdown, targetPageIndex + 1)
          if (text.isBlank()) {
            throw PageTutorSourceException("Markdown 源本页为空。")
          }
          currentSource = PageTutorSourceText(text = text, usedOcr = false)
          neighbors = listOf(targetPageIndex - 1, targetPageIndex + 1)
            .filter { it in 0 until totalPages }
            .mapNotNull { neighborIndex ->
              val neighborText = MarkdownSourcePages.pageAt(activeMarkdown, neighborIndex + 1)
              if (neighborText.isBlank()) null else PageRequest.PageMaterial(
                page = neighborIndex + 1,
                text = neighborText,
              )
            }
        } else {
          val pdfRenderer = activeRenderer!!
          val pageIdentity = preparationIdentity(activeProject, pdfRenderer)
          val pageSourceLoader = createPdfSourceLoader(pdfRenderer, activeProject)
          val loaded = withTimeout(OcrPolicy.TIMEOUT_MS) {
            val current = loadCurrentPageSource(pageIdentity, pageSourceLoader, targetPageIndex)
            val adjacent = mutableListOf<PageRequest.PageMaterial>()
            listOf(targetPageIndex - 1, targetPageIndex + 1)
              .filter { it in 0 until pdfRenderer.pageCount }
              .forEach { neighborIndex ->
                loadNeighborPageSource(pageIdentity, pageSourceLoader, neighborIndex)?.let { source ->
                  adjacent += PageRequest.PageMaterial(
                    page = neighborIndex + 1,
                    text = source.text,
                  )
                }
              }
            current to adjacent
          }
          currentSource = loaded.first
          neighbors = loaded.second
        }
        val result = pageTutor.explain(
          endpoint = endpoint,
          request = PageTutorRequest(
            documentId = activeProject.manifest.id,
            page = targetPageIndex + 1,
            totalPages = totalPages,
            instruction = instruction,
            sourceText = currentSource.text,
            noteMarkdown = currentNote,
            noteRevision = expectedRevision,
            sourceWarnings = currentSource.warnings,
            neighbors = neighbors,
            focus = focusContext?.focus,
          ),
        )
        if (project?.manifest?.id != activeProject.manifest.id || pageIndex != targetPageIndex ||
          !focusIsCurrent()
        ) {
          pageTutorError = context.getString(R.string.page_tutor_conflict)
        } else {
          pageTutorResult = result
          pageTutorResultInstruction = instruction
        }
      } catch (missing: MissingModelConfigException) {
        pageTutorError = context.getString(R.string.page_tutor_model_missing)
        showAgentModelSettings = true
      } catch (timeout: TimeoutCancellationException) {
        pageTutorError = context.getString(R.string.page_tutor_timeout)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: PageTutorSourceException) {
        pageTutorError = "${context.getString(R.string.page_tutor_source_failed)} ${error.message.orEmpty()}"
      } catch (error: Exception) {
        pageTutorError = "${context.getString(R.string.page_tutor_request_failed)} ${error.message.orEmpty()}"
      } finally {
        isPageTutorRunning = false
      }
    }
  }

  fun appendPageTutorResult() {
    val activeProject = project ?: return
    val preview = pageTutorResult ?: return
    if (!noteWritable) return
    if (preview.page != pageIndex + 1 || preview.noteRevision != noteBaseRevision) {
      pageTutorError = context.getString(R.string.page_tutor_conflict)
      return
    }

    scope.launch {
      isSaving = true
      pageTutorError = null
      try {
        val (edited, recoveryCopyName) = withContext(Dispatchers.IO) {
          val store = ProjectNoteStore(context.contentResolver, activeProject.notesUri)
          val latest = store.read()
          val heading = context.getString(R.string.page_tutor_note_heading)
          val result = PageNoteEditor.appendPage(
            latestMarkdown = latest,
            page = preview.page,
            addition = "$heading\n\n${preview.markdown}",
            expectedRevision = preview.noteRevision,
          )
          val retainedCopy = store.write(result.markdown, expectedOriginal = latest)
          result to retainedCopy
        }
        notesMarkdown = edited.markdown
        readerSession.setNoteDraft(NotePage.pageAt(edited.markdown, preview.page))
        noteBaseRevision = edited.pageRevision
        noteStatus = recoveryCopyName?.let { name ->
          context.getString(R.string.page_tutor_saved_recovery_copy, name)
        } ?: context.getString(R.string.page_tutor_saved)
        pageTutorResult = null
        pageTutorResultInstruction = null
        pageTutorRegionContext = null
        showPageTutor = false
      } catch (conflict: PageNoteConflictException) {
        pageTutorError = context.getString(R.string.page_tutor_conflict)
      } catch (conflict: NoteDocumentConflictException) {
        pageTutorError = conflict.recoveryCopyName?.let { name ->
          context.getString(R.string.page_tutor_conflict_recovery_copy, name)
        } ?: context.getString(R.string.page_tutor_conflict)
      } catch (recovery: NoteRecoveryCopyRetainedException) {
        pageTutorError = context.getString(R.string.page_tutor_recovery_copy_retained, recovery.recoveryCopyName)
      } catch (recovery: NoteWriteRecoveryException) {
        pageTutorError = if (recovery.rollbackSucceeded) {
          recovery.recoveryCopyName?.let { name ->
            context.getString(R.string.page_tutor_recovery_copy_rollback, name)
          } ?: context.getString(R.string.page_tutor_append_failed)
        } else {
          recovery.recoveryCopyName?.let { name ->
            context.getString(R.string.page_tutor_recovery_copy_saved, name)
          } ?: context.getString(R.string.page_tutor_recovery_copy_unavailable)
        }
      } catch (error: Exception) {
        pageTutorError = "${context.getString(R.string.page_tutor_append_failed)} ${error.message.orEmpty()}"
      } finally {
        isSaving = false
      }
    }
  }

  suspend fun executeAgentPage(
    activeProject: OpenedProject,
    activeMarkdown: String?,
    activeRenderer: PdfDocumentSession?,
    targetPage: Int,
    instruction: String,
    endpoint: ModelEndpoint,
  ): AgentEvent {
    val targetPageIndex = targetPage - 1
    val notesSnapshot = withContext(Dispatchers.IO) {
      ProjectNoteStore(context.contentResolver, activeProject.notesUri).read()
    }
    val currentNote = NotePage.pageAt(notesSnapshot, targetPage)
    val expectedRevision = PageNotes.revision(currentNote)
    val totalPages = if (activeMarkdown != null) {
      MarkdownSourcePages.pageCount(activeMarkdown, notesSnapshot)
    } else {
      activeRenderer!!.pageCount
    }
    require(targetPage in 1..totalPages) { "page out of range" }
    var sourceWarnings = emptyList<String>()
    val sourceText: String
    val neighbors: List<Pair<String, Int>>
    if (activeMarkdown != null) {
      sourceText = MarkdownSourcePages.pageAt(activeMarkdown, targetPage)
      neighbors = listOf(targetPageIndex - 1, targetPageIndex + 1)
        .filter { it in 0 until totalPages }
        .mapNotNull { neighborIndex ->
          val text = MarkdownSourcePages.pageAt(activeMarkdown, neighborIndex + 1)
          if (text.isBlank()) null else text to (neighborIndex + 1)
        }
    } else {
      val prepared = withTimeout(OcrPolicy.TIMEOUT_MS) {
        val pdfRenderer = activeRenderer!!
        val pageIdentity = preparationIdentity(activeProject, pdfRenderer)
        val pageSourceLoader = createPdfSourceLoader(pdfRenderer, activeProject)
        val current = loadCurrentPageSource(pageIdentity, pageSourceLoader, targetPageIndex)
        val adjacent = listOf(targetPageIndex - 1, targetPageIndex + 1)
          .filter { it in 0 until pdfRenderer.pageCount }
          .map { neighborIndex ->
            neighborIndex to loadAgentPageSource(pageIdentity, pageSourceLoader, neighborIndex)
          }
        val warnings = buildList {
          current.warnings.forEach { add("Page $targetPage source: $it") }
          adjacent.forEach { (neighborIndex, source) ->
            source.warnings.forEach { add("Page ${neighborIndex + 1} source: $it") }
          }
        }
        Triple(current, adjacent, warnings)
      }
      sourceText = prepared.first.text
      neighbors = prepared.second.map { (neighborIndex, source) -> source.text to (neighborIndex + 1) }
      sourceWarnings = prepared.third
    }
    if (sourceText.isBlank()) {
      throw PageTutorSourceException(context.getString(R.string.page_tutor_source_failed))
    }
    val input = AgentTaskInput.build(
      documentId = activeProject.manifest.id,
      page = targetPage,
      totalPages = totalPages,
      instruction = instruction,
      noteMarkdown = currentNote,
      noteRevision = expectedRevision,
      sourceText = sourceText,
      neighbors = neighbors,
      sourceWarnings = sourceWarnings,
    )
    val handlers = ProjectAgentTools.handlers(
      context.contentResolver,
      activeProject,
      activeRenderer,
      targetPage,
    )
    return agentRunner.run(input.task, endpoint, input.messages, LocalToolDispatcher(handlers))
  }

  fun runAgentTask(instruction: String) {
    val activeProject = project ?: return
    val targetPageIndex = pageIndex
    val activeMarkdown = markdownSource
    val activeRenderer = renderer
    if (activeRenderer == null && activeMarkdown == null) return
    scope.launch {
      agentInstruction = instruction
      isAgentRunning = true
      agentError = null
      agentEvent = null
      try {
        val endpoint = withContext(Dispatchers.IO) { modelSettingsStore.resolveEndpoint() }
        if (hasUnsavedChanges && !saveCurrentNote()) {
          agentError = noteStatus ?: context.getString(R.string.note_save_failed, "")
          return@launch
        }
        val event = executeAgentPage(
          activeProject = activeProject,
          activeMarkdown = activeMarkdown,
          activeRenderer = activeRenderer,
          targetPage = targetPageIndex + 1,
          instruction = instruction,
          endpoint = endpoint,
        )
        if (project?.manifest?.id != activeProject.manifest.id || pageIndex != targetPageIndex) {
          agentError = context.getString(R.string.page_tutor_conflict)
        } else {
          agentEvent = event
          val updated = (event as? AgentEvent.Done)?.updated.orEmpty()
          if (targetPageIndex + 1 in updated) {
            val latest = withContext(Dispatchers.IO) {
              ProjectNoteStore(context.contentResolver, activeProject.notesUri).read()
            }
            notesMarkdown = latest
            val freshPage = NotePage.pageAt(latest, targetPageIndex + 1)
            readerSession.setNoteDraft(freshPage)
            noteBaseRevision = PageNotes.revision(freshPage)
            noteStatus = context.getString(R.string.note_reloaded)
          }
        }
      } catch (missing: MissingModelConfigException) {
        agentError = context.getString(R.string.page_tutor_model_missing)
        showAgentModelSettings = true
      } catch (timeout: TimeoutCancellationException) {
        agentError = context.getString(R.string.page_tutor_timeout)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: PageTutorSourceException) {
        agentError = "${context.getString(R.string.page_tutor_source_failed)} ${error.message.orEmpty()}"
      } catch (error: Exception) {
        agentError = "${context.getString(R.string.page_tutor_request_failed)} ${error.message.orEmpty()}"
      } finally {
        isAgentRunning = false
      }
    }
  }

  fun runAgentBatch(instruction: String, rangeText: String) {
    val activeProject = project ?: return
    val activeMarkdown = markdownSource
    val activeRenderer = renderer
    if (activeRenderer == null && activeMarkdown == null) return
    scope.launch {
      agentInstruction = instruction
      agentPageRange = rangeText
      isAgentRunning = true
      agentError = null
      agentEvent = null
      agentReport = null
      try {
        val endpoint = withContext(Dispatchers.IO) { modelSettingsStore.resolveEndpoint() }
        if (hasUnsavedChanges && !saveCurrentNote()) {
          agentError = noteStatus ?: context.getString(R.string.note_save_failed, "")
          return@launch
        }
        val notesSnapshot = withContext(Dispatchers.IO) {
          ProjectNoteStore(context.contentResolver, activeProject.notesUri).read()
        }
        val totalPages = if (activeMarkdown != null) {
          MarkdownSourcePages.pageCount(activeMarkdown, notesSnapshot)
        } else {
          activeRenderer!!.pageCount
        }
        val pages = try {
          val raw = rangeText.trim().ifBlank { (pageIndex + 1).toString() }
          AgentPageRange.parse(raw, totalPages)
        } catch (error: IllegalArgumentException) {
          agentError = context.getString(R.string.agent_task_bad_range, rangeText.trim())
          return@launch
        }
        val documentId = activeProject.manifest.id
        val report = SerialPages.run(pages) { targetPage ->
          if (project?.manifest?.id != documentId) return@run AgentEvent.Failed("project changed")
          executeAgentPage(
            activeProject = activeProject,
            activeMarkdown = activeMarkdown,
            activeRenderer = activeRenderer,
            targetPage = targetPage,
            instruction = instruction,
            endpoint = endpoint,
          )
        }
        agentReport = report
        agentEvent = if (report.incomplete.isEmpty()) {
          AgentEvent.Done(pages = report.completed, updated = report.updated)
        } else {
          val reason = if (report.stoppedByRateLimit) {
            context.getString(R.string.agent_task_rate_limited, report.incomplete.joinToString(", "))
          } else {
            context.getString(R.string.agent_task_incomplete, report.incomplete.joinToString(", "))
          }
          AgentEvent.Failed(reason)
        }
        if (pageIndex + 1 in report.updated) {
          val latest = withContext(Dispatchers.IO) {
            ProjectNoteStore(context.contentResolver, activeProject.notesUri).read()
          }
          notesMarkdown = latest
          val freshPage = NotePage.pageAt(latest, pageIndex + 1)
          readerSession.setNoteDraft(freshPage)
          noteBaseRevision = PageNotes.revision(freshPage)
          noteStatus = context.getString(R.string.note_reloaded)
        }
      } catch (missing: MissingModelConfigException) {
        agentError = context.getString(R.string.page_tutor_model_missing)
        showAgentModelSettings = true
      } catch (timeout: TimeoutCancellationException) {
        agentError = context.getString(R.string.page_tutor_timeout)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        agentError = "${context.getString(R.string.page_tutor_request_failed)} ${error.message.orEmpty()}"
      } finally {
        isAgentRunning = false
      }
    }
  }

  var showNewProject by rememberSaveable { mutableStateOf(false) }
  var newProjectName by rememberSaveable { mutableStateOf("") }
  var newProjectDir by rememberSaveable { mutableStateOf<Uri?>(null) }
  var newProjectPdfUri by rememberSaveable { mutableStateOf<Uri?>(null) }
  var newProjectPdfName by rememberSaveable { mutableStateOf<String?>(null) }
  var isCreatingProject by remember { mutableStateOf(false) }
  var isChoosingNewProjectDir by rememberSaveable { mutableStateOf(false) }
  var isChoosingNewProjectPdf by rememberSaveable { mutableStateOf(false) }
  var newProjectError by remember { mutableStateOf<String?>(null) }

  fun handleNewProjectDirectoryResult(treeUri: Uri?) {
    isChoosingNewProjectDir = false
    showNewProject = true
    if (treeUri != null) {
      try {
        context.contentResolver.takePersistableUriPermission(
          treeUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
      } catch (_: SecurityException) {
      }
      newProjectDir = treeUri
    }
  }

  val newProjectDirPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
    onResult = ::handleNewProjectDirectoryResult,
  )

  fun chooseNewProjectDirectory() {
    isChoosingNewProjectDir = true
    val pickerForTesting = (activityResultOwner as? MainActivity)?.newProjectDirectoryPickerForTesting
    if (pickerForTesting == null) newProjectDirPicker.launch(null)
    else pickerForTesting(::handleNewProjectDirectoryResult)
  }

  fun releaseNewProjectPdfPermission(uri: Uri?) {
    if (uri == null) return
    scope.launch(Dispatchers.IO) {
      runCatching {
        context.contentResolver.releasePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
      }
    }
  }

  fun clearNewProjectPdf() {
    val previous = newProjectPdfUri
    newProjectPdfUri = null
    newProjectPdfName = null
    releaseNewProjectPdfPermission(previous)
  }

  fun handleNewProjectPdfResult(uri: Uri?) {
    showNewProject = true
    if (uri == null) {
      isChoosingNewProjectPdf = false
      return
    }
    scope.launch {
      val pickerForTesting = (activityResultOwner as? MainActivity)?.newProjectPdfPickerForTesting
      val persistGrant = pickerForTesting == null
      try {
        val displayName = withContext(Dispatchers.IO) {
          if (persistGrant) {
            context.contentResolver.takePersistableUriPermission(
              uri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
          }
          runCatching {
            context.contentResolver.query(
              uri,
              arrayOf(OpenableColumns.DISPLAY_NAME),
              null,
              null,
              null,
            )?.use { cursor ->
              val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
              if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
          }.getOrNull()?.takeIf(String::isNotBlank) ?: "document.pdf"
        }
        val previous = newProjectPdfUri
        newProjectPdfUri = uri
        newProjectPdfName = displayName
        newProjectError = null
        if (previous != uri) releaseNewProjectPdfPermission(previous)
      } catch (_: SecurityException) {
        if (persistGrant) releaseNewProjectPdfPermission(uri)
        newProjectError = context.getString(R.string.new_project_pdf_access_failed)
      } catch (_: Exception) {
        if (persistGrant) releaseNewProjectPdfPermission(uri)
        newProjectError = context.getString(R.string.new_project_pdf_access_failed)
      } finally {
        isChoosingNewProjectPdf = false
      }
    }
  }

  val newProjectPdfPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
    onResult = ::handleNewProjectPdfResult,
  )

  fun chooseNewProjectPdf() {
    isChoosingNewProjectPdf = true
    try {
      val pickerForTesting = (activityResultOwner as? MainActivity)?.newProjectPdfPickerForTesting
      if (pickerForTesting == null) newProjectPdfPicker.launch(arrayOf("application/pdf"))
      else pickerForTesting(arrayOf("application/pdf"), ::handleNewProjectPdfResult)
    } catch (_: Exception) {
      isChoosingNewProjectPdf = false
      newProjectError = context.getString(R.string.new_project_pdf_access_failed)
    }
  }

  fun assetChildExists(assetsUri: Uri, treeUri: Uri, name: String): Boolean {
    val assetsId = DocumentsContract.getDocumentId(assetsUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, assetsId)
    val cursor = context.contentResolver.query(
      childrenUri,
      arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
    ) ?: return false
    cursor.use {
      val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (it.moveToNext()) {
        if (it.getString(nameColumn) == name) return true
      }
    }
    return false
  }

  suspend fun openProjectTree(
    target: ProjectOpenTarget,
    manageOpening: Boolean = true,
    requestedCheckpoint: ReaderSessionCheckpoint? = null,
  ) {
    if (manageOpening) {
      isOpening = true
      errorMessage = null
    }
    try {
      if (project != null) readerSession.flushDraftRecovery()
      val checkpoint = requestedCheckpoint ?: readerSession.checkpointFor(target)
      var writable = true
      if (checkpoint != null && !target.needsPersistablePermission) {
        val grants = context.contentResolver.persistedUriPermissions.filter { it.uri == target.accessTreeUri }
        val hasReadGrant = grants.any { it.isReadPermission }
        if (!hasReadGrant) throw PersistedProjectPermissionMissingException()
        writable = grants.any { it.isWritePermission }
      }
      if (target.needsPersistablePermission) {
        try {
          context.contentResolver.takePersistableUriPermission(
            target.accessTreeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
          )
        } catch (_: SecurityException) {
          writable = false
          context.contentResolver.takePersistableUriPermission(
            target.accessTreeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
      }
      val loaded = withContext(Dispatchers.IO) {
        val opened = ProjectDocumentOpener(context.contentResolver).open(
          target.accessTreeUri,
          target.rootDocumentId,
        )
        val noteStore = ProjectNoteStore(context.contentResolver, opened.notesUri)
        val noteText = noteStore.read()
        val supportsNoteWrite = noteStore.supportsWrite()
        val projectPenStore = ProjectPenStore(
          context.contentResolver,
          opened.sourceDirectoryUri,
          opened.annotationsUri,
        )
        val loadedPenPages = PenPageStore().apply {
          loadAnnotationsMap(
            mapOf(PenSide.DOCUMENT.key to projectPenStore.readPages(PenSide.DOCUMENT)),
          )
        }
        val supportsPenWrite = projectPenStore.supportsWrite()
        val isMarkdown = MarkdownSourcePages.isMarkdownExtension(opened.sourceExtension)
        if (!isMarkdown) {
          require(opened.sourceExtension == "pdf") {
            // UI 侧再做一次本地化；这里只抛英文技术信息避免 IO 线程触碰资源。
            "Unsupported source format: ${opened.sourceExtension.ifBlank { "(empty)" }}"
          }
        }
        val markdownText = if (isMarkdown) {
          context.contentResolver.openInputStream(opened.sourceUri)?.bufferedReader(Charsets.UTF_8)?.use {
            it.readText()
          } ?: throw IllegalStateException("无法读取 Markdown 原文")
        } else {
          null
        }
        val recoveries = readerSession.findDraftRecoveries(target, opened.manifest.id)
        val pdf = if (isMarkdown) {
          null
        } else {
          PdfDocumentSession.open(context, opened.sourceUri, context.cacheDir)
        }
        val savedCheckpoint = checkpoint?.takeIf { it.projectId == opened.manifest.id }
        val pageCount = pdf?.pageCount ?: markdownText?.let {
          MarkdownSourcePages.pageCount(it, noteText)
        } ?: 1
        val draftRecovery = if (savedCheckpoint != null) {
          recoveries.firstOrNull { it.page == savedCheckpoint.pageIndex + 1 }
        } else {
          recoveries.firstOrNull()
        }
        val restoredPageIndex = (savedCheckpoint?.pageIndex ?: draftRecovery?.page?.minus(1) ?: 0)
          .coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val restoredCheckpoint = savedCheckpoint ?: draftRecovery?.let {
          ReaderSessionCheckpoint(
            accessTreeUri = target.accessTreeUri.toString(),
            rootDocumentId = target.rootDocumentId,
            projectId = opened.manifest.id,
            pageIndex = restoredPageIndex,
            viewportBookmark = PdfViewportBookmark.DEFAULT,
            penToolName = PenCanvasTool.PEN.name,
          )
        }
        val missingAssets = try {
          val refs = ProjectAssetRefs.scan(noteText)
          if (refs.isEmpty()) emptyList() else {
            val srcDir = opened.sourceDirectoryUri
            val srcId = DocumentsContract.getDocumentId(srcDir)
            val assetsUri = DocumentsContract.buildDocumentUriUsingTree(srcDir, srcId + "/assets")
            ProjectAssetRefs.missing(refs) { name ->
              runCatching { assetChildExists(assetsUri, srcDir, name) }.getOrDefault(false)
            }
          }
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (_: Exception) {
          emptyList()
        }
        ReaderProjectData(
          project = opened,
          noteText = noteText,
          renderer = pdf,
          markdownSource = markdownText,
          noteWritable = writable && supportsNoteWrite,
          penStore = projectPenStore,
          penPageStore = loadedPenPages,
          penWritable = writable && supportsPenWrite,
          restoredPageIndex = restoredPageIndex,
          checkpoint = restoredCheckpoint,
          draftRecovery = draftRecovery,
          missingAssets = missingAssets,
        )
      }
      readerSession.replaceRenderer(loaded.renderer)
      markdownSource = loaded.markdownSource
      project = loaded.project
      notesMarkdown = loaded.noteText
      penStore = loaded.penStore
      penPageStore = loaded.penPageStore
      penStrokes = loaded.penPageStore.getItems(PenSide.DOCUMENT, loaded.restoredPageIndex + 1)
      penWritable = loaded.penWritable
      failedPenSave = null
      penStatus = if (loaded.penWritable) null else context.getString(R.string.pen_read_only)
      pageIndex = loaded.restoredPageIndex
      val restoredPage = pageIndex + 1
      val savedPage = NotePage.pageAt(loaded.noteText, restoredPage)
      val recoveryDecision = loaded.draftRecovery?.assess(savedPage)
      if (recoveryDecision == NoteDraftRecoveryDecision.ALREADY_SAVED) {
        readerSession.clearDraftRecovery(target, loaded.project.manifest.id, restoredPage)
      }
      val recoveredDraft = when (recoveryDecision) {
        NoteDraftRecoveryDecision.RESTORE_DRAFT,
        NoteDraftRecoveryDecision.RESTORE_CONFLICT -> loaded.draftRecovery?.draft
        else -> null
      }
      readerSession.setNoteDraft(recoveredDraft ?: savedPage)
      noteBaseRevision = loaded.draftRecovery
        ?.takeIf { recoveryDecision == NoteDraftRecoveryDecision.RESTORE_DRAFT ||
          recoveryDecision == NoteDraftRecoveryDecision.RESTORE_CONFLICT }
        ?.baseRevision ?: PageNotes.revision(savedPage)
      noteWritable = loaded.noteWritable
      noteStatus = when {
        recoveryDecision == NoteDraftRecoveryDecision.RESTORE_CONFLICT ->
          context.getString(R.string.note_recovery_conflict)
        recoveryDecision == NoteDraftRecoveryDecision.RESTORE_DRAFT ->
          context.getString(R.string.note_recovery_restored)
        !noteWritable -> context.getString(R.string.read_only_project)
        loaded.missingAssets.isNotEmpty() -> context.getString(
          R.string.note_missing_assets, loaded.missingAssets.take(3).joinToString(", "),
        )
        else -> null
      }
      ocrResult = null
      showOcrResult = false
      regionOcrResult = null
      regionOcrContext = null
      pageTutorRegionContext = null
      readerOcrState = ReaderOcrState()
      readerSession.viewportBookmark.value = loaded.checkpoint?.viewportBookmark ?: PdfViewportBookmark.DEFAULT
      penTool = loaded.checkpoint?.resolvedPenTool() ?: PenCanvasTool.PEN
      readerSession.rememberOpenedProject(target)
      val hasPersistedReadGrant = runCatching {
        context.contentResolver.persistedUriPermissions.any {
          it.uri == target.accessTreeUri && it.isReadPermission
        }
      }.getOrDefault(false)
      if (hasPersistedReadGrant) {
        try {
          recentProjectStore.record(
            RecentProjectEntry(
              accessTreeUri = target.accessTreeUri.toString(),
              rootDocumentId = target.rootDocumentId,
              projectId = loaded.project.manifest.id,
              name = loaded.project.manifest.name.ifBlank { loaded.project.sourceName },
              lastOpenedAtMillis = System.currentTimeMillis(),
            ),
          )
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (_: Exception) {
          noteStatus = context.getString(R.string.recent_projects_save_failed)
        }
      } else {
        noteStatus = context.getString(R.string.recent_projects_permission_not_persisted)
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      val detail = error.message.orEmpty()
      errorMessage = if (error is PersistedProjectPermissionMissingException) {
        context.getString(R.string.note_recovery_permission_missing)
      } else if (detail.startsWith("Unsupported source format:")) {
        val extension = detail.substringAfter("Unsupported source format:").trim().ifBlank { "" }
        context.getString(R.string.unsupported_format, extension.ifBlank { "" })
      } else {
        context.getString(R.string.select_project_error, detail)
      }
    } finally {
      if (manageOpening) isOpening = false
    }
  }

  fun createNewProject() {
    if (isCreatingProject || isChoosingNewProjectDir || isChoosingNewProjectPdf) return
    val name = newProjectName.trim()
    val dir = newProjectDir
    val pdfUri = newProjectPdfUri
    val pdfName = newProjectPdfName
    if (name.isEmpty() || name.contains(Regex("[<>:\"/\\|?*]"))) {
      newProjectError = context.getString(R.string.new_project_bad_name)
      return
    }
    if (dir == null) {
      chooseNewProjectDirectory()
      return
    }
    readerSession.launchSessionTask {
      isCreatingProject = true
      newProjectError = null
      try {
        val projectTarget = withContext(Dispatchers.IO) {
          ProjectCreator(context.contentResolver, context.cacheDir).create(dir, name, pdfUri, pdfName)
        }
        showNewProject = false
        newProjectName = ""
        newProjectDir = null
        clearNewProjectPdf()
        if (hasUnsavedChanges && !saveCurrentNote()) return@launchSessionTask
        openProjectTree(projectTarget, manageOpening = true)
      } catch (error: Exception) {
        newProjectError = context.getString(R.string.new_project_failed, error.message ?: "")
      } finally {
        isCreatingProject = false
      }
    }
  }


  val projectPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { treeUri: Uri? ->
    if (treeUri != null) {
      readerSession.launchSessionTask { openProjectTree(ProjectOpenTarget.fromSelectedTree(treeUri)) }
    }
  }

  val projectAttachmentMimeTypes = remember {
    arrayOf(
      "image/*",
      "application/pdf",
      "text/markdown",
      "text/plain",
      "audio/mpeg",
      "video/mp4",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    )
  }

  fun refreshProjectAttachments() {
    val activeProject = project ?: return
    val projectId = activeProject.manifest.id
    val sourceDirectoryUri = activeProject.sourceDirectoryUri
    isLoadingProjectAttachments = true
    projectAttachmentsError = null
    scope.launch {
      try {
        val entries = withContext(Dispatchers.IO) {
          ProjectAttachmentStore(context.contentResolver, context.cacheDir, sourceDirectoryUri).list()
        }
        if (project?.manifest?.id == projectId && project?.sourceDirectoryUri == sourceDirectoryUri) {
          projectAttachments = entries
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        if (project?.manifest?.id == projectId && project?.sourceDirectoryUri == sourceDirectoryUri) {
          projectAttachmentsError = context.getString(R.string.project_attachments_load_failed)
        }
      } finally {
        if (project?.manifest?.id == projectId && project?.sourceDirectoryUri == sourceDirectoryUri) {
          isLoadingProjectAttachments = false
        }
      }
    }
  }

  fun handleProjectAttachmentImportResult(sourceUri: Uri?) {
    val target = pendingProjectAttachmentImport
    pendingProjectAttachmentImport = null
    if (sourceUri == null || target == null) {
      isImportingProjectAttachment = false
      return
    }
    val (projectId, sourceDirectoryUri) = target
    scope.launch {
      try {
        if (project?.manifest?.id != projectId || project?.sourceDirectoryUri != sourceDirectoryUri) {
          noteStatus = context.getString(R.string.project_attachments_context_changed)
          return@launch
        }
        withContext(NonCancellable + Dispatchers.IO) {
          ProjectAttachmentStore(context.contentResolver, context.cacheDir, sourceDirectoryUri).import(sourceUri)
        }
        if (project?.manifest?.id == projectId && project?.sourceDirectoryUri == sourceDirectoryUri) {
          projectAttachmentsError = null
          refreshProjectAttachments()
        } else {
          noteStatus = context.getString(R.string.project_attachments_context_changed)
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: ProjectAttachmentImportException) {
        projectAttachmentsError = error.residualAssetName?.let {
          context.getString(R.string.project_attachments_import_failed_residual, it)
        } ?: context.getString(R.string.project_attachments_import_failed)
      } catch (_: Exception) {
        projectAttachmentsError = context.getString(R.string.project_attachments_import_failed)
      } finally {
        isImportingProjectAttachment = false
      }
    }
  }

  val projectAttachmentPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
    onResult = ::handleProjectAttachmentImportResult,
  )

  fun launchProjectAttachmentImport() {
    val activeProject = project ?: return
    if (isOpening || isCreatingProject || isSaving || isPenSaving || isImageImporting || isImportingNotes ||
      isExportingNotes || failedPenSave != null || isOcrRunning || isAgentRunning || isPageTutorRunning ||
      isImportingProjectAttachment
    ) return
    pendingProjectAttachmentImport = activeProject.manifest.id to activeProject.sourceDirectoryUri
    isImportingProjectAttachment = true
    projectAttachmentsError = null
    try {
      val pickerForTesting = (activityResultOwner as? MainActivity)?.projectAttachmentPickerForTesting
      if (pickerForTesting == null) {
        projectAttachmentPicker.launch(projectAttachmentMimeTypes)
      } else {
        pickerForTesting(projectAttachmentMimeTypes) { sourceUri ->
          handleProjectAttachmentImportResult(sourceUri)
        }
      }
    } catch (_: Exception) {
      pendingProjectAttachmentImport = null
      isImportingProjectAttachment = false
      projectAttachmentsError = context.getString(R.string.project_attachments_import_failed)
    }
  }

  fun deleteProjectAttachment(entry: ProjectAttachmentEntry) {
    val activeProject = project ?: return
    val projectId = activeProject.manifest.id
    val sourceDirectoryUri = activeProject.sourceDirectoryUri
    val notesForReferenceCheck = "$notesMarkdown\n\n$noteDraft"
    isDeletingProjectAttachment = true
    projectAttachmentsError = null
    scope.launch {
      try {
        val result = withContext(Dispatchers.IO) {
          val store = ProjectAttachmentStore(context.contentResolver, context.cacheDir, sourceDirectoryUri)
          val annotations = store.readAnnotations()
          store.deleteIfUnreferenced(entry.assetName, notesForReferenceCheck, annotations)
        }
        if (project?.manifest?.id != projectId || project?.sourceDirectoryUri != sourceDirectoryUri) {
          return@launch
        }
        when (result) {
          ProjectAttachmentDeleteResult.DELETED -> {
            projectAttachmentsError = null
            noteStatus = null
            refreshProjectAttachments()
          }
          ProjectAttachmentDeleteResult.REFERENCED ->
            projectAttachmentsError = context.getString(R.string.project_attachments_delete_referenced)
          ProjectAttachmentDeleteResult.UNKNOWN_REFERENCES ->
            projectAttachmentsError = context.getString(R.string.project_attachments_delete_unknown)
          ProjectAttachmentDeleteResult.NOT_FOUND,
          ProjectAttachmentDeleteResult.UNSAFE_NAME,
          ProjectAttachmentDeleteResult.PROVIDER_REJECTED,
          -> projectAttachmentsError = context.getString(R.string.project_attachments_delete_failed)
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        if (project?.manifest?.id == projectId && project?.sourceDirectoryUri == sourceDirectoryUri) {
          projectAttachmentsError = context.getString(R.string.project_attachments_delete_unknown)
        }
      } finally {
        isDeletingProjectAttachment = false
      }
    }
  }

  suspend fun saveCurrentPageBeforeSwitch(): Boolean {
    if (readerSession.project.value == null) return true
    val currentPage = readerSession.pageIndex.intValue + 1
    val currentDraft = readerSession.noteDraft.value
    val savedPage = NotePage.pageAt(readerSession.notesMarkdown.value, currentPage)
    return currentDraft == savedPage || saveCurrentNote()
  }

  fun chooseProjectFromRecentDialog() {
    scope.launch {
      if (!saveCurrentPageBeforeSwitch()) return@launch
      showRecentProjectsDialog = false
      projectPicker.launch(null)
    }
  }

  fun openRecentProject(entry: RecentProjectEntry) {
    scope.launch {
      if (!saveCurrentPageBeforeSwitch()) return@launch
      showRecentProjectsDialog = false
      readerSession.launchSessionTask { openProjectTree(entry.toProjectOpenTarget()) }
    }
  }

  fun imageInsertionContextIsCurrent(pending: PendingMarkdownImageInsertion): Boolean =
    project === pending.project &&
      project?.manifest?.id == pending.projectId &&
      project?.sourceDirectoryUri == pending.sourceDirectoryUri &&
      pageIndex == pending.pageIndex &&
      markdownEditor === pending.editor &&
      pending.editor.isEnabled &&
      noteWritable &&
      noteDraft == pending.markdown &&
      pending.editor.markdownSourceText() == pending.markdown

  fun captureMarkdownImageInsertion(): PendingMarkdownImageInsertion? {
    val activeProject = project ?: return null
    val editor = markdownEditor ?: return null
    if (isImageImporting || isOpening || isCreatingProject || isRendering || isSaving || isPenSaving ||
      isOcrRunning || isAgentRunning || isPageTutorRunning || !noteWritable || !editor.isEnabled
    ) return null
    val markdown = editor.markdownSourceText()
    if (markdown != noteDraft) return null
    return PendingMarkdownImageInsertion(
      project = activeProject,
      projectId = activeProject.manifest.id,
      sourceDirectoryUri = activeProject.sourceDirectoryUri,
      pageIndex = pageIndex,
      editor = editor,
      markdown = markdown,
      selectionStart = editor.markdownSelectionStart(),
      selectionEnd = editor.markdownSelectionEnd(),
    )
  }

  fun handleMarkdownImageResult(sourceUri: Uri?) {
    val pending = pendingMarkdownImageInsertion
    pendingMarkdownImageInsertion = null
    if (sourceUri == null || pending == null) {
      isImageImporting = false
      return
    }
    scope.launch {
      var importedForCleanup: ImportedProjectImage? = null
      val importer = ProjectImageImporter(
        context.contentResolver,
        context.cacheDir,
        pending.sourceDirectoryUri,
      )
      try {
        if (!imageInsertionContextIsCurrent(pending)) {
          noteStatus = context.getString(R.string.reader_menu_image_context_changed)
          return@launch
        }
        val imported = withContext(NonCancellable + Dispatchers.IO) {
          importer.import(
            sourceUri,
            fallbackDisplayName = pending.fallbackDisplayName,
            altTextOverride = pending.altTextOverride,
          ).also { importedForCleanup = it }
        }
        if (!imageInsertionContextIsCurrent(pending)) {
          val removed = withContext(Dispatchers.IO) { importer.deleteImported(imported.documentUri) }
          importedForCleanup = null
          noteStatus = if (removed) {
            context.getString(R.string.reader_menu_image_context_changed)
          } else {
            context.getString(R.string.reader_menu_image_orphaned, imported.assetName)
          }
          return@launch
        }
        val reference = MarkdownImageInsertion.createReference(imported.altText, imported.assetName)
        val edit = MarkdownImageInsertion.createEdit(
          pending.markdown,
          pending.selectionStart,
          pending.selectionEnd,
          reference,
        )
        pending.editor.requestFocus()
        pending.editor.replaceMarkdownSourceRange(edit.replaceStart, edit.replaceEnd, edit.replacement)
        pending.editor.setMarkdownSourceSelection(edit.cursor)
        importedForCleanup = null
        noteStatus = null
      } catch (cancelled: CancellationException) {
        importedForCleanup?.let { imported ->
          withContext(NonCancellable + Dispatchers.IO) { importer.deleteImported(imported.documentUri) }
        }
        throw cancelled
      } catch (error: ProjectImageImportException) {
        noteStatus = error.residualAssetName?.let {
          context.getString(R.string.reader_menu_image_orphaned, it)
        } ?: context.getString(R.string.reader_menu_image_import_failed)
      } catch (_: Exception) {
        importedForCleanup?.let { imported ->
          val removed = withContext(NonCancellable + Dispatchers.IO) {
            importer.deleteImported(imported.documentUri)
          }
          if (!removed) {
            noteStatus = context.getString(R.string.reader_menu_image_orphaned, imported.assetName)
            importedForCleanup = null
            return@launch
          }
        }
        noteStatus = context.getString(R.string.reader_menu_image_import_failed)
      } finally {
        isImageImporting = false
      }
    }
  }

  val markdownImagePicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
    onResult = ::handleMarkdownImageResult,
  )

  fun noteImportContextIsCurrent(pending: PendingNoteImportContext): Boolean =
    project?.manifest?.id == pending.projectId &&
      project?.sourceDirectoryUri == pending.sourceDirectoryUri &&
      pageIndex == pending.pageIndex &&
      noteDraft == pending.markdown

  fun currentSourcePageCount(): Int = renderer?.pageCount
    ?: markdownSource?.let { MarkdownSourcePages.pageCount(it, notesMarkdown) }
    ?: 0

  fun captureNoteImportContext(): PendingNoteImportContext? {
    val activeProject = project ?: return null
    if (!noteWritable) return null
    val editor = markdownEditor?.takeIf { it.isEnabled && it.markdownSourceText() == noteDraft }
    val markdown = editor?.markdownSourceText() ?: noteDraft
    return PendingNoteImportContext(
      projectId = activeProject.manifest.id,
      sourceDirectoryUri = activeProject.sourceDirectoryUri,
      pageIndex = pageIndex,
      markdown = markdown,
      selectionStart = editor?.markdownSelectionStart() ?: markdown.length,
      selectionEnd = editor?.markdownSelectionEnd() ?: markdown.length,
    )
  }

  suspend fun applyPendingNoteImport() {
    val pending = pendingNoteImportBackup ?: return
    showNoteImportConfirmation = false
    try {
      if (!noteImportContextIsCurrent(pending.context)) {
        noteStatus = context.getString(R.string.reader_menu_import_stale)
        return
      }
      if (hasUnsavedChanges && !saveCurrentNote()) return
      if (!noteImportContextIsCurrent(pending.context)) {
        noteStatus = context.getString(R.string.reader_menu_import_stale)
        return
      }
      val activeProject = project ?: return
      val pageCount = currentSourcePageCount()
      if (pageCount <= 0) {
        noteStatus = context.getString(R.string.reader_menu_import_failed)
        return
      }
      val result = withContext(Dispatchers.IO) {
        val store = ProjectNoteStore(context.contentResolver, activeProject.notesUri)
        store.withRevisionLock {
          val original = store.read()
          val merged = NoteImportPayload.merge(original, pageCount, pending.pages)
          val changedPages = pending.pages.entries
            .filter { (page, markdown) -> NotePage.pageAt(original, page) != markdown }
            .map { it.key to it.value }
          val recoveryCopyName = if (merged == original) null else {
            store.write(merged, expectedOriginal = original)
          }
          changedPages.forEach { (page, markdown) ->
            runCatching {
              NoteHistoryMirror.snapshotPage(context.filesDir, pending.context.projectId, page, markdown)
            }
          }
          NoteImportWriteResult(merged, recoveryCopyName)
        }
      }
      val currentPage = pending.context.pageIndex + 1
      val currentMarkdown = NotePage.pageAt(result.markdown, currentPage)
      notesMarkdown = result.markdown
      readerSession.setNoteDraft(currentMarkdown)
      noteBaseRevision = PageNotes.revision(currentMarkdown)
      readerSession.refreshDraftRecoveryAfterSave(currentPage, currentMarkdown)
      noteStatus = result.recoveryCopyName?.let {
        context.getString(R.string.note_saved_recovery_copy, it)
      } ?: context.getString(R.string.reader_menu_import_success)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: NoteDocumentConflictException) {
      noteStatus = context.getString(R.string.note_conflict)
    } catch (_: Exception) {
      noteStatus = context.getString(R.string.reader_menu_import_failed)
    } finally {
      isImportingNotes = false
      pendingNoteImportContext = null
      pendingNoteImportBackup = null
    }
  }

  suspend fun finishNoteImport(uri: Uri?) {
    val pending = pendingNoteImportContext
    if (uri == null || pending == null) {
      isImportingNotes = false
      pendingNoteImportContext = null
      pendingNoteImportBackup = null
      noteStatus = null
      return
    }
    try {
      if (!noteImportContextIsCurrent(pending)) {
        noteStatus = context.getString(R.string.reader_menu_import_stale)
        return
      }
      val (displayName, mimeType, content) = withContext(Dispatchers.IO) {
        val displayName = runCatching {
          context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
              if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
              } else null
            }
        }.getOrNull()
        val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val content = context.contentResolver.openInputStream(uri)
          ?.bufferedReader(Charsets.UTF_8)
          ?.use { it.readText() }
          ?: throw IllegalStateException("无法读取所选文件")
        Triple(displayName, mimeType, content)
      }
      if (NoteImportPayload.isJson(displayName, mimeType)) {
        val currentPageCount = currentSourcePageCount()
        val updates = withContext(Dispatchers.Default) {
          NoteImportPayload.parseBackup(content, currentPageCount)
        }
        if (!noteImportContextIsCurrent(pending)) {
          noteStatus = context.getString(R.string.reader_menu_import_stale)
          return
        }
        pendingNoteImportBackup = PendingNoteImportBackup(pending, updates)
        showNoteImportConfirmation = true
        return
      }
      if (!noteImportContextIsCurrent(pending)) {
        noteStatus = context.getString(R.string.reader_menu_import_stale)
        return
      }
      val edit = NoteImportPayload.insertText(
        markdown = pending.markdown,
        selectionStart = pending.selectionStart,
        selectionEnd = pending.selectionEnd,
        text = content,
      )
      val updated = buildString(pending.markdown.length + content.length) {
        append(pending.markdown, 0, edit.replaceStart)
        append(edit.replacement)
        append(pending.markdown, edit.replaceEnd, pending.markdown.length)
      }
      val editor = markdownEditor?.takeIf {
        it.isEnabled && it.markdownSourceText() == pending.markdown
      }
      if (editor != null) {
        editor.requestFocus()
        editor.replaceMarkdownSourceRange(edit.replaceStart, edit.replaceEnd, edit.replacement)
        editor.setMarkdownSourceSelection(edit.cursor)
      } else {
        readerSession.setNoteDraft(updated)
        noteEditRequest++
      }
      noteStatus = context.getString(R.string.reader_menu_import_success)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: NoteImportFormatException) {
      noteStatus = context.getString(R.string.reader_menu_import_invalid_backup)
    } catch (_: Exception) {
      noteStatus = context.getString(R.string.reader_menu_import_failed)
    } finally {
      isImportingNotes = showNoteImportConfirmation
      if (!showNoteImportConfirmation) pendingNoteImportContext = null
    }
  }

  val noteImportPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
    onResult = { uri -> readerSession.launchSessionTask { finishNoteImport(uri) } },
  )

  fun beginNoteImport() {
    val pending = captureNoteImportContext() ?: return
    pendingNoteImportContext = pending
    pendingNoteImportBackup = null
    isImportingNotes = true
    noteStatus = null
    val mimeTypes = arrayOf("application/json", "text/markdown", "text/plain")
    try {
      val pickerForTesting = (activityResultOwner as? MainActivity)?.noteImportPickerForTesting
      if (pickerForTesting == null) noteImportPicker.launch(mimeTypes)
      else pickerForTesting(mimeTypes) { uri ->
        readerSession.launchSessionTask { finishNoteImport(uri) }
      }
    } catch (_: Exception) {
      isImportingNotes = false
      pendingNoteImportContext = null
      noteStatus = context.getString(R.string.reader_menu_import_failed)
    }
  }

  suspend fun finishNoteExport(uri: Uri?) {
    readerSession.isHandlingNoteExportResult.value = true
    showPendingNoteExportRecoveryDialog = false
    try {
      if (uri == null) {
        readerSession.clearPendingNoteExport()
        noteStatus = null
        return
      }

      val file = try {
        pendingNoteExport ?: readerSession.readPendingNoteExport()
      } catch (error: Exception) {
        noteStatus = context.getString(
          R.string.reader_menu_export_failed,
          error.message ?: error.javaClass.simpleName,
        )
        return
      }
      if (file == null) {
        noteStatus = context.getString(
          R.string.reader_menu_export_failed,
          context.getString(R.string.reader_menu_export_snapshot_missing),
        )
        return
      }

      try {
        withContext(Dispatchers.IO) {
          val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("The selected destination could not be opened.")
          output.use { it.write(file.content.toByteArray(Charsets.UTF_8)) }
        }
        noteStatus = context.getString(R.string.reader_menu_export_success)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        noteStatus = context.getString(
          R.string.reader_menu_export_failed,
          error.message ?: error.javaClass.simpleName,
        )
      } finally {
        readerSession.clearPendingNoteExport()
      }
    } finally {
      readerSession.isHandlingNoteExportResult.value = false
    }
  }

  val jsonNoteExportPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json"),
  ) { uri -> readerSession.launchSessionTask { finishNoteExport(uri) } }
  val markdownNoteExportPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("text/markdown"),
  ) { uri -> readerSession.launchSessionTask { finishNoteExport(uri) } }

  suspend fun launchNoteExport(file: NoteExportFile, persistSnapshot: Boolean = true) {
    try {
      if (persistSnapshot) {
        readerSession.storePendingNoteExport(file)
      } else {
        readerSession.pendingNoteExport.value = file
        readerSession.isPendingNoteExportRecovered.value = false
      }
      noteStatus = null
      val pickerForTesting = (activityResultOwner as? MainActivity)?.noteExportPickerForTesting
      if (pickerForTesting != null) {
        pickerForTesting(file.mimeType, file.fileName) { uri ->
          readerSession.launchSessionTask { finishNoteExport(uri) }
        }
      } else if (file.mimeType == "application/json") {
        jsonNoteExportPicker.launch(file.fileName)
      } else {
        markdownNoteExportPicker.launch(file.fileName)
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      if (persistSnapshot) {
        readerSession.clearPendingNoteExport()
      } else {
        readerSession.isPendingNoteExportRecovered.value = true
      }
      noteStatus = context.getString(
        R.string.reader_menu_export_failed,
        error.message ?: error.javaClass.simpleName,
      )
    }
  }

  suspend fun resumePendingNoteExport() {
    showPendingNoteExportRecoveryDialog = false
    if (isExportingNotes) return
    isPreparingNoteExport = true
    try {
      val file = pendingNoteExport ?: readerSession.readPendingNoteExport()
      if (file == null) {
        noteStatus = context.getString(
          R.string.reader_menu_export_failed,
          context.getString(R.string.reader_menu_export_snapshot_missing),
        )
        return
      }
      launchNoteExport(file, persistSnapshot = false)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      noteStatus = context.getString(
        R.string.reader_menu_export_failed,
        error.message ?: error.javaClass.simpleName,
      )
    } finally {
      isPreparingNoteExport = false
    }
  }

  suspend fun discardPendingNoteExport() {
    showPendingNoteExportRecoveryDialog = false
    readerSession.isHandlingNoteExportResult.value = true
    try {
      readerSession.clearPendingNoteExport()
      noteStatus = null
    } finally {
      readerSession.isHandlingNoteExportResult.value = false
    }
  }

  suspend fun prepareNoteExport(fullBackup: Boolean) {
    showNoteExportDialog = false
    val activeProject = project ?: return
    if (isExportingNotes) return
    isPreparingNoteExport = true
    try {
      if (noteWritable && hasUnsavedChanges && !saveCurrentNote()) return

      val currentPage = pageIndex + 1
      val readOnlyDraft = noteDraft.takeIf { !noteWritable && hasUnsavedChanges }
      val projectName = activeProject.name
      val notesSnapshot = notesMarkdown
      val currentPageMarkdown = readOnlyDraft ?: NotePage.pageAt(notesSnapshot, currentPage)
      val sourcePageCount = renderer?.pageCount
        ?: markdownSource?.let { MarkdownSourcePages.splitSource(it).size.coerceAtLeast(1) }
        ?: 0
      if (fullBackup && sourcePageCount == 0) {
        noteStatus = context.getString(R.string.reader_menu_export_no_source_pages)
        return
      }
      val file = withContext(Dispatchers.Default) {
        if (fullBackup) {
          NoteExportPayload.fullBackup(
            projectName = projectName,
            sourcePageCount = sourcePageCount,
            notesMarkdown = notesSnapshot,
            currentPage = currentPage.takeIf { readOnlyDraft != null },
            currentDraft = readOnlyDraft,
          )
        } else {
          NoteExportPayload.currentPage(
            projectName = projectName,
            page = currentPage,
            markdown = currentPageMarkdown,
          )
        }
      }
      launchNoteExport(file)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      noteStatus = context.getString(
        R.string.reader_menu_export_failed,
        error.message ?: error.javaClass.simpleName,
      )
    } finally {
      isPreparingNoteExport = false
    }
  }

  fun chooseMarkdownImage() {
    val pending = captureMarkdownImageInsertion() ?: return
    pendingMarkdownImageInsertion = pending
    isImageImporting = true
    try {
      val pickerForTesting = (activityResultOwner as? MainActivity)?.markdownImagePickerForTesting
      if (pickerForTesting == null) {
        markdownImagePicker.launch(arrayOf("image/*"))
      } else {
        pickerForTesting(::handleMarkdownImageResult)
      }
    } catch (_: Exception) {
      pendingMarkdownImageInsertion = null
      isImageImporting = false
      noteStatus = context.getString(R.string.reader_menu_image_import_failed)
    }
  }

  fun pasteMarkdownImage() {
    val pending = captureMarkdownImageInsertion() ?: return
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
    if (clip == null || clip.itemCount == 0) {
      noteStatus = context.getString(R.string.reader_menu_paste_image_no_image)
      return
    }
    pendingMarkdownImageInsertion = pending
    isImageImporting = true
    scope.launch {
      try {
        val candidate = withContext(Dispatchers.IO) {
          ClipboardImageSelection.firstSupported(
            sequence {
              for (index in 0 until clip.itemCount) {
                val uri = clip.getItemAt(index).uri ?: continue
                val uriString = uri.toString()
                if (!ClipboardImageSelection.isContentUri(uriString)) continue
                val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                yield(ClipboardImageCandidate(uriString, mimeType))
              }
            }.asIterable(),
          )
        }
        if (pendingMarkdownImageInsertion !== pending) return@launch
        if (candidate == null) {
          pendingMarkdownImageInsertion = null
          isImageImporting = false
          noteStatus = context.getString(R.string.reader_menu_paste_image_no_image)
          return@launch
        }
        val mimeType = requireNotNull(candidate.mimeType)
        val fallbackName = requireNotNull(ClipboardImageSelection.fallbackDisplayName(mimeType))
        pendingMarkdownImageInsertion = pending.copy(
          fallbackDisplayName = fallbackName,
          altTextOverride = context.getString(R.string.reader_menu_paste_image_alt),
        )
        handleMarkdownImageResult(Uri.parse(requireNotNull(candidate.uri)))
      } catch (cancelled: CancellationException) {
        if (pendingMarkdownImageInsertion === pending) {
          pendingMarkdownImageInsertion = null
          isImageImporting = false
        }
        throw cancelled
      } catch (_: Exception) {
        if (pendingMarkdownImageInsertion === pending) {
          pendingMarkdownImageInsertion = null
          isImageImporting = false
          noteStatus = context.getString(R.string.reader_menu_image_import_failed)
        }
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose { pageBitmap?.recycleAfterDrawFrames() }
  }
  val ocrEngineToClose = ocrEngine
  DisposableEffect(ocrEngineToClose) {
    onDispose {
      ocrEngineToClose?.let { engine ->
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { engine.close() }
      }
    }
  }

  LaunchedEffect(readerSession) {
    val checkpoint = readerSession.claimRestorationCheckpoint() ?: return@LaunchedEffect
    openProjectTree(
      target = checkpoint.toProjectOpenTarget(),
      requestedCheckpoint = checkpoint,
    )
  }

  LaunchedEffect(project, pageIndex, readerSession.viewportBookmark.value, penTool) {
    if (project == null) return@LaunchedEffect
    readerSession.saveCheckpoint()
  }

  LaunchedEffect(pageIndex, penPageStore) {
    penStrokes = penPageStore.getItems(PenSide.DOCUMENT, pageIndex + 1)
  }

  LaunchedEffect(renderer, pageIndex) {
    val activeRenderer = renderer ?: return@LaunchedEffect
    isRendering = true
    try {
      val rendered = withContext(Dispatchers.IO) { activeRenderer.render(pageIndex) }
      if (renderer === activeRenderer) {
        val previous = pageBitmap
        pageBitmap = rendered
        previous?.recycleAfterDrawFrames()
        errorMessage = null
      } else {
        rendered.recycle()
      }
    } catch (error: Exception) {
      errorMessage = error.message ?: context.getString(R.string.page_render_failed)
    } finally {
      isRendering = false
    }
  }

  val activeRenderer = renderer
  val activeProject = project
  val activeMarkdown = markdownSource
  DisposableEffect(pagePrefetcher) {
    onDispose { pagePrefetcher.cancel() }
  }
  LaunchedEffect(
    activeProject?.sourceDirectoryUri,
    activeProject?.manifest?.id,
    activeRenderer,
    activeRenderer?.sourceRevision,
    activeMarkdown,
    pageIndex,
    settings.prepareAheadPages,
  ) {
    val currentProject = activeProject
    val currentRenderer = activeRenderer
    if (currentProject == null || currentRenderer == null || activeMarkdown != null) {
      pagePrefetcher.cancel()
      return@LaunchedEffect
    }
    val identity = preparationIdentity(currentProject, currentRenderer)
    val loader = createPdfSourceLoader(currentRenderer, currentProject)
    pagePrefetcher.cached(identity, pageIndex)?.warnings?.takeIf { it.isNotEmpty() }?.let { warnings ->
      noteStatus = context.getString(
        R.string.prepare_page_warning,
        pageIndex + 1,
        warnings.joinToString(" "),
      )
    }
    pagePrefetcher.schedule(
      scope = this,
      identity = identity,
      currentPageIndex = pageIndex,
      pageCount = currentRenderer.pageCount,
      prepareAheadPages = settings.prepareAheadPages,
      prepare = { key ->
        val prepared = withTimeout(OcrPolicy.TIMEOUT_MS) { loader.loadCurrent(key.pageIndex) }
        if (key.pageIndex == pageIndex && project === currentProject && renderer === currentRenderer &&
          prepared.warnings.isNotEmpty()
        ) {
          noteStatus = context.getString(
            R.string.prepare_page_warning,
            key.pageIndex + 1,
            prepared.warnings.joinToString(" "),
          )
        }
        prepared
      },
      onFailure = { key, error ->
        if (key.pageIndex == pageIndex && project === currentProject && renderer === currentRenderer) {
          noteStatus = context.getString(
            R.string.prepare_page_failed,
            key.pageIndex + 1,
            error.message.orEmpty(),
          )
        }
      },
    ).join()
  }
  val readerInputLocked = readerOcrState.inputLocked || pdfTextSelectionMode
  LaunchedEffect(
    activeProject?.manifest?.id,
    activeProject?.sourceDirectoryUri,
    activeProject?.annotationsUri,
    activeRenderer,
    activeMarkdown,
    pageIndex,
    annotationOverlayReload,
  ) {
    annotationOverlayItems = emptyList()
    annotationPageItems = JsonArray(emptyList())
    annotationOverlayImages.values.forEach(Bitmap::recycleAfterDrawFrames)
    annotationOverlayImages = emptyMap()
    annotationOverlayHtmlBitmaps.values.forEach(Bitmap::recycleAfterDrawFrames)
    annotationOverlayHtmlBitmaps = emptyMap()
    val currentProject = activeProject
    if (currentProject != null && activeRenderer != null) {
      val targetPage = pageIndex + 1
      val loaded = withContext(Dispatchers.IO) {
        runCatching {
          val page = ProjectAnnotationStore(
            context.contentResolver,
            currentProject.sourceDirectoryUri,
            currentProject.annotationsUri,
          ).readPage("document", pageIndex + 1)
          val items = PdfAnnotationOverlayItem.fromPage(page)
          val imageLoader = PdfAnnotationImageLoader(context.contentResolver, currentProject.sourceDirectoryUri)
          val images = linkedMapOf<String, Bitmap>()
          val htmlBitmaps = linkedMapOf<Int, Bitmap>()
          var decodedPixels = 0L
          items.mapNotNull(PdfAnnotationOverlayItem::imageAssetName)
            .distinct()
            .forEach { assetName ->
              val image = imageLoader.load(assetName) ?: return@forEach
              val pixels = image.width.toLong() * image.height
              if (decodedPixels + pixels <= MAX_ANNOTATION_PAGE_IMAGE_PIXELS) {
                images[assetName] = image
                decodedPixels += pixels
              } else {
                image.recycle()
              }
            }
          items.filter { it.type == "html" }.forEach { overlayItem ->
            val item = page.getOrNull(overlayItem.sourceIndex) as? JsonObject ?: return@forEach
            val bounds = PdfAnnotationClipboard.bounds(item) ?: return@forEach
            val width = (bounds.right - bounds.left).roundToInt().coerceAtLeast(1)
            val height = (bounds.bottom - bounds.top).roundToInt().coerceAtLeast(1)
            if (width > RichHtmlAnnotationRenderer.MAX_SIDE_PX || height > RichHtmlAnnotationRenderer.MAX_SIDE_PX) {
              return@forEach
            }
            val density = context.resources.displayMetrics.density
            val expectedWidth = kotlin.math.ceil(width * density).toLong()
            val expectedHeight = kotlin.math.ceil(height * density).toLong()
            if (expectedWidth * expectedHeight > MAX_ANNOTATION_PAGE_IMAGE_PIXELS - decodedPixels) {
              return@forEach
            }
            val html = overlayItem.html ?: return@forEach
            val rendered = runCatching {
              richHtmlRenderer.render(html, width, height)
            }.getOrNull() ?: return@forEach
            val pixels = rendered.bitmap.width.toLong() * rendered.bitmap.height
            if (decodedPixels + pixels <= MAX_ANNOTATION_PAGE_IMAGE_PIXELS) {
              htmlBitmaps[overlayItem.sourceIndex] = rendered.bitmap
              decodedPixels += pixels
            } else {
              rendered.bitmap.recycle()
            }
          }
          AnnotationOverlayLoad(page, items, images, htmlBitmaps)
        }.getOrElse {
          AnnotationOverlayLoad(JsonArray(emptyList()), emptyList(), emptyMap(), emptyMap())
        }
      }
      if (
        project?.manifest?.id != currentProject.manifest.id ||
        project?.sourceDirectoryUri != currentProject.sourceDirectoryUri ||
        renderer !== activeRenderer || pageIndex + 1 != targetPage
      ) {
        loaded.images.values.forEach { if (!it.isRecycled) it.recycle() }
        loaded.htmlBitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
        return@LaunchedEffect
      }
      annotationPageItems = loaded.page
      annotationOverlayItems = loaded.items
      annotationOverlayImages = loaded.images
      annotationOverlayHtmlBitmaps = loaded.htmlBitmaps
    }
  }
  val totalPages = when {
    activeProject == null -> 0
    activeRenderer != null -> activeRenderer.pageCount
    activeMarkdown != null -> MarkdownSourcePages.pageCount(activeMarkdown, notesMarkdown)
    else -> 0
  }
  val menuBusy = isOpening || isCreatingProject || isRendering || isSaving || isPenSaving || isImageImporting ||
    isImportingNotes || isExportingNotes || failedPenSave != null || isOcrRunning || isSelectablePageTextLoading ||
    readerInputLocked || isAgentRunning || isPageTutorRunning || isLoadingProjectAttachments ||
    isImportingProjectAttachment || isDeletingProjectAttachment
  val unavailableReason = context.getString(R.string.reader_menu_unavailable_reason)
  val busyReason = context.getString(R.string.reader_menu_busy)

  fun commandReason(
    requireProject: Boolean = false,
    requirePdf: Boolean = false,
    requireWritable: Boolean = false,
    busy: Boolean = menuBusy,
  ): String? = when {
    busy -> busyReason
    requireProject && activeProject == null -> context.getString(R.string.reader_menu_project_required)
    requirePdf && isMarkdownReading -> context.getString(R.string.reader_menu_pdf_required)
    requireWritable && !noteWritable -> context.getString(R.string.reader_menu_read_only)
    else -> null
  }

  fun actionCommand(
    id: String,
    labelRes: Int,
    enabled: Boolean,
    disabledReason: String? = null,
    onSelect: () -> Unit,
  ) = ReaderMenuCommand(
    id = id,
    label = context.getString(labelRes),
    enabled = enabled,
    disabledReason = if (enabled) null else disabledReason ?: unavailableReason,
    onSelect = onSelect,
  )

  val canFormatMarkdown = markdownEditor?.isEnabled == true && noteWritable && !menuBusy
  val canUndoMarkdown = remember(markdownHistoryRevision, canFormatMarkdown) {
    canFormatMarkdown && markdownHistory.canUndo
  }
  val canRedoMarkdown = remember(markdownHistoryRevision, canFormatMarkdown) {
    canFormatMarkdown && markdownHistory.canRedo
  }
  val markdownFormatDisabledReason = commandReason(requireProject = true, requireWritable = true)
    ?: if (!canFormatMarkdown) context.getString(R.string.reader_menu_note_editor_required) else null
  val markdownUndoDisabledReason = markdownFormatDisabledReason
    ?: if (!markdownHistory.canUndo) context.getString(R.string.reader_menu_nothing_to_undo) else null
  val markdownRedoDisabledReason = markdownFormatDisabledReason
    ?: if (!markdownHistory.canRedo) context.getString(R.string.reader_menu_nothing_to_redo) else null
  val hasNoteSelection = noteSelection?.let { it.start != it.end } == true
  val markdownClipboardDisabledReason = commandReason(requireProject = true, requireWritable = true)
    ?: if (!canFormatMarkdown) context.getString(R.string.reader_menu_text_editor_required) else null
  val markdownSelectionDisabledReason = markdownClipboardDisabledReason
    ?: if (!hasNoteSelection) context.getString(R.string.reader_menu_text_selection_required) else null
  val canInsertMarkdownImage = markdownEditor?.isEnabled == true && noteWritable && !menuBusy
  val markdownImageDisabledReason = commandReason(requireProject = true, requireWritable = true)
    ?: if (!canInsertMarkdownImage) context.getString(R.string.reader_menu_image_editor_required) else null

  fun applyMarkdownHistory(undo: Boolean) {
    val editor = markdownEditor ?: return
    if (!editor.isEnabled || !noteWritable || menuBusy) return
    val current = editor.markdownSourceText()
    val restored = if (undo) markdownHistory.undo(current) else markdownHistory.redo(current)
    markdownHistoryRevision++
    if (restored == null) return
    isApplyingMarkdownHistory = true
    try {
      editor.requestFocus()
      editor.setMarkdownSourceText(restored)
      editor.setMarkdownSourceSelection(restored.length)
    } finally {
      isApplyingMarkdownHistory = false
    }
  }

  fun applyMarkdownInlineFormatting(style: MarkdownInlineStyle) {
    val editor = markdownEditor ?: return
    if (!editor.isEnabled || !noteWritable || menuBusy) return
    val edit = MarkdownInlineFormatting.wrapSelection(
      markdown = editor.markdownSourceText(),
      selectionStart = editor.markdownSelectionStart(),
      selectionEnd = editor.markdownSelectionEnd(),
      style = style,
      placeholder = context.getString(R.string.reader_menu_format_placeholder),
    )
    editor.requestFocus()
    editor.replaceMarkdownSourceRange(edit.replaceStart, edit.replaceEnd, edit.replacement)
    editor.setMarkdownSourceSelection(edit.selectionStart, edit.selectionEnd)
  }

  fun applyMarkdownParagraphFormatting(prefix: MarkdownParagraphPrefix) {
    val editor = markdownEditor ?: return
    if (!editor.isEnabled || !noteWritable || menuBusy) return
    val edit = MarkdownParagraphFormatting.prefixLines(
      markdown = editor.markdownSourceText(),
      selectionStart = editor.markdownSelectionStart(),
      selectionEnd = editor.markdownSelectionEnd(),
      prefix = prefix,
    )
    editor.requestFocus()
    editor.replaceMarkdownSourceRange(edit.replaceStart, edit.replaceEnd, edit.replacement)
    editor.setMarkdownSourceSelection(edit.cursor)
  }

  fun applyMarkdownCodeBlockInsertion() {
    val editor = markdownEditor ?: return
    if (!editor.isEnabled || !noteWritable || menuBusy) return
    val edit = MarkdownCodeBlockInsertion.createEdit(
      markdown = editor.markdownSourceText(),
      selectionStart = editor.markdownSelectionStart(),
      selectionEnd = editor.markdownSelectionEnd(),
      placeholder = context.getString(R.string.reader_menu_code_placeholder),
    )
    editor.requestFocus()
    editor.replaceMarkdownSourceRange(edit.replaceStart, edit.replaceEnd, edit.replacement)
    editor.setMarkdownSourceSelection(edit.selectionStart, edit.selectionEnd)
  }

  fun applyMarkdownFormulaInsertion() {
    val editor = markdownEditor ?: return
    if (!editor.isEnabled || !noteWritable || menuBusy) return
    val edit = MarkdownFormulaInsertion.createEdit(
      markdown = editor.markdownSourceText(),
      selectionStart = editor.markdownSelectionStart(),
      selectionEnd = editor.markdownSelectionEnd(),
    )
    editor.requestFocus()
    editor.replaceMarkdownSourceRange(edit.replaceStart, edit.replaceEnd, edit.replacement)
    editor.setMarkdownSourceSelection(edit.selectionStart, edit.selectionEnd)
  }

  fun applyMarkdownTableInsertion() {
    val editor = markdownEditor ?: return
    if (!editor.isEnabled || !noteWritable || menuBusy) return
    val edit = MarkdownTableInsertion.createEdit(
      markdown = editor.markdownSourceText(),
      selectionStart = editor.markdownSelectionStart(),
      selectionEnd = editor.markdownSelectionEnd(),
    )
    editor.requestFocus()
    editor.replaceMarkdownSourceRange(edit.replaceStart, edit.replaceEnd, edit.replacement)
    editor.setMarkdownSourceSelection(edit.cursor)
  }

  fun applyMarkdownLinkInsertion() {
    val editor = markdownEditor ?: return
    if (!editor.isEnabled || !noteWritable || menuBusy) return
    val edit = MarkdownLinkInsertion.createEdit(
      markdown = editor.markdownSourceText(),
      selectionStart = editor.markdownSelectionStart(),
      selectionEnd = editor.markdownSelectionEnd(),
      placeholder = context.getString(R.string.reader_menu_link_placeholder),
    )
    editor.requestFocus()
    editor.replaceMarkdownSourceRange(edit.replaceStart, edit.replaceEnd, edit.replacement)
    editor.setMarkdownSourceSelection(edit.selectionStart, edit.selectionEnd)
  }

  fun applyMarkdownClipboardCommand(cut: Boolean) {
    val editor = markdownEditor ?: return
    if (!editor.isEnabled || !noteWritable || menuBusy) return
    val markdown = editor.markdownSourceText()
    val length = markdown.length
    val start = minOf(editor.markdownSelectionStart(), editor.markdownSelectionEnd()).coerceIn(0, length)
    val end = maxOf(editor.markdownSelectionStart(), editor.markdownSelectionEnd()).coerceIn(0, length)
    if (start == end) return
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val selectedText = markdown.substring(start, end)
    val label = context.getString(if (cut) R.string.reader_menu_cut else R.string.reader_menu_copy)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, selectedText))
    if (cut) {
      editor.requestFocus()
      editor.replaceMarkdownSourceRange(start, end, "")
      editor.setMarkdownSourceSelection(start)
    }
  }

  fun selectAllMarkdown() {
    val editor = markdownEditor ?: return
    if (!editor.isEnabled || !noteWritable || menuBusy) return
    editor.requestFocus()
    editor.setMarkdownSourceSelection(0, editor.markdownSourceText().length)
  }

  fun pasteMarkdownFromClipboard() {
    val editor = markdownEditor ?: return
    if (!editor.isEnabled || !noteWritable || menuBusy) return
    val text = context.getSystemService(ClipboardManager::class.java)
      ?.primaryClip
      ?.takeIf { it.itemCount > 0 }
      ?.getItemAt(0)
      ?.text
      ?.toString()
    if (text.isNullOrEmpty()) {
      noteStatus = context.getString(R.string.reader_menu_paste_markdown_empty)
      return
    }
    val edit = NoteImportPayload.insertText(
      markdown = editor.markdownSourceText(),
      selectionStart = editor.markdownSelectionStart(),
      selectionEnd = editor.markdownSelectionEnd(),
      text = text,
    )
    editor.requestFocus()
    editor.replaceMarkdownSourceRange(edit.replaceStart, edit.replaceEnd, edit.replacement)
    editor.setMarkdownSourceSelection(edit.cursor)
    noteStatus = null
  }

  fun pendingCommand(
    id: String,
    labelRes: Int,
    reasonRes: Int = R.string.reader_menu_unavailable_reason,
  ) = actionCommand(
    id = id,
    labelRes = labelRes,
    enabled = false,
    disabledReason = context.getString(reasonRes),
    onSelect = {},
  )

  fun requestProjectAttachmentImageInsertion(entry: ProjectAttachmentEntry) {
    if (project == null) return
    if (!noteWritable) {
      noteStatus = context.getString(R.string.project_attachments_insert_read_only)
      return
    }
    if (!entry.canInsertImage) return
    val reference = ProjectAttachmentPolicy.createImageReference(
      entry.assetName.substringBeforeLast('.'),
      entry.assetName,
    )
    val editor = markdownEditor?.takeIf { it.isEnabled && it.markdownSourceText() == noteDraft }
    if (editor != null) {
      val edit = NoteImportPayload.insertText(
        markdown = noteDraft,
        selectionStart = editor.markdownSelectionStart(),
        selectionEnd = editor.markdownSelectionEnd(),
        text = reference,
      )
      editor.requestFocus()
      editor.replaceMarkdownSourceRange(edit.replaceStart, edit.replaceEnd, edit.replacement)
      editor.setMarkdownSourceSelection(edit.cursor)
    } else {
      val edit = NoteImportPayload.insertText(
        markdown = noteDraft,
        selectionStart = noteDraft.length,
        selectionEnd = noteDraft.length,
        text = reference,
      )
      val updated = buildString(noteDraft.length + reference.length) {
        append(noteDraft, 0, edit.replaceStart)
        append(edit.replacement)
        append(noteDraft, edit.replaceEnd, noteDraft.length)
      }
      readerSession.setNoteDraft(updated)
      noteEditRequest++
    }
    noteStatus = null
    showProjectAttachments = false
  }

  val canImportNotes = activeProject != null && noteWritable && !menuBusy
  val canManageAttachments = activeProject != null && !menuBusy

  fun openHelp(topic: ReaderHelpTopic) {
    if (topic == ReaderHelpTopic.DIAGNOSTICS) {
      val openProject = project != null
      val sourceKind = when {
        renderer != null -> context.getString(R.string.help_source_pdf)
        markdownSource != null -> context.getString(R.string.help_source_markdown)
        else -> context.getString(R.string.help_source_none)
      }
      helpDiagnostics = context.getString(
        R.string.help_diagnostics_format,
        appVersion,
        Build.VERSION.SDK_INT,
        Build.MANUFACTURER.orEmpty(),
        Build.MODEL.orEmpty(),
        context.getString(if (openProject) R.string.help_project_open else R.string.help_project_closed),
        sourceKind,
        if (openProject) totalPages else 0,
      )
    }
    helpTopic = topic
  }

  fun copyHelpDiagnostics() {
    val clipboard = systemClipboard ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("pagenote diagnostics", helpDiagnostics))
    noteStatus = context.getString(R.string.help_diagnostics_copied)
  }

  fun shareHelpDiagnostics() {
    val send = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, helpDiagnostics)
    }
    runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.help_share_title))) }
      .onFailure { noteStatus = context.getString(R.string.help_share_failed) }
  }

  fun menuSection(group: ReaderMenuGroup, labelRes: Int, commands: List<ReaderMenuCommand>) =
    ReaderMenuSection(group, context.getString(labelRes), commands)

  val canOpenProject = !isOpening && !isCreatingProject && !isSaving && !isPenSaving && !isImageImporting &&
    !isImportingProjectAttachment && !isDeletingProjectAttachment && !isLoadingProjectAttachments &&
    !isImportingNotes && !isExportingNotes &&
    failedPenSave == null && !isOcrRunning && !readerInputLocked
  val canReadDocument = activeProject != null && (activeRenderer != null || activeMarkdown != null)
  val canExportNotes = activeProject != null && !menuBusy
  val canUsePdf = canReadDocument && !isMarkdownReading
  val canChangePage = canReadDocument && !isRendering && !isSaving && !isPenSaving && !isImageImporting &&
    !isImportingProjectAttachment && !isDeletingProjectAttachment && !isLoadingProjectAttachments &&
    !isImportingNotes && !isExportingNotes &&
    failedPenSave == null && !isOcrRunning && !readerInputLocked
  fun changePageFromSwipe(direction: PdfPageSwipeDirection) {
    if (!canChangePage) return
    val target = PdfPageSwipePolicy.targetPage(pageIndex, totalPages, direction) ?: return
    readerSession.launchSessionTask { changePage(target) }
  }
  val shortcutActivity = activityResultOwner as? MainActivity
  DisposableEffect(shortcutActivity, activeProject?.manifest?.id, pageIndex, canChangePage, noteWritable, menuBusy) {
    val activity = shortcutActivity ?: return@DisposableEffect onDispose {}
    val handler: (KeyEvent) -> Boolean = handler@{ event ->
      if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return@handler false
      when (ReaderShortcutPolicy.action(
        event.keyCode,
        alt = event.isAltPressed,
        control = event.isCtrlPressed,
        meta = event.isMetaPressed,
      )) {
        ReaderShortcutAction.PREVIOUS_PAGE -> {
          if (!canChangePage || pageIndex == 0) return@handler false
          readerSession.launchSessionTask { changePage(pageIndex - 1) }
          true
        }
        ReaderShortcutAction.NEXT_PAGE -> {
          if (!canChangePage || pageIndex >= totalPages - 1) return@handler false
          readerSession.launchSessionTask { changePage(pageIndex + 1) }
          true
        }
        ReaderShortcutAction.SAVE_NOTE -> {
          if (activeProject == null || !noteWritable || menuBusy) return@handler false
          readerSession.launchSessionTask { saveCurrentNote() }
          true
        }
        null -> false
      }
    }
    activity.readerShortcutHandler = handler
    onDispose {
      if (activity.readerShortcutHandler === handler) activity.readerShortcutHandler = null
    }
  }
  val canUsePen = canUsePdf && penWritable && !readerInputLocked && !isAnnotationSaving
  val canSelectPen = canUsePdf && !readerInputLocked && !isAnnotationSaving
  val hasValidInkClipboard = remember(clipboardRevision, project?.sourceDirectoryUri) {
    clipboardInkForPaste() != null || nativeAnnotationClipboardForPaste() != null || annotationClipboardHtml() != null
  }

  fun pdfImageInsertionContextIsCurrent(pending: PendingPdfImageInsertion): Boolean =
    project === pending.project &&
      project?.manifest?.id == pending.projectId &&
      project?.sourceDirectoryUri == pending.sourceDirectoryUri &&
      renderer === pending.renderer &&
      pageIndex == pending.pageIndex &&
      penWritable &&
      markdownSource == null

  fun capturePdfImageInsertion(): PendingPdfImageInsertion? {
    val activeProject = project ?: return null
    val activeRenderer = renderer ?: return null
    if (!penWritable || markdownSource != null || menuBusy || readerInputLocked || isAnnotationSaving) {
      return null
    }
    val visibleTopY = PdfImageAnnotationDraft.visibleTopY(
      bounds = viewportBounds,
      transform = viewportTransform,
      density = densityScale,
    ) ?: return null
    return PendingPdfImageInsertion(
      project = activeProject,
      projectId = activeProject.manifest.id,
      sourceDirectoryUri = activeProject.sourceDirectoryUri,
      renderer = activeRenderer,
      pageIndex = pageIndex,
      visibleTopY = visibleTopY,
      style = penStyle,
    )
  }

  fun handlePdfImageResult(sourceUri: Uri?) {
    val pending = pendingPdfImageInsertion
    pendingPdfImageInsertion = null
    if (sourceUri == null || pending == null) {
      isImageImporting = false
      return
    }
    scope.launch {
      var importedForCleanup: ImportedProjectImage? = null
      val importer = ProjectImageImporter(
        context.contentResolver,
        context.cacheDir,
        pending.sourceDirectoryUri,
      )
      try {
        if (!pdfImageInsertionContextIsCurrent(pending)) {
          noteStatus = context.getString(R.string.reader_menu_pdf_image_context_changed)
          return@launch
        }
        val imported = withContext(NonCancellable + Dispatchers.IO) {
          importer.import(sourceUri).also { importedForCleanup = it }
        }
        if (!pdfImageInsertionContextIsCurrent(pending)) {
          val removed = withContext(NonCancellable + Dispatchers.IO) {
            importer.deleteImported(imported.documentUri)
          }
          importedForCleanup = null
          noteStatus = if (removed) {
            context.getString(R.string.reader_menu_pdf_image_context_changed)
          } else {
            context.getString(R.string.reader_menu_pdf_image_orphaned, imported.assetName)
          }
          return@launch
        }
        val draft = PdfImageAnnotationDraft.create(
          assetName = imported.assetName,
          pixelWidth = imported.pixelWidth,
          pixelHeight = imported.pixelHeight,
          visibleTopY = pending.visibleTopY,
          style = pending.style,
        ) ?: throw IllegalArgumentException("The selected image geometry is not supported")

        var contextChanged = false
        var retainedRecoveryCopyName: String? = null
        penSaveMutex.withLock {
          if (!pdfImageInsertionContextIsCurrent(pending)) {
            contextChanged = true
            return@withLock
          }
          val store = ProjectAnnotationStore(
            context.contentResolver,
            pending.sourceDirectoryUri,
            pending.project.annotationsUri,
          )
          val existing = withContext(Dispatchers.IO) {
            store.readPage("document", pending.pageIndex + 1)
          }
          if (!pdfImageInsertionContextIsCurrent(pending)) {
            contextChanged = true
            return@withLock
          }
          val merged = existing.toMutableList().apply { add(draft) }
          withContext(Dispatchers.IO) {
            store.writePage("document", pending.pageIndex + 1, kotlinx.serialization.json.JsonArray(merged))
            retainedRecoveryCopyName = store.lastRetainedRecoveryCopyName
          }
        }
        if (contextChanged) {
          val removed = withContext(NonCancellable + Dispatchers.IO) {
            importer.deleteImported(imported.documentUri)
          }
          importedForCleanup = null
          noteStatus = if (removed) {
            context.getString(R.string.reader_menu_pdf_image_context_changed)
          } else {
            context.getString(R.string.reader_menu_pdf_image_orphaned, imported.assetName)
          }
          return@launch
        }
        importedForCleanup = null
        annotationOverlayReload++
        noteStatus = retainedRecoveryCopyName?.let {
          context.getString(R.string.reader_menu_pdf_image_saved_recovery_copy, it)
        } ?: context.getString(R.string.reader_menu_pdf_image_saved)
      } catch (cancelled: CancellationException) {
        importedForCleanup?.let { imported ->
          withContext(NonCancellable + Dispatchers.IO) { importer.deleteImported(imported.documentUri) }
        }
        throw cancelled
      } catch (error: ProjectImageImportException) {
        noteStatus = error.residualAssetName?.let {
          context.getString(R.string.reader_menu_pdf_image_orphaned, it)
        } ?: context.getString(R.string.reader_menu_pdf_image_import_failed)
      } catch (error: Exception) {
        importedForCleanup?.let { imported ->
          val removed = withContext(NonCancellable + Dispatchers.IO) {
            importer.deleteImported(imported.documentUri)
          }
          importedForCleanup = null
          if (!removed) {
            noteStatus = context.getString(R.string.reader_menu_pdf_image_orphaned, imported.assetName)
            return@launch
          }
        }
        noteStatus = context.getString(R.string.reader_menu_pdf_image_import_failed)
      } finally {
        isImageImporting = false
      }
    }
  }

  val pdfImagePicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
    onResult = ::handlePdfImageResult,
  )

  fun choosePdfImage() {
    val pending = capturePdfImageInsertion()
    if (pending == null) {
      noteStatus = context.getString(R.string.reader_menu_busy)
      return
    }
    pendingPdfImageInsertion = pending
    isImageImporting = true
    try {
      val pickerForTesting = (activityResultOwner as? MainActivity)?.pdfImagePickerForTesting
      if (pickerForTesting == null) pdfImagePicker.launch(arrayOf("image/*"))
      else pickerForTesting(::handlePdfImageResult)
    } catch (_: Exception) {
      pendingPdfImageInsertion = null
      isImageImporting = false
      noteStatus = context.getString(R.string.reader_menu_pdf_image_import_failed)
    }
  }

  val hasNoteHistory = noteWritable && activeProject?.manifest?.id?.let {
    NoteHistoryMirror.directory(context.filesDir, it) != null
  } == true
  val menuSections = listOf(
    menuSection(
      ReaderMenuGroup.FILE,
      R.string.reader_menu_group_file,
      listOf(
        actionCommand(
          "open_project", R.string.open_project, canOpenProject,
          disabledReason = commandReason(busy = !canOpenProject),
        ) {
          chooseProjectFromRecentDialog()
        },
        actionCommand(
          "new_project", R.string.new_project, canOpenProject,
          disabledReason = commandReason(busy = !canOpenProject),
        ) { showNewProject = true },
        actionCommand(
          "recent_documents", R.string.reader_menu_recent_documents, canOpenProject,
          disabledReason = commandReason(busy = !canOpenProject),
        ) { showRecentProjectsDialog = true },
        actionCommand(
          "attachments", R.string.reader_menu_attachments, canManageAttachments,
          disabledReason = commandReason(requireProject = true),
        ) {
          showProjectAttachments = true
          refreshProjectAttachments()
        },
        actionCommand(
          "import_notes", R.string.reader_menu_import_notes, canImportNotes,
          disabledReason = commandReason(requireProject = true, requireWritable = true),
        ) { beginNoteImport() },
        actionCommand(
          "export_notes", R.string.reader_menu_export_notes, canExportNotes,
          disabledReason = commandReason(requireProject = true),
        ) {
          if (isPendingNoteExportRecovered) {
            showPendingNoteExportRecoveryDialog = true
          } else {
            showNoteExportDialog = true
          }
        },
        actionCommand(
          "note_history", R.string.note_history, hasNoteHistory,
          disabledReason = commandReason(
            requireProject = true,
            requireWritable = true,
            busy = isSaving || isOpening,
          ) ?: context.getString(R.string.reader_menu_history_unavailable),
        ) { showNoteHistory = true },
      ),
    ),
    menuSection(
      ReaderMenuGroup.AI_ANNOTATIONS,
      R.string.reader_menu_group_ai_annotations,
      listOf(
        actionCommand(
          "pdf_text_selection", R.string.reader_menu_pdf_text_selection,
          canUsePdf && !isSelectablePageTextLoading && !readerInputLocked,
          disabledReason = commandReason(requireProject = true, requirePdf = true) ?: when {
            isSelectablePageTextLoading -> context.getString(R.string.reader_menu_busy)
            else -> context.getString(R.string.reader_menu_unavailable_reason)
          },
        ) {
          fun enterSelectionMode() {
            readerOcrState = ReaderOcrState()
            selectAnnotationDrawTool(null)
            pdfTextSelection = null
            pdfTextSelectionMode = true
          }
          val activePdf = activeRenderer
          val targetPage = pageIndex
          val pageText = selectablePageText
          if (pageText != null) {
            enterSelectionMode()
          } else if (activePdf == null) {
            noteStatus = context.getString(R.string.pdf_text_selection_unavailable)
          } else {
            isSelectablePageTextLoading = true
            readerSession.launchSessionTask {
              val extracted = withContext(Dispatchers.IO) {
                runCatching { activePdf.extractSelectableText(targetPage) }.getOrNull()
              }
              if (
                readerSession.renderer.value !== activePdf ||
                readerSession.pageIndex.intValue != targetPage
              ) return@launchSessionTask
              selectablePageText = extracted
              isSelectablePageTextLoading = false
              if (extracted == null) {
                noteStatus = context.getString(R.string.pdf_text_selection_unavailable)
              } else {
                enterSelectionMode()
              }
            }
          }
        },
        actionCommand(
          "page_tutor", R.string.page_tutor_action,
          canReadDocument && !isRendering && !isSaving && !isPageTutorRunning && !isOcrRunning &&
            !readerInputLocked,
          disabledReason = commandReason(requireProject = true),
        ) {
          pageTutorRegionContext = null
          pageTutorResult = null
          pageTutorResultInstruction = null
          pageTutorError = null
          showPageTutor = true
        },
        actionCommand(
          "agent_task", R.string.agent_task_action,
          canReadDocument && !isRendering && !isSaving && !isAgentRunning && !isOcrRunning &&
            !readerInputLocked,
          disabledReason = commandReason(requireProject = true),
        ) {
          agentEvent = null
          agentError = null
          showAgentTask = true
        },
        actionCommand(
          "annotations", R.string.annotations, activeProject != null && !readerInputLocked,
          disabledReason = commandReason(requireProject = true, busy = readerInputLocked),
        ) { showAnnotations = true },
        actionCommand(
          "reviews", R.string.review_action, activeProject != null && !readerInputLocked,
          disabledReason = commandReason(requireProject = true, busy = readerInputLocked),
        ) {
          selectionReviewDraft = null
          pdfSelectionReviewDraft = null
          showReviews = true
        },
        actionCommand(
          "handoff", R.string.handoff_action, activeProject != null && !readerInputLocked,
          disabledReason = commandReason(requireProject = true, busy = readerInputLocked),
        ) { showHandoff = true },
      ),
    ),
    menuSection(
      ReaderMenuGroup.EDIT,
      R.string.reader_menu_group_edit,
      listOf(
        actionCommand(
          "note_undo", R.string.reader_menu_edit_undo, canUndoMarkdown, markdownUndoDisabledReason,
        ) { applyMarkdownHistory(undo = true) },
        actionCommand(
          "note_redo", R.string.reader_menu_edit_redo, canRedoMarkdown, markdownRedoDisabledReason,
        ) { applyMarkdownHistory(undo = false) },
        actionCommand(
          "cut", R.string.reader_menu_cut,
          canFormatMarkdown && hasNoteSelection, markdownSelectionDisabledReason,
        ) { applyMarkdownClipboardCommand(cut = true) },
        actionCommand(
          "copy", R.string.reader_menu_copy,
          canFormatMarkdown && hasNoteSelection, markdownSelectionDisabledReason,
        ) { applyMarkdownClipboardCommand(cut = false) },
        actionCommand(
          "select_all", R.string.reader_menu_select_all,
          canFormatMarkdown, markdownClipboardDisabledReason,
        ) { selectAllMarkdown() },
      ),
    ),
    menuSection(
      ReaderMenuGroup.PARAGRAPH,
      R.string.reader_menu_group_paragraph,
      listOf(
        actionCommand(
          "font_size", R.string.reader_menu_font_size,
          canFormatMarkdown, markdownFormatDisabledReason,
        ) { showNoteFontSizeDialog = true },
        actionCommand(
          "heading", R.string.reader_menu_heading, canFormatMarkdown, markdownFormatDisabledReason,
        ) { showHeadingLevelDialog = true },
        actionCommand(
          "unordered_list", R.string.reader_menu_unordered_list,
          canFormatMarkdown, markdownFormatDisabledReason,
        ) { applyMarkdownParagraphFormatting(MarkdownParagraphPrefix.UNORDERED_LIST) },
        actionCommand(
          "ordered_list", R.string.reader_menu_ordered_list,
          canFormatMarkdown, markdownFormatDisabledReason,
        ) { applyMarkdownParagraphFormatting(MarkdownParagraphPrefix.ORDERED_LIST) },
        actionCommand(
          "task_list", R.string.reader_menu_task_list, canFormatMarkdown, markdownFormatDisabledReason,
        ) { applyMarkdownParagraphFormatting(MarkdownParagraphPrefix.TASK_LIST) },
        actionCommand(
          "quote", R.string.reader_menu_quote, canFormatMarkdown, markdownFormatDisabledReason,
        ) { applyMarkdownParagraphFormatting(MarkdownParagraphPrefix.QUOTE) },
      ),
    ),
    menuSection(
      ReaderMenuGroup.FORMAT,
      R.string.reader_menu_group_format,
      listOf(
        actionCommand(
          "bold", R.string.reader_menu_bold, canFormatMarkdown, markdownFormatDisabledReason,
        ) { applyMarkdownInlineFormatting(MarkdownInlineStyle.BOLD) },
        actionCommand(
          "italic", R.string.reader_menu_italic, canFormatMarkdown, markdownFormatDisabledReason,
        ) { applyMarkdownInlineFormatting(MarkdownInlineStyle.ITALIC) },
        actionCommand(
          "strikethrough", R.string.reader_menu_strikethrough,
          canFormatMarkdown, markdownFormatDisabledReason,
        ) { applyMarkdownInlineFormatting(MarkdownInlineStyle.STRIKETHROUGH) },
        actionCommand(
          "underline", R.string.reader_menu_underline, canFormatMarkdown, markdownFormatDisabledReason,
        ) { applyMarkdownInlineFormatting(MarkdownInlineStyle.UNDERLINE) },
      ),
    ),
    menuSection(
      ReaderMenuGroup.INSERT,
      R.string.reader_menu_group_insert,
      listOf(
        actionCommand(
          "code", R.string.reader_menu_code, canFormatMarkdown, markdownFormatDisabledReason,
        ) { applyMarkdownCodeBlockInsertion() },
        actionCommand(
          "formula", R.string.reader_menu_formula, canFormatMarkdown, markdownFormatDisabledReason,
        ) { applyMarkdownFormulaInsertion() },
        actionCommand(
          "table", R.string.reader_menu_table, canFormatMarkdown, markdownFormatDisabledReason,
        ) { applyMarkdownTableInsertion() },
        actionCommand(
          "link", R.string.reader_menu_link, canFormatMarkdown, markdownFormatDisabledReason,
        ) { applyMarkdownLinkInsertion() },
        actionCommand(
          "image", R.string.reader_menu_image,
          canInsertMarkdownImage, markdownImageDisabledReason,
        ) { chooseMarkdownImage() },
        actionCommand(
          "paste_image", R.string.reader_menu_paste_image,
          canInsertMarkdownImage, markdownImageDisabledReason,
        ) { pasteMarkdownImage() },
        actionCommand(
          "paste_markdown", R.string.reader_menu_paste_markdown,
          canFormatMarkdown, markdownClipboardDisabledReason,
        ) { pasteMarkdownFromClipboard() },
      ),
    ),
    menuSection(
      ReaderMenuGroup.READING,
      R.string.reader_menu_group_reading,
      listOf(
        actionCommand(
          "previous_page", R.string.previous_page,
          canChangePage && pageIndex > 0,
          disabledReason = commandReason(requireProject = true) ?: context.getString(R.string.reader_menu_first_page),
          ) { readerSession.launchSessionTask { changePage((pageIndex - 1).coerceAtLeast(0)) } },
        actionCommand(
          "next_page", R.string.next_page,
          canChangePage && pageIndex < totalPages - 1,
          disabledReason = commandReason(requireProject = true) ?: context.getString(R.string.reader_menu_last_page),
          ) { readerSession.launchSessionTask { changePage((pageIndex + 1).coerceAtMost(totalPages - 1)) } },
        actionCommand(
          "ocr", R.string.ocr,
          canUsePdf && !isRendering && !isOcrRunning && !readerInputLocked,
          disabledReason = commandReason(requireProject = true, requirePdf = true),
        ) {
          selectAnnotationDrawTool(null)
          recognizeCurrentPage()
        },
        actionCommand(
          "region_ocr", R.string.region_ocr_action,
          canUsePdf && !isOcrRunning && !readerInputLocked,
          disabledReason = commandReason(requireProject = true, requirePdf = true),
        ) {
          selectAnnotationDrawTool(null)
          readerOcrState = ReaderOcrState().enterSelection(pageIndex)
        },
        actionCommand(
          "zoom_out", R.string.zoom_out,
          canUsePdf && !readerInputLocked &&
            viewportTransform.scale > PdfViewportTransform.MIN_SCALE,
          disabledReason = commandReason(requireProject = true, requirePdf = true) ?:
            context.getString(R.string.reader_menu_min_zoom),
        ) { applyViewportZoom(0.8f) },
        actionCommand(
          "zoom_in", R.string.zoom_in,
          canUsePdf && !readerInputLocked &&
            viewportTransform.scale < PdfViewportTransform.MAX_SCALE,
          disabledReason = commandReason(requireProject = true, requirePdf = true) ?:
            context.getString(R.string.reader_menu_max_zoom),
        ) { applyViewportZoom(1.25f) },
        actionCommand(
          "fit_width", R.string.fit_width, canUsePdf && !readerInputLocked,
          disabledReason = commandReason(requireProject = true, requirePdf = true),
        ) { fitViewportWidth() },
        actionCommand(
          "fit_page", R.string.fit_page, canUsePdf && !readerInputLocked,
          disabledReason = commandReason(requireProject = true, requirePdf = true),
        ) { readerSession.viewportBookmark.value = PdfViewportBookmark.DEFAULT },
      ),
    ),
    menuSection(
      ReaderMenuGroup.HANDWRITING,
      R.string.reader_menu_group_handwriting,
      listOf(
        actionCommand(
          "select_tool", R.string.reader_menu_select_tool, canSelectPen,
          disabledReason = commandReason(requireProject = true, requirePdf = true),
        ) {
          selectedPenIndex = null
          selectedAnnotationIndex = null
          selectAnnotationDrawTool(null)
          penTool = PenCanvasTool.SELECT
        },
        actionCommand(
          "pen", R.string.pen, canUsePen,
          disabledReason = commandReason(requireProject = true, requirePdf = true, requireWritable = true) ?:
            context.getString(R.string.reader_menu_pen_unavailable),
        ) { selectedPenIndex = null; selectedAnnotationIndex = null; selectAnnotationDrawTool(null); penTool = PenCanvasTool.PEN },
        actionCommand(
          "highlighter", R.string.highlighter, canUsePen,
          disabledReason = commandReason(requireProject = true, requirePdf = true, requireWritable = true) ?:
            context.getString(R.string.reader_menu_pen_unavailable),
        ) { selectedPenIndex = null; selectedAnnotationIndex = null; selectAnnotationDrawTool(null); penTool = PenCanvasTool.HIGHLIGHTER },
        actionCommand(
          "eraser", R.string.eraser, canUsePen,
          disabledReason = commandReason(requireProject = true, requirePdf = true, requireWritable = true) ?:
            context.getString(R.string.reader_menu_pen_unavailable),
        ) { selectedPenIndex = null; selectedAnnotationIndex = null; selectAnnotationDrawTool(null); penTool = PenCanvasTool.ERASER },
        actionCommand(
          "line", R.string.reader_menu_line, canUsePen,
          disabledReason = commandReason(requireProject = true, requirePdf = true, requireWritable = true),
        ) { selectAnnotationDrawTool(PdfAnnotationDrawTool.LINE) },
        actionCommand(
          "arrow", R.string.reader_menu_arrow, canUsePen,
          disabledReason = commandReason(requireProject = true, requirePdf = true, requireWritable = true),
        ) { selectAnnotationDrawTool(PdfAnnotationDrawTool.ARROW) },
        actionCommand(
          "shape", R.string.reader_menu_shape, canUsePen,
          disabledReason = commandReason(requireProject = true, requirePdf = true, requireWritable = true),
        ) { showAnnotationShapePicker = true },
        actionCommand(
          "ink_text", R.string.reader_menu_text, canUsePen,
          disabledReason = commandReason(requireProject = true, requirePdf = true, requireWritable = true) ?:
            context.getString(R.string.reader_menu_pen_unavailable),
        ) {
          selectAnnotationDrawTool(null)
          annotationTextToolMode = true
          annotateIsRegion = false
          annotateMode = true
        },
        actionCommand(
          "ink_color", R.string.reader_menu_color, canUsePen,
          disabledReason = commandReason(requireProject = true, requirePdf = true, requireWritable = true),
        ) { showPenColorPicker = true },
        actionCommand(
          "ink_width", R.string.reader_menu_width, canUsePen,
          disabledReason = commandReason(requireProject = true, requirePdf = true, requireWritable = true),
        ) { showPenWidthPicker = true },
        actionCommand(
          "annotate_mode", R.string.annotate, canUsePen,
          disabledReason = commandReason(requireProject = true, requirePdf = true, requireWritable = true) ?:
            context.getString(R.string.reader_menu_pen_unavailable),
        ) {
          if (annotationTextToolMode) {
            annotationTextToolMode = false
            annotateIsRegion = false
            annotateMode = true
          } else {
            if (!annotateMode) selectAnnotationDrawTool(null)
            annotateMode = !annotateMode
          }
        },
        actionCommand(
          "pen_undo", R.string.undo,
          canUsePen && (penPageStore.canUndo(PenSide.DOCUMENT, pageIndex + 1) ||
            project?.manifest?.id?.let { annotationEditHistory.canUndo(it, pageIndex + 1) } == true),
          disabledReason = commandReason(requireProject = true, requirePdf = true, requireWritable = true) ?:
            context.getString(R.string.reader_menu_nothing_to_undo),
        ) { undoPenPage() },
        actionCommand(
          "pen_redo", R.string.redo,
          canUsePen && (penPageStore.canRedo(PenSide.DOCUMENT, pageIndex + 1) ||
            project?.manifest?.id?.let { annotationEditHistory.canRedo(it, pageIndex + 1) } == true),
          disabledReason = commandReason(requireProject = true, requirePdf = true, requireWritable = true) ?:
            context.getString(R.string.reader_menu_nothing_to_redo),
        ) { redoPenPage() },
        actionCommand(
          "copy_ink", R.string.reader_menu_copy_ink,
          canUsePdf && selectedInkCanBeCopied(),
          disabledReason = if (selectedInkCanBeCopied()) commandReason(requireProject = true, requirePdf = true)
            else context.getString(R.string.pen_selection_required),
        ) { copySelectedInk() },
        actionCommand(
          "paste_ink", R.string.reader_menu_paste_ink,
          canUsePen && hasValidInkClipboard,
          disabledReason = commandReason(requireProject = true, requirePdf = true) ?: when {
            !penWritable -> context.getString(R.string.pen_read_only)
            !hasValidInkClipboard -> context.getString(R.string.pen_clipboard_invalid)
            else -> context.getString(R.string.reader_menu_pen_unavailable)
          },
        ) { pasteInk() },
        actionCommand(
          "insert_ink_image", R.string.reader_menu_insert_image, canUsePen && !menuBusy,
          disabledReason = commandReason(requireProject = true, requirePdf = true, requireWritable = true),
        ) { choosePdfImage() },
        actionCommand(
          "clear_page", R.string.clear_page,
          canUsePen && penStrokes.isNotEmpty(),
          disabledReason = commandReason(requireProject = true, requirePdf = true, requireWritable = true) ?:
            context.getString(R.string.reader_menu_no_ink),
        ) { clearPenPage() },
        actionCommand(
          "delete_ink", R.string.reader_menu_delete_ink,
          canUsePen && selectedInkCanBeCopied(),
          disabledReason = commandReason(requireProject = true, requirePdf = true) ?: when {
            !penWritable -> context.getString(R.string.pen_read_only)
            !selectedInkCanBeCopied() -> context.getString(R.string.pen_selection_required)
            else -> context.getString(R.string.reader_menu_pen_unavailable)
          },
        ) { deleteSelectedInk() },
      ),
    ),
    menuSection(
      ReaderMenuGroup.WORD_EDITING,
      R.string.reader_menu_group_word_editing,
      listOf(
        pendingCommand(
          "office_edit",
          R.string.reader_menu_office_edit,
          R.string.reader_menu_office_desktop_only,
        ),
        actionCommand(
          "selection_to_annotation",
          R.string.reader_menu_selection_to_annotation,
          enabled = activeProject != null && noteWritable && !menuBusy &&
            selectionReviewReadiness(
              start = noteSelection?.start,
              end = noteSelection?.end,
              markdown = noteDraft,
            ) == SelectionReviewReadiness.AVAILABLE,
          disabledReason = commandReason(requireProject = true, requireWritable = true) ?:
            context.getString(
              if (selectionReviewReadiness(
                  start = noteSelection?.start,
                  end = noteSelection?.end,
                  markdown = noteDraft,
                ) == SelectionReviewReadiness.TOO_LARGE
              ) R.string.reader_menu_selection_too_large
              else R.string.reader_menu_selection_required,
            ),
        ) {
          val selection = noteSelection ?: return@actionCommand
          val selectedMarkdown = noteDraft
          val selectedPage = pageIndex + 1
          scope.launch {
            if (hasUnsavedChanges && !saveCurrentNote()) return@launch
            try {
              selectionReviewDraft = NoteSelectionReviewDraft.capture(
                page = selectedPage,
                context = selectedMarkdown,
                start = selection.start,
                end = selection.end,
              )
              showReviews = true
            } catch (_: IllegalArgumentException) {
              noteStatus = context.getString(R.string.review_invalid_input)
            }
          }
        },
        pendingCommand(
          "save_return",
          R.string.reader_menu_save_return,
          R.string.reader_menu_save_return_desktop_session,
        ),
      ),
    ),
    menuSection(
      ReaderMenuGroup.SETTINGS,
      R.string.reader_menu_group_settings,
      listOf(
        actionCommand("settings_action", R.string.settings_action, true) { showSettings = true },
        actionCommand("model_settings", R.string.agent_model_settings_action, true) {
          showAgentModelSettings = true
        },
      ),
    ),
    menuSection(
      ReaderMenuGroup.HELP,
      R.string.reader_menu_group_help,
      listOf(
        actionCommand("about", R.string.reader_menu_about, true) { openHelp(ReaderHelpTopic.ABOUT) },
        actionCommand("shortcuts", R.string.reader_menu_shortcuts, true) { openHelp(ReaderHelpTopic.SHORTCUTS) },
        actionCommand("privacy_license", R.string.reader_menu_privacy_license, true) {
          openHelp(ReaderHelpTopic.PRIVACY_LICENSE)
        },
        actionCommand("diagnostics", R.string.reader_menu_diagnostics, true) {
          openHelp(ReaderHelpTopic.DIAGNOSTICS)
        },
      ),
    ),
  )

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    AndroidView(
      modifier = Modifier.size(1.dp).align(Alignment.TopStart),
      factory = { viewContext ->
        FrameLayout(viewContext).apply {
          importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
          isClickable = false
          richHtmlRenderer.attachTo(this)
        }
      },
      update = {},
    )
    val menuLayout = ReaderMenuLayout.forWidth(maxWidth.value.toInt())
    LaunchedEffect(menuLayout) {
      if (menuLayout != ReaderMenuLayout.COMPACT_DRAWER) menuDrawerOpen = false
    }
    ReaderAdaptiveMenu(
      layout = menuLayout,
      groups = menuSections,
      expanded = menuSidebarExpanded,
      onExpandedChange = { menuSidebarExpanded = it },
      drawerOpen = menuDrawerOpen,
      onDrawerOpenChange = { menuDrawerOpen = it },
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (menuLayout == ReaderMenuLayout.COMPACT_DRAWER) {
          ReaderMenuTrigger(onClick = { menuDrawerOpen = true })
        }
        Text(
          text = activeProject?.name ?: "pagenote",
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (canUsePdf) {
          Text(
            text = "${(viewportTransform.scale * 100f).roundToInt()}%",
            modifier = Modifier.testTag("reader_zoom_status"),
            style = MaterialTheme.typography.labelLarge,
          )
        }
        if (activeProject != null) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              modifier = Modifier.testTag("reader_previous_page"),
              enabled = canChangePage && pageIndex > 0,
              onClick = { readerSession.launchSessionTask { changePage(pageIndex - 1) } },
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.previous_page),
              )
            }
            TextButton(
              modifier = Modifier.testTag("reader_page_status"),
              enabled = canChangePage && totalPages > 0,
              onClick = {
                pageJumpInput = (pageIndex + 1).toString()
                showPageJumpDialog = true
              },
            ) {
              Text(
                text = stringResource(R.string.page_count, pageIndex + 1, totalPages),
                style = MaterialTheme.typography.labelLarge,
              )
            }
            IconButton(
              modifier = Modifier.testTag("reader_next_page"),
              enabled = canChangePage && pageIndex < totalPages - 1,
              onClick = { readerSession.launchSessionTask { changePage(pageIndex + 1) } },
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.next_page),
              )
            }
          }
        }
      }

    errorMessage?.let { message ->
      Text(
        text = message,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
      )
    }

    if (activeProject != null && (activeRenderer != null || activeMarkdown != null)) {
      if (pdfTextSelectionMode) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          PdfTextSelectionActions(
            selection = pdfTextSelection,
            onCreateReview = {
              val pageText = selectablePageText ?: return@PdfTextSelectionActions
              val selection = pdfTextSelection ?: return@PdfTextSelectionActions
              readerSession.launchSessionTask {
                if (hasUnsavedChanges && !saveCurrentNote()) return@launchSessionTask
                try {
                  pdfSelectionReviewDraft = PdfSelectionReviewDraft.capture(
                    page = pageIndex + 1,
                    source = pageText,
                    selection = selection,
                    targetNoteRevision = noteBaseRevision,
                  )
                  pdfTextSelectionMode = false
                  pdfTextSelection = null
                  selectionReviewDraft = null
                  showReviews = true
                } catch (_: IllegalArgumentException) {
                  noteStatus = context.getString(R.string.review_invalid_input)
                }
              }
            },
            onCancel = {
              pdfTextSelection = null
              pdfTextSelectionMode = false
            },
          )
        }
      }
      if (readerOcrState.mode != ReaderOcrMode.IDLE) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          ReaderOcrActions(
            state = readerOcrState,
            currentPage = pageIndex,
            enabled = !isMarkdownReading && !isOcrRunning && !isRendering,
            onStateChange = { readerOcrState = it },
            onRecognize = ::recognizeSelectedRegion,
          )
        }
      }
      penStatus?.let { status ->
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = status,
            modifier = Modifier.weight(1f),
            color = if (isReaderErrorStatus(status)) {
              MaterialTheme.colorScheme.error
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelMedium,
          )
          failedPenSave?.let { (targetPage, strokes) ->
            TextButton(
              onClick = { readerSession.launchSessionTask { savePenPage(targetPage, strokes) } },
              enabled = !isPenSaving,
            ) { Text(stringResource(R.string.retry)) }
          }
        }
      }
    }

    if (activeProject != null && (activeRenderer != null || activeMarkdown != null)) {
      BoxWithConstraints(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
      ) {
        if (maxWidth >= 840.dp) {
          val (pdfPaneWeight, notePaneWeight) = readerPaneWeights(
            ReaderPaneSplitPolicy.persistedPercent(splitLeftPercent),
          )
          val dividerAvailableWidthPx = with(LocalDensity.current) {
            (maxWidth - READER_PANE_DIVIDER_WIDTH).toPx()
          }
          Row(modifier = Modifier.fillMaxSize()) {
            if (activeMarkdown != null) {
              MarkdownSourcePane(
                markdown = activeMarkdown,
                sourceName = activeProject.sourceName,
                page = pageIndex + 1,
                sourceDirectoryUri = activeProject.sourceDirectoryUri,
                modifier = Modifier.weight(pdfPaneWeight).fillMaxHeight(),
              )
            } else {
              PdfPageViewport(
                bitmap = pageBitmap,
                sourceName = activeProject.sourceName,
                page = pageIndex,
                annotations = annotationOverlayItems + listOfNotNull(
                  annotationPreview?.takeIf { it.first == pageIndex + 1 }?.second,
                ),
                selectedAnnotationBounds = selectedAnnotationIndex?.let { index ->
                  (annotationPageItems.getOrNull(index) as? JsonObject)?.let(PdfAnnotationClipboard::bounds)
                },
                selectedAnnotationResizable = selectedAnnotationIndex?.let { index ->
                  (annotationPageItems.getOrNull(index) as? JsonObject)?.get("type")
                    ?.let { runCatching { it.jsonPrimitive.content != "text" }.getOrNull() }
                } == true,
                annotationImages = annotationOverlayImages,
                annotationHtmlBitmaps = annotationOverlayHtmlBitmaps,
                loading = isRendering,
                strokes = penStrokes,
                tool = penTool,
                inputEnabled = (penWritable || penTool == PenCanvasTool.SELECT) && !readerInputLocked && !annotateMode &&
                  annotationDrawTool == null && !isAnnotationSaving,
                onStrokeAdded = ::applyPenStroke,
                onShapeRecognized = ::applyRecognizedShape,
                onStrokeErased = ::erasePenStroke,
                selectedStrokeIndex = selectedPenIndex,
                onSelectedStrokeChanged = { selectedPenIndex = it },
                onAnnotationSelectionTouch = ::handleNativeAnnotationSelectionTouch,
                transform = viewportTransform,
                onTransformChanged = ::updateViewportTransform,
                onBoundsChanged = { viewportBounds = it },
                selectionEnabled = readerOcrState.mode == ReaderOcrMode.SELECTING,
                textSelectionEnabled = pdfTextSelectionMode,
                pageText = selectablePageText,
                textSelection = pdfTextSelection,
                onTextSelectionChanged = { pdfTextSelection = it },
                gesturesEnabled = !readerInputLocked && !annotateMode &&
                  annotationDrawTool == null && !isAnnotationSaving,
                allowInkOutsidePage = settings.allowInkOutsidePage,
                pageSwipesEnabled = activeRenderer != null && canChangePage &&
                  penTool != PenCanvasTool.SELECT && !annotateMode &&
                  annotationDrawTool == null && !isAnnotationSaving,
                onPageSwipe = ::changePageFromSwipe,
                selection = if (readerOcrState.pageIndex == pageIndex) readerOcrState.selection else null,
                onSelectionChanged = { readerOcrState = readerOcrState.withSelection(it) },
                annotateTapEnabled = annotateMode,
                onAnnotateTap = { tap ->
                  val point = mapPdfTapToAnnotationPoint(tap, viewportBounds, viewportTransform)
                  if (point != null) {
                    annotateDraft = point
                    annotateText = ""
                    if (annotationTextToolMode) annotateIsRegion = false
                  }
                },
                annotationDrawTool = annotationDrawTool.takeUnless { isAnnotationSaving },
                onAnnotationDrag = { start, end, finished ->
                  annotationDrawTool?.let { handleAnnotationDrag(it, start, end, finished) }
                },
                penStyle = penStyle,
                modifier = Modifier.weight(pdfPaneWeight).fillMaxHeight(),
              )
            }
            ReaderPaneDivider(
              leftPercent = splitLeftPercent,
              availableWidthPx = dividerAvailableWidthPx,
              onPercentChange = { splitLeftPercent = it },
              onDragFinished = {
                scope.launch {
                  try {
                    settingsStore.save(
                      settings.copy(leftPercent = ReaderPaneSplitPolicy.persistedPercent(splitLeftPercent)),
                    )
                  } catch (cancelled: CancellationException) {
                    throw cancelled
                  } catch (_: Exception) {
                    splitLeftPercent = settings.leftPercent.toFloat()
                    noteStatus = context.getString(R.string.settings_save_failed)
                  }
                }
              },
              modifier = Modifier.fillMaxHeight(),
            )
            NotePanel(
              page = pageIndex + 1,
              editRequest = noteEditRequest,
              markdown = noteDraft,
              dirty = hasUnsavedChanges,
              writable = noteWritable,
              saving = isSaving || isExportingNotes,
              status = noteStatus,
              onMarkdownChange = readerSession::setNoteDraft,
              onMarkdownMutation = { before, after ->
                if (!isApplyingMarkdownHistory) {
                  markdownHistory.record(before, after, SystemClock.uptimeMillis())
                  markdownHistoryRevision++
                }
              },
              onEditorTextReset = { markdown ->
                markdownHistory.reset(markdown)
                markdownHistoryRevision++
              },
              onSave = { readerSession.launchSessionTask { saveCurrentNote() } },
              onReload = ::reloadCurrentNote,
              onSelectionChanged = { noteSelection = it },
              onEditorViewAttached = {
                markdownHistory.reset(it.markdownSourceText())
                markdownHistoryRevision++
                markdownEditor = it
              },
              onEditorViewReleased = { released ->
                if (markdownEditor === released) {
                  markdownHistory.reset(released.markdownSourceText())
                  markdownHistoryRevision++
                  markdownEditor = null
                }
              },
              historyEnabled = noteWritable &&
                NoteHistoryMirror.directory(context.filesDir, project?.manifest?.id) != null,
              onHistory = { showNoteHistory = true },
              sourceDirectoryUri = activeProject.sourceDirectoryUri,
              animationEnabled = settings.noteAnimation,
              noteFontSize = settings.noteFontSize,
              modifier = Modifier.weight(notePaneWeight).fillMaxHeight(),
            )
          }
        } else {
          Column(modifier = Modifier.fillMaxSize()) {
            if (activeMarkdown != null) {
              MarkdownSourcePane(
                markdown = activeMarkdown,
                sourceName = activeProject.sourceName,
                page = pageIndex + 1,
                sourceDirectoryUri = activeProject.sourceDirectoryUri,
                modifier = Modifier.weight(1f).fillMaxWidth(),
              )
            } else {
              PdfPageViewport(
                bitmap = pageBitmap,
                sourceName = activeProject.sourceName,
                page = pageIndex,
                annotations = annotationOverlayItems + listOfNotNull(
                  annotationPreview?.takeIf { it.first == pageIndex + 1 }?.second,
                ),
                selectedAnnotationBounds = selectedAnnotationIndex?.let { index ->
                  (annotationPageItems.getOrNull(index) as? JsonObject)?.let(PdfAnnotationClipboard::bounds)
                },
                selectedAnnotationResizable = selectedAnnotationIndex?.let { index ->
                  (annotationPageItems.getOrNull(index) as? JsonObject)?.get("type")
                    ?.let { runCatching { it.jsonPrimitive.content != "text" }.getOrNull() }
                } == true,
                annotationImages = annotationOverlayImages,
                annotationHtmlBitmaps = annotationOverlayHtmlBitmaps,
                loading = isRendering,
                strokes = penStrokes,
                tool = penTool,
                inputEnabled = (penWritable || penTool == PenCanvasTool.SELECT) && !readerInputLocked && !annotateMode &&
                  annotationDrawTool == null && !isAnnotationSaving,
                onStrokeAdded = ::applyPenStroke,
                onShapeRecognized = ::applyRecognizedShape,
                onStrokeErased = ::erasePenStroke,
                selectedStrokeIndex = selectedPenIndex,
                onSelectedStrokeChanged = { selectedPenIndex = it },
                onAnnotationSelectionTouch = ::handleNativeAnnotationSelectionTouch,
                transform = viewportTransform,
                onTransformChanged = ::updateViewportTransform,
                onBoundsChanged = { viewportBounds = it },
                selectionEnabled = readerOcrState.mode == ReaderOcrMode.SELECTING,
                textSelectionEnabled = pdfTextSelectionMode,
                pageText = selectablePageText,
                textSelection = pdfTextSelection,
                onTextSelectionChanged = { pdfTextSelection = it },
                gesturesEnabled = !readerInputLocked && !annotateMode &&
                  annotationDrawTool == null && !isAnnotationSaving,
                allowInkOutsidePage = settings.allowInkOutsidePage,
                pageSwipesEnabled = activeRenderer != null && canChangePage &&
                  penTool != PenCanvasTool.SELECT && !annotateMode &&
                  annotationDrawTool == null && !isAnnotationSaving,
                onPageSwipe = ::changePageFromSwipe,
                selection = if (readerOcrState.pageIndex == pageIndex) readerOcrState.selection else null,
                onSelectionChanged = { readerOcrState = readerOcrState.withSelection(it) },
                annotateTapEnabled = annotateMode,
                onAnnotateTap = { tap ->
                  val point = mapPdfTapToAnnotationPoint(tap, viewportBounds, viewportTransform)
                  if (point != null) {
                    annotateDraft = point
                    annotateText = ""
                    if (annotationTextToolMode) annotateIsRegion = false
                  }
                },
                annotationDrawTool = annotationDrawTool.takeUnless { isAnnotationSaving },
                onAnnotationDrag = { start, end, finished ->
                  annotationDrawTool?.let { handleAnnotationDrag(it, start, end, finished) }
                },
                penStyle = penStyle,
                modifier = Modifier.weight(1f).fillMaxWidth(),
              )
            }
            Box(
              modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                .height(1.dp).background(MaterialTheme.colorScheme.outlineVariant),
            )
            NotePanel(
              page = pageIndex + 1,
              editRequest = noteEditRequest,
              markdown = noteDraft,
              dirty = hasUnsavedChanges,
              writable = noteWritable,
              saving = isSaving || isExportingNotes,
              status = noteStatus,
              onMarkdownChange = readerSession::setNoteDraft,
              onMarkdownMutation = { before, after ->
                if (!isApplyingMarkdownHistory) {
                  markdownHistory.record(before, after, SystemClock.uptimeMillis())
                  markdownHistoryRevision++
                }
              },
              onEditorTextReset = { markdown ->
                markdownHistory.reset(markdown)
                markdownHistoryRevision++
              },
              onSave = { readerSession.launchSessionTask { saveCurrentNote() } },
              onReload = ::reloadCurrentNote,
              onSelectionChanged = { noteSelection = it },
              onEditorViewAttached = {
                markdownHistory.reset(it.markdownSourceText())
                markdownHistoryRevision++
                markdownEditor = it
              },
              onEditorViewReleased = { released ->
                if (markdownEditor === released) {
                  markdownHistory.reset(released.markdownSourceText())
                  markdownHistoryRevision++
                  markdownEditor = null
                }
              },
              historyEnabled = noteWritable &&
                NoteHistoryMirror.directory(context.filesDir, project?.manifest?.id) != null,
              onHistory = { showNoteHistory = true },
              sourceDirectoryUri = activeProject.sourceDirectoryUri,
              animationEnabled = settings.noteAnimation,
              noteFontSize = settings.noteFontSize,
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
          }
        }
      }
    } else {
      Box(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (isOpening) {
          Text(stringResource(R.string.opening_project_message), style = MaterialTheme.typography.bodyLarge)
        } else {
          Text(stringResource(R.string.choose_project_message), style = MaterialTheme.typography.bodyLarge)
        }
      }
    }
  }
    }
  }

  if (showPageJumpDialog && activeProject != null && totalPages > 0) {
    PageJumpDialog(
      input = pageJumpInput,
      totalPages = totalPages,
      isRunning = isPageJumpRunning,
      onInputChange = { pageJumpInput = it },
      onDismiss = { showPageJumpDialog = false },
      onConfirm = { targetPage ->
        if (!isPageJumpRunning) {
          isPageJumpRunning = true
          readerSession.launchSessionTask {
            try {
              if (changePage(targetPage)) showPageJumpDialog = false
            } finally {
              isPageJumpRunning = false
            }
          }
        }
      },
    )
  }
  if (showHeadingLevelDialog) {
    AlertDialog(
      onDismissRequest = { showHeadingLevelDialog = false },
      title = { Text(stringResource(R.string.reader_menu_heading)) },
      text = {
        Column {
          listOf(
            MarkdownParagraphPrefix.HEADING_1 to R.string.reader_menu_heading_level_1,
            MarkdownParagraphPrefix.HEADING_2 to R.string.reader_menu_heading_level_2,
            MarkdownParagraphPrefix.HEADING_3 to R.string.reader_menu_heading_level_3,
          ).forEachIndexed { index, (prefix, labelRes) ->
            TextButton(
              modifier = Modifier.fillMaxWidth().testTag("reader_heading_level_${index + 1}"),
              enabled = canFormatMarkdown,
              onClick = {
                showHeadingLevelDialog = false
                applyMarkdownParagraphFormatting(prefix)
              },
            ) {
              Text(stringResource(labelRes))
            }
          }
        }
      },
      confirmButton = {
        TextButton(
          modifier = Modifier.testTag("reader_heading_cancel"),
          onClick = { showHeadingLevelDialog = false },
        ) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }
  if (showOcrResult) {
    ocrResult?.let { result ->
      OcrResultDialog(
        page = ocrResultPage + 1,
        result = result,
        onDismiss = { showOcrResult = false },
      )
    }
  }
  if (readerOcrState.mode == ReaderOcrMode.RESULT) {
    regionOcrResult?.let { result ->
      OcrResultDialog(
        page = (readerOcrState.pageIndex ?: pageIndex) + 1,
        result = result,
        onDismiss = {
          readerOcrState = readerOcrState.dismissResult()
          regionOcrContext = null
        },
        onExplainRegion = {
          val activeProject = project
          val activeRenderer = renderer
          val regionContext = regionOcrContext
          val resultPage = readerOcrState.pageIndex
          val current = activeProject != null && activeRenderer != null && regionContext != null &&
            resultPage != null && readerOcrState.selection == regionContext.region &&
            resultPage == pageIndex && regionContext.matches(
              activeProject.manifest.id,
              activeProject.sourceDirectoryUri.toString(),
              pageIndex,
              activeRenderer.sourceRevision,
              activeRenderer,
            )
          if (current) {
            pageTutorRegionContext = regionContext
            pageTutorInstruction = context.getString(R.string.page_tutor_region_default_instruction)
            pageTutorResult = null
            pageTutorResultInstruction = null
            pageTutorError = null
            regionOcrResult = null
            regionOcrContext = null
            readerOcrState = readerOcrState.dismissResult()
            showPageTutor = true
          } else {
            errorMessage = context.getString(R.string.page_tutor_region_stale)
            readerOcrState = readerOcrState.dismissResult()
            regionOcrContext = null
          }
        },
      )
    }
  }
  if (showSettings) {
    ReaderSettingsDialog(
      settings = settings,
      onDismiss = { showSettings = false },
      onConfigureAgent = {
        showSettings = false
        showAgentModelSettings = true
      },
      onSave = settingsStore::save,
    )
  }
  if (showNoteFontSizeDialog) {
    NoteFontSizeDialog(
      currentSize = settings.noteFontSize,
      onDismiss = { showNoteFontSizeDialog = false },
      onApply = { size -> settingsStore.save(settings.copy(noteFontSize = size)) },
    )
  }
  if (showPageTutor && project != null && (renderer != null || markdownSource != null)) {
    PageTutorDialog(
      page = pageIndex + 1,
      focus = pageTutorRegionContext?.focus,
      instruction = pageTutorInstruction,
      previewInstruction = pageTutorResultInstruction,
      isRunning = isPageTutorRunning,
      isSaving = isSaving && pageTutorResult != null,
      errorMessage = pageTutorError,
      preview = pageTutorResult,
      noteWritable = noteWritable,
      onInstructionChange = { pageTutorInstruction = it },
      onExplain = ::explainCurrentPage,
      onAppend = ::appendPageTutorResult,
      onDismiss = {
        showPageTutor = false
        pageTutorRegionContext = null
        pageTutorResult = null
        pageTutorResultInstruction = null
        pageTutorError = null
      },
    )
  }
  if (showAgentTask && project != null && (renderer != null || markdownSource != null)) {
    AgentTaskDialog(
      page = pageIndex + 1,
      instruction = agentInstruction,
      pageRange = agentPageRange,
      report = agentReport,
      isRunning = isAgentRunning,
      errorMessage = agentError,
      event = agentEvent,
      onInstructionChange = { agentInstruction = it },
      onPageRangeChange = { agentPageRange = it },
      onRun = { instruction, range -> runAgentBatch(instruction, range) },
      onDismiss = {
        showAgentTask = false
        agentEvent = null
        agentError = null
        agentReport = null
      },
    )
  }
  if (showAgentModelSettings) {
    AgentModelSettingsDialog(
      store = modelSettingsStore,
      onDismiss = { showAgentModelSettings = false },
    )
  }
  if (showRecentProjectsDialog) {
    RecentProjectsDialog(
      projects = recentProjects,
      onOpen = ::openRecentProject,
      onRemove = { entry ->
        scope.launch {
          try {
            recentProjectStore.remove(entry)
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: Exception) {
            noteStatus = context.getString(R.string.recent_projects_remove_failed)
          }
        }
      },
      onOpenProjectPicker = ::chooseProjectFromRecentDialog,
      onDismiss = { showRecentProjectsDialog = false },
    )
  }
  if (showProjectAttachments) {
    ProjectAttachmentsDialog(
      entries = projectAttachments,
      isLoading = isLoadingProjectAttachments,
      isImporting = isImportingProjectAttachment,
      isDeleting = isDeletingProjectAttachment,
      errorMessage = projectAttachmentsError,
      onImport = ::launchProjectAttachmentImport,
      onInsertImage = ::requestProjectAttachmentImageInsertion,
      onDelete = ::deleteProjectAttachment,
      onDismiss = { showProjectAttachments = false },
    )
  }
  helpTopic?.let { topic ->
    ReaderHelpDialog(
      topic = topic,
      appVersion = appVersion,
      diagnostics = helpDiagnostics,
      onCopyDiagnostics = ::copyHelpDiagnostics,
      onShareDiagnostics = ::shareHelpDiagnostics,
      onDismiss = { helpTopic = null },
    )
  }
  if (showNewProject) {
    NewProjectDialog(
      name = newProjectName,
      onNameChange = { newProjectName = it },
      hasDir = newProjectDir != null,
      pdfDisplayName = newProjectPdfName,
      error = newProjectError,
      creating = isCreatingProject,
      onChooseDir = { chooseNewProjectDirectory() },
      onChoosePdf = { chooseNewProjectPdf() },
      onClearPdf = { clearNewProjectPdf() },
      onConfirm = { createNewProject() },
      onDismiss = {
        if (NewProjectDialogPolicy.canDismiss(
            isCreatingProject,
            isChoosingNewProjectDir,
            isChoosingNewProjectPdf,
          )
        ) {
          showNewProject = false
          newProjectError = null
          clearNewProjectPdf()
        }
      },
    )
  }
  if (annotateDraft != null && project != null) {
    AnnotationTextInputDialog(
      text = annotateText,
      onTextChange = { annotateText = it },
      isRegion = annotateIsRegion,
      allowKindSelection = !annotationTextToolMode,
      onKindChange = { annotateIsRegion = it },
      onConfirm = {
        scope.launch {
          val point = annotateDraft ?: return@launch
          val store = ProjectAnnotationStore(
            context.contentResolver,
            project!!.sourceDirectoryUri,
            project!!.annotationsUri,
          )
          try {
            var retainedRecoveryCopyName: String? = null
            val useRegion = annotateIsRegion
            val item = if (useRegion) {
              AnnotationRegionDraft.defaultSize(
                point.first, point.second, penStyle.color, penStyle.width, annotateText,
              )
            } else {
              AnnotationTextDraft.toItem(
                point.first, point.second, penStyle.color, penStyle.width, annotateText,
              )
            }
            penSaveMutex.withLock {
              val existing = withContext(Dispatchers.IO) {
                store.readPage("document", pageIndex + 1)
              }
              val merged = existing.toMutableList()
              merged.add(item.toJsonElement())
              withContext(Dispatchers.IO) {
                store.writePage("document", pageIndex + 1, kotlinx.serialization.json.JsonArray(merged))
                retainedRecoveryCopyName = store.lastRetainedRecoveryCopyName
              }
            }
            annotationOverlayReload += 1
            noteStatus = retainedRecoveryCopyName?.let {
              context.getString(R.string.annotation_saved_recovery_copy, it)
            } ?: context.getString(R.string.annotation_saved)
          } catch (error: Exception) {
            noteStatus = context.getString(R.string.annotation_save_failed, error.message ?: "")
          } finally {
            annotateDraft = null
            annotateText = ""
            annotateIsRegion = false
            annotateMode = false
            annotationTextToolMode = false
          }
        }
      },
      onDismiss = {
        annotateDraft = null
        annotateText = ""
        annotateIsRegion = false
      },
    )
  }
  if (showPenColorPicker) {
    InkColorPickerDialog(
      currentColor = penStyle.color,
      onApply = { color ->
        applyPenStyle(color = color)
        showPenColorPicker = false
      },
      onDismiss = { showPenColorPicker = false },
    )
  }
  if (showPenWidthPicker) {
    InkWidthPickerDialog(
      currentWidth = penStyle.width,
      onApply = { width ->
        applyPenStyle(width = width)
        showPenWidthPicker = false
      },
      onDismiss = { showPenWidthPicker = false },
    )
  }
  if (showAnnotationShapePicker) {
    AnnotationShapePickerDialog(
      onSelect = { tool ->
        showAnnotationShapePicker = false
        selectAnnotationDrawTool(tool)
      },
      onDismiss = { showAnnotationShapePicker = false },
    )
  }
  if (showAnnotations && project != null) {
    AnnotationListDialog(
      page = pageIndex + 1,
      project = project!!,
      onDismiss = { showAnnotations = false },
      onChanged = { noteStatus = it },
      onDelete = { targetPage, targetIndex ->
        val retainedRecoveryCopyName = penSaveMutex.withLock {
          withContext(Dispatchers.IO) {
            val store = ProjectAnnotationStore(
              context.contentResolver,
              project!!.sourceDirectoryUri,
              project!!.annotationsUri,
            )
            val current = store.readPage("document", targetPage).toMutableList()
            current.removeAt(targetIndex)
            store.writePage("document", targetPage, kotlinx.serialization.json.JsonArray(current))
            store.lastRetainedRecoveryCopyName
          }
        }
        if (targetPage == pageIndex + 1) {
          selectedAnnotationIndex = PdfAnnotationClipboard.selectedIndexAfterDeletion(
            selectedAnnotationIndex,
            targetIndex,
          )
          annotationGesture = null
        }
        annotationOverlayReload += 1
        retainedRecoveryCopyName
      },
    )
  }
  if (showReviews && project != null) {
    ReviewListDialog(
      page = pageIndex + 1,
      project = project!!,
      noteRevision = noteBaseRevision,
      selectionDraft = selectionReviewDraft,
      pdfSelectionDraft = pdfSelectionReviewDraft,
      onDismiss = {
        showReviews = false
        selectionReviewDraft = null
        pdfSelectionReviewDraft = null
      },
      onChanged = { noteStatus = it },
    )
  }
  if (showHandoff && project != null && (renderer != null || markdownSource != null)) {
    HandoffDialog(
      page = pageIndex + 1,
      project = project!!,
      noteRevision = PageNotes.revision(noteDraft),
      sourceRevision = renderer?.sourceRevision.orEmpty(),
      onDismiss = { showHandoff = false },
      onChanged = { noteStatus = it },
    )
  }
  if (showNoteImportConfirmation && pendingNoteImportBackup != null) {
    AlertDialog(
      onDismissRequest = {
        showNoteImportConfirmation = false
        isImportingNotes = false
        pendingNoteImportContext = null
        pendingNoteImportBackup = null
      },
      title = { Text(stringResource(R.string.reader_menu_import_title)) },
      text = { Text(stringResource(R.string.reader_menu_import_confirm_message)) },
      confirmButton = {
        TextButton(onClick = { scope.launch { applyPendingNoteImport() } }) {
          Text(stringResource(R.string.reader_menu_import_confirm))
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            showNoteImportConfirmation = false
            isImportingNotes = false
            pendingNoteImportContext = null
            pendingNoteImportBackup = null
          },
        ) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }
  if (showNoteExportDialog && project != null) {
    AlertDialog(
      onDismissRequest = { showNoteExportDialog = false },
      title = { Text(stringResource(R.string.reader_menu_export_title)) },
      text = {
        Column {
          TextButton(
            onClick = { scope.launch { prepareNoteExport(fullBackup = true) } },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(stringResource(R.string.reader_menu_export_json))
          }
          TextButton(
            onClick = { scope.launch { prepareNoteExport(fullBackup = false) } },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(stringResource(R.string.reader_menu_export_current_page))
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showNoteExportDialog = false }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }
  if (showPendingNoteExportRecoveryDialog && isPendingNoteExportRecovered) {
    AlertDialog(
      onDismissRequest = { showPendingNoteExportRecoveryDialog = false },
      title = { Text(stringResource(R.string.reader_menu_export_recovery_title)) },
      text = { Text(stringResource(R.string.reader_menu_export_recovery_message)) },
      confirmButton = {
        TextButton(onClick = { scope.launch { resumePendingNoteExport() } }) {
          Text(stringResource(R.string.reader_menu_export_recovery_retry))
        }
      },
      dismissButton = {
        Row {
          TextButton(onClick = { scope.launch { discardPendingNoteExport() } }) {
            Text(stringResource(R.string.reader_menu_export_recovery_discard))
          }
          TextButton(onClick = { showPendingNoteExportRecoveryDialog = false }) {
            Text(stringResource(R.string.cancel))
          }
        }
      },
    )
  }
  if (showNoteHistory && project != null) {
    NoteHistoryDialog(
      page = pageIndex + 1,
      projectId = project!!.manifest.id,
      currentDraft = noteDraft,
      baseRevision = noteBaseRevision,
      onDismiss = { showNoteHistory = false },
      onRestore = { restored ->
        readerSession.setNoteDraft(restored)
        noteBaseRevision = PageNotes.revision(restored)
        noteStatus = context.getString(R.string.note_history_restored)
        showNoteHistory = false
      },
    )
  }
}

@Composable
internal fun NewProjectDialog(
  name: String,
  onNameChange: (String) -> Unit,
  hasDir: Boolean,
  pdfDisplayName: String?,
  error: String?,
  creating: Boolean,
  onChooseDir: () -> Unit,
  onChoosePdf: () -> Unit,
  onClearPdf: () -> Unit,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.new_project_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = onNameChange,
          modifier = Modifier.fillMaxWidth().testTag("new_project_name_input"),
          label = { Text(stringResource(R.string.new_project_name)) },
          singleLine = true,
        )
        TextButton(
          modifier = Modifier.testTag("new_project_choose_dir"),
          onClick = onChooseDir,
          enabled = !creating,
        ) {
          Text(
            if (hasDir) stringResource(R.string.new_project_choose_dir) + " ✓"
            else stringResource(R.string.new_project_choose_dir),
          )
        }
        TextButton(
          modifier = Modifier.testTag("new_project_choose_pdf"),
          onClick = onChoosePdf,
          enabled = !creating,
        ) {
          Text(stringResource(R.string.new_project_choose_pdf))
        }
        pdfDisplayName?.let { selectedName ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = selectedName,
              modifier = Modifier.weight(1f),
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            TextButton(
              modifier = Modifier.testTag("new_project_clear_pdf"),
              onClick = onClearPdf,
              enabled = !creating,
            ) {
              Text(stringResource(R.string.new_project_clear_pdf))
            }
          }
        }
        error?.let {
          Text(it, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      Button(
        modifier = Modifier.testTag("new_project_confirm"),
        onClick = onConfirm,
        enabled = !creating,
      ) {
        Text(stringResource(if (creating) R.string.new_project_creating else R.string.new_project_create))
      }
    },
    dismissButton = {
      TextButton(modifier = Modifier.testTag("new_project_cancel"), onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@Composable
internal fun AnnotationTextInputDialog(
  text: String,
  onTextChange: (String) -> Unit,
  isRegion: Boolean = false,
  allowKindSelection: Boolean = true,
  onKindChange: (Boolean) -> Unit = {},
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.annotation_text_label)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.annotation_hint))
        if (allowKindSelection) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isRegion) {
              Button(
                modifier = Modifier.testTag("annotation_kind_region"),
                onClick = {},
              ) { Text(stringResource(R.string.annotation_kind_region)) }
            } else {
              TextButton(
                modifier = Modifier.testTag("annotation_kind_region"),
                onClick = { onKindChange(true) },
              ) { Text(stringResource(R.string.annotation_kind_region)) }
            }
            if (!isRegion) {
              Button(
                modifier = Modifier.testTag("annotation_kind_text"),
                onClick = {},
              ) { Text(stringResource(R.string.annotation_kind_text)) }
            } else {
              TextButton(
                modifier = Modifier.testTag("annotation_kind_text"),
                onClick = { onKindChange(false) },
              ) { Text(stringResource(R.string.annotation_kind_text)) }
            }
          }
        }
        AndroidView(
          factory = { viewContext ->
            EditText(viewContext).apply {
              inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
              setText(text)
              setSelection(getText().length)
              addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(editable: Editable?) {
                  onTextChange(editable?.toString().orEmpty())
                }
              })
            }
          },
          update = { editText ->
            if (editText.text.toString() != text) {
              editText.setText(text)
              editText.setSelection(editText.text.length)
            }
          },
        )
      }
    },
    confirmButton = {
      Button(
        modifier = Modifier.testTag("annotation_confirm"),
        onClick = onConfirm,
        enabled = isRegion || text.isNotBlank(),
      ) { Text(stringResource(R.string.annotation_add)) }
    },
    dismissButton = {
      TextButton(modifier = Modifier.testTag("annotation_cancel"), onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@Composable
internal fun AnnotationShapePickerDialog(
  onSelect: (PdfAnnotationDrawTool) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.reader_menu_shape)) },
    text = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
          modifier = Modifier.testTag("annotation_shape_rectangle"),
          onClick = { onSelect(PdfAnnotationDrawTool.RECTANGLE) },
        ) { Text(stringResource(R.string.annotation_shape_rectangle)) }
        TextButton(
          modifier = Modifier.testTag("annotation_shape_ellipse"),
          onClick = { onSelect(PdfAnnotationDrawTool.ELLIPSE) },
        ) { Text(stringResource(R.string.annotation_shape_ellipse)) }
      }
    },
    confirmButton = {},
  )
}

@Composable
internal fun InkColorPickerDialog(
  currentColor: String,
  onApply: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var draftColor by remember(currentColor) { mutableStateOf(currentColor) }
  val colorIsValid = PenStyle.isValidColor(draftColor)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.reader_menu_color)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          PenStyle.COLOR_SWATCHES.forEach { (name, hex) ->
            val isSelected = draftColor.equals(hex, ignoreCase = true)
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(android.graphics.Color.parseColor(hex)))
                .border(
                  width = if (isSelected) 3.dp else 1.dp,
                  color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                  shape = CircleShape,
                )
                .clickable { draftColor = hex }
                .semantics {
                  contentDescription = "$name $hex"
                  selected = isSelected
                }
                .testTag("ink_color_swatch_$name"),
            )
          }
        }
        OutlinedTextField(
          modifier = Modifier.fillMaxWidth().testTag("ink_color_hex"),
          value = draftColor,
          onValueChange = { draftColor = it },
          label = { Text(stringResource(R.string.pen_color_hex_label)) },
          singleLine = true,
          isError = !colorIsValid,
        )
        if (!colorIsValid) {
          Text(
            text = stringResource(R.string.pen_color_invalid),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        modifier = Modifier.testTag("ink_color_apply"),
        enabled = colorIsValid,
        onClick = { onApply(draftColor.lowercase(Locale.ROOT)) },
      ) { Text(stringResource(R.string.save)) }
    },
    dismissButton = {
      TextButton(modifier = Modifier.testTag("ink_color_cancel"), onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@Composable
internal fun InkWidthPickerDialog(
  currentWidth: Double,
  onApply: (Double) -> Unit,
  onDismiss: () -> Unit,
) {
  var selectedWidth by remember(currentWidth) { mutableStateOf(currentWidth) }
  val widthLabels = listOf(
    R.string.pen_width_thin,
    R.string.pen_width_standard,
    R.string.pen_width_thick,
    R.string.pen_width_extra_thick,
  )
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.reader_menu_width)) },
    text = {
      Column {
        PenStyle.WIDTH_OPTIONS.forEachIndexed { index, width ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { selectedWidth = width }
              .testTag("ink_width_${width.toInt()}"),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = selectedWidth == width,
              onClick = { selectedWidth = width },
            )
            Text("${stringResource(widthLabels[index])} ($width)")
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        modifier = Modifier.testTag("ink_width_apply"),
        onClick = { onApply(selectedWidth) },
      ) { Text(stringResource(R.string.save)) }
    },
    dismissButton = {
      TextButton(modifier = Modifier.testTag("ink_width_cancel"), onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@Composable
internal fun AnnotationListDialog(
  page: Int,
  project: OpenedProject,
  onDismiss: () -> Unit,
  onChanged: (String) -> Unit,
  onDelete: (suspend (Int, Int) -> String?)? = null,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var items by remember(project, page) { mutableStateOf<List<kotlinx.serialization.json.JsonElement>>(emptyList()) }
  var loadError by remember(project, page) { mutableStateOf<String?>(null) }
  var pendingDelete by remember { mutableStateOf<Int?>(null) }

  LaunchedEffect(project, page) {
    loadError = null
    items = withContext(Dispatchers.IO) {
      runCatching {
        ProjectAnnotationStore(
          context.contentResolver, project.sourceDirectoryUri, project.annotationsUri,
        ).readPage("document", page).toList()
      }.getOrElse {
        loadError = context.getString(R.string.annotation_save_failed, it.message ?: "")
        emptyList()
      }
    }
  }

  fun nonPenEntries(): List<Pair<Int, kotlinx.serialization.json.JsonObject>> =
    items.mapIndexedNotNull { index, element ->
      val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapIndexedNotNull null
      val type = obj["type"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNullFallback() } ?: ""
      if (type == "pen" || type == "highlight") null else index to obj
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.annotation_title, page)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val entries = nonPenEntries()
        if (loadError == null && entries.isEmpty()) {
          Text(stringResource(R.string.annotation_empty))
        }
        entries.forEach { (index, obj) ->
          val type = obj["type"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNullFallback() } ?: ""
          val supportedVector = type == "line" || type == "arrow" || type == "ellipse"
          val label = if (type == "text" || type == "rect" || supportedVector) {
            val text = obj["text"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNullFallback() }.orEmpty()
            if (text.isBlank()) context.getString(R.string.annotation_other_type, type) else text
          } else {
            context.getString(R.string.annotation_other_type, type)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(label, modifier = Modifier.weight(1f))
            if (type == "text" || type == "rect" || supportedVector) {
              TextButton(
                modifier = Modifier.testTag("annotation_delete_$index"),
                onClick = { pendingDelete = index },
              ) { Text(stringResource(R.string.delete)) }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(modifier = Modifier.testTag("annotations_close"), onClick = onDismiss) {
        Text(stringResource(R.string.close))
      }
    },
  )
  if (pendingDelete != null) {
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text(stringResource(R.string.delete)) },
      text = { Text(stringResource(R.string.annotation_hint)) },
      confirmButton = {
        TextButton(
          modifier = Modifier.testTag("annotation_delete_confirm"),
          onClick = {
            val target = pendingDelete ?: return@TextButton
            scope.launch {
              try {
                val retainedRecoveryCopyName = if (onDelete != null) {
                  onDelete(page, target)
                } else {
                  withContext(Dispatchers.IO) {
                    val store = ProjectAnnotationStore(
                      context.contentResolver, project.sourceDirectoryUri, project.annotationsUri,
                    )
                    val current = store.readPage("document", page).toMutableList()
                    current.removeAt(target)
                    store.writePage("document", page, kotlinx.serialization.json.JsonArray(current))
                    store.lastRetainedRecoveryCopyName
                  }
                }
                items = items.toMutableList().also { it.removeAt(target) }
                onChanged(retainedRecoveryCopyName?.let {
                  context.getString(R.string.annotation_deleted_recovery_copy, it)
                } ?: context.getString(R.string.annotation_deleted))
              } catch (error: Exception) {
                onChanged(context.getString(R.string.annotation_save_failed, error.message ?: ""))
              } finally {
                pendingDelete = null
              }
            }
          },
        ) { Text(stringResource(R.string.delete)) }
      },
      dismissButton = {
        TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
      },
    )
  }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullFallback(): String? =
  try {
    if (isString) content else null
  } catch (_: Exception) {
    null
  }

private fun Map<String, Any?>.toJsonElement(): kotlinx.serialization.json.JsonElement = when {
  this == null -> kotlinx.serialization.json.JsonNull
  else -> kotlinx.serialization.json.JsonObject(
    entries.associate { (k, v) -> k to v.toJsonElementValue() },
  )
}

private fun Any?.toJsonElementValue(): kotlinx.serialization.json.JsonElement = when (this) {
  null -> kotlinx.serialization.json.JsonNull
  is String -> kotlinx.serialization.json.JsonPrimitive(this as String)
  is Number -> try {
    kotlinx.serialization.json.JsonPrimitive(this as Number)
  } catch (_: Exception) {
    kotlinx.serialization.json.JsonPrimitive(toString())
  }
  is Boolean -> kotlinx.serialization.json.JsonPrimitive(this as Boolean)
  is Map<*, *> -> (this as Map<String, Any?>).toJsonElement()
  is Iterable<*> -> kotlinx.serialization.json.JsonArray(map { it.toJsonElementValue() })
  else -> kotlinx.serialization.json.JsonPrimitive(toString())
}

@Composable
internal fun ReviewListDialog(
  page: Int,
  project: OpenedProject,
  noteRevision: String,
  selectionDraft: NoteSelectionReviewDraft? = null,
  pdfSelectionDraft: PdfSelectionReviewDraft? = null,
  onDismiss: () -> Unit,
  onChanged: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var records by remember(project, page) { mutableStateOf<List<JsonObject>>(emptyList()) }
  var loadError by remember(project, page) { mutableStateOf<String?>(null) }
  var instruction by remember { mutableStateOf("") }
  var pendingDismiss by remember { mutableStateOf<String?>(null) }
  var pendingDelete by remember { mutableStateOf<String?>(null) }

  fun store() = ProjectReviewStore(context.contentResolver, project.sourceDirectoryUri, project.reviewsUri)

  LaunchedEffect(project, page) {
    loadError = null
    records = withContext(Dispatchers.IO) {
      runCatching {
        store().list(page).mapNotNull { it as? JsonObject }
      }.getOrElse {
        loadError = context.getString(R.string.review_save_failed, it.message ?: "")
        emptyList()
      }
    }
  }

  suspend fun refresh() {
    records = withContext(Dispatchers.IO) {
      runCatching { store().list(page).mapNotNull { it as? JsonObject } }.getOrDefault(records)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.review_title, page)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        selectionDraft?.let { draft ->
          Text(stringResource(R.string.review_selection_quote_label))
          Text(
            draft.quote,
            modifier = Modifier.testTag("review_selection_quote"),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        pdfSelectionDraft?.let { draft ->
          Text(stringResource(R.string.review_selection_quote_label))
          Text(
            draft.quote,
            modifier = Modifier.testTag("review_selection_quote"),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        if (loadError == null && records.isEmpty()) {
          Text(stringResource(R.string.review_empty))
        }
        records.forEach { record ->
          val id = record["id"]?.let { (it as? JsonPrimitive)?.contentOrNullFallback() } ?: ""
          val text = record["instruction"]?.let { (it as? JsonPrimitive)?.contentOrNullFallback() } ?: ""
          val status = record["status"]?.let { (it as? JsonPrimitive)?.contentOrNullFallback() } ?: ""
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(text)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(status, style = MaterialTheme.typography.labelSmall)
              if (status == "open") {
                TextButton(onClick = { pendingDismiss = id }) { Text(stringResource(R.string.review_dismissed)) }
              }
              TextButton(onClick = { pendingDelete = id }) { Text(stringResource(R.string.delete)) }
            }
          }
        }
        Text(stringResource(R.string.review_instruction_label))
        AndroidView(
          modifier = Modifier.fillMaxWidth().testTag("review_instruction_editor"),
          factory = { viewContext ->
            EditText(viewContext).apply {
              inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
              setText(instruction)
              addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(editable: Editable?) {
                  instruction = editable?.toString().orEmpty()
                }
              })
            }
          },
          update = { editText ->
            if (editText.text.toString() != instruction) editText.setText(instruction)
          },
        )
        Button(
          modifier = Modifier.testTag("review_create"),
          onClick = {
            scope.launch {
              try {
                val recoveryCopyName = withContext(Dispatchers.IO) {
                  val reviewStore = store()
                  val noteDraft = selectionDraft
                  val pdfDraft = pdfSelectionDraft
                  if (noteDraft == null && pdfDraft == null) {
                    reviewStore.create("note", "overall", page, instruction, noteRevision)
                  } else if (noteDraft != null) {
                    val noteStore = ProjectNoteStore(context.contentResolver, project.notesUri)
                    noteStore.withRevisionLock {
                      val latestNotes = noteStore.read()
                      val latestPage = NotePage.pageAt(latestNotes, page)
                      try {
                        require(noteDraft.page == page) { "selection belongs to another page" }
                        noteDraft.requireCurrentPage(latestPage)
                      } catch (_: IllegalArgumentException) {
                        throw StaleSelectionReviewException()
                      }
                      reviewStore.create(
                        ReviewCreate(
                          side = "note",
                          kind = "selection",
                          page = page,
                          instruction = instruction,
                          scope = "page",
                          quote = noteDraft.quote,
                          context = noteDraft.context,
                          revision = noteDraft.revision,
                          anchor = noteDraft.anchor,
                        ),
                      )
                    }
                  } else {
                    val draft = requireNotNull(pdfDraft)
                    val noteStore = ProjectNoteStore(context.contentResolver, project.notesUri)
                    noteStore.withRevisionLock {
                      val latestNotes = noteStore.read()
                      val latestPage = NotePage.pageAt(latestNotes, page)
                      try {
                        require(draft.page == page) { "selection belongs to another page" }
                        draft.requireCurrentNote(latestPage)
                      } catch (_: IllegalArgumentException) {
                        throw StaleSelectionReviewException()
                      }
                      reviewStore.create(
                        ReviewCreate(
                          side = "document",
                          kind = "selection",
                          page = page,
                          instruction = instruction,
                          scope = "page",
                          quote = draft.quote,
                          context = draft.context,
                          revision = draft.revision,
                          anchor = draft.anchor,
                          targetNoteRevision = draft.targetNoteRevision,
                        ),
                      )
                    }
                  }
                  reviewStore.lastRetainedRecoveryCopyName
                }
                instruction = ""
                refresh()
                onChanged(
                  recoveryCopyName?.let {
                    context.getString(R.string.review_saved_recovery_copy, it)
                  } ?: context.getString(R.string.review_saved),
                )
              } catch (_: StaleSelectionReviewException) {
                onChanged(context.getString(R.string.review_selection_stale))
              } catch (error: Exception) {
                onChanged(reviewCreateErrorMessage(context, error))
              }
            }
          },
          enabled = instruction.isNotBlank(),
        ) { Text(stringResource(R.string.review_save)) }
      }
    },
    confirmButton = {
      TextButton(modifier = Modifier.testTag("reviews_close"), onClick = onDismiss) {
        Text(stringResource(R.string.close))
      }
    },
  )
  pendingDismiss?.let { id ->
    scope.launch {
      try {
        val recoveryCopyName = withContext(Dispatchers.IO) {
          val reviewStore = store()
          reviewStore.transition(id, "dismissed")
          reviewStore.lastRetainedRecoveryCopyName
        }
        refresh()
        onChanged(
          recoveryCopyName?.let {
            context.getString(R.string.review_updated_recovery_copy, it)
          } ?: context.getString(R.string.review_dismissed),
        )
      } catch (error: Exception) {
        onChanged(context.getString(R.string.review_save_failed, error.message ?: ""))
      } finally {
        pendingDismiss = null
      }
    }
  }
  pendingDelete?.let { id ->
    scope.launch {
      try {
        val recoveryCopyName = withContext(Dispatchers.IO) {
          val reviewStore = store()
          reviewStore.delete(id)
          reviewStore.lastRetainedRecoveryCopyName
        }
        refresh()
        onChanged(
          recoveryCopyName?.let {
            context.getString(R.string.review_updated_recovery_copy, it)
          } ?: context.getString(R.string.review_deleted),
        )
      } catch (error: Exception) {
        onChanged(context.getString(R.string.review_save_failed, error.message ?: ""))
      } finally {
        pendingDelete = null
      }
    }
  }
}

internal fun reviewCreateErrorMessage(context: Context, error: Throwable): String = when (error) {
  is IncompleteReviewFileInitializationException ->
    context.getString(R.string.review_incomplete_file_may_remain)
  is IllegalArgumentException -> context.getString(R.string.review_invalid_input)
  else -> context.getString(R.string.review_save_failed, error.message ?: "")
}

private class StaleSelectionReviewException : Exception()

internal fun selectionToAnnotationEnabled(
  selection: TextRange?,
  markdown: String,
  writable: Boolean,
  readerIdle: Boolean,
): Boolean {
  if (!writable || !readerIdle) return false
  return selectionReviewReadiness(selection?.start, selection?.end, markdown) ==
    SelectionReviewReadiness.AVAILABLE
}

@Composable
internal fun HandoffDialog(
  page: Int,
  project: OpenedProject,
  noteRevision: String,
  sourceRevision: String,
  onDismiss: () -> Unit,
  onChanged: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var view by remember(project, page) { mutableStateOf<HandoffSafView?>(null) }
  var summary by remember { mutableStateOf("") }
  var loadError by remember(project, page) { mutableStateOf<String?>(null) }

  fun store() = ProjectHandoffStore(context.contentResolver, project.sourceDirectoryUri, project.handoffsUri)

  LaunchedEffect(project, page, noteRevision, sourceRevision) {
    loadError = null
    view = withContext(Dispatchers.IO) {
      runCatching { store().view(page, noteRevision, sourceRevision) }.getOrElse {
        loadError = context.getString(R.string.handoff_save_failed, it.message ?: "")
        null
      }
    }
    view?.record?.let { summary = it.summary }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.handoff_title, page)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val current = view
        if (current?.record == null && loadError == null) {
          Text(stringResource(R.string.handoff_empty))
        }
        current?.record?.let { Text(it.summary) }
        if (current?.stale == true) {
          Text(stringResource(R.string.handoff_stale), color = MaterialTheme.colorScheme.error)
        }
        Text(stringResource(R.string.handoff_summary_label))
        AndroidView(
          factory = { viewContext ->
            EditText(viewContext).apply {
              inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
              setText(summary)
              addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(editable: Editable?) {
                  summary = editable?.toString().orEmpty()
                }
              })
            }
          },
          update = { editText ->
            if (editText.text.toString() != summary) editText.setText(summary)
          },
        )
        Button(
          modifier = Modifier.testTag("handoff_save"),
          onClick = {
            scope.launch {
              try {
                val saved = withContext(Dispatchers.IO) {
                  store().write(page, summary, noteRevision, sourceRevision, noteRevision)
                }
                view = HandoffSafView(saved, false)
                onChanged(context.getString(R.string.handoff_saved))
              } catch (error: Exception) {
                onChanged(context.getString(R.string.handoff_save_failed, error.message ?: ""))
              }
            }
          },
          enabled = summary.isNotBlank() && current?.stale != true,
        ) { Text(stringResource(R.string.handoff_save)) }
      }
    },
    confirmButton = {
      TextButton(modifier = Modifier.testTag("handoff_close"), onClick = onDismiss) {
        Text(stringResource(R.string.close))
      }
    },
  )
}

private fun isReaderErrorStatus(status: String): Boolean =
  status.contains("冲突") || status.contains("失败") || status.contains("只读") ||
    status.contains("conflict", ignoreCase = true) || status.contains("fail", ignoreCase = true) ||
    status.contains("read-only", ignoreCase = true) || status.contains("not saved", ignoreCase = true)

@Composable
internal fun MarkdownSourcePane(
  markdown: String,
  sourceName: String,
  page: Int,
  sourceDirectoryUri: Uri?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val contentColorInt = MaterialTheme.colorScheme.onSurface.toArgb()
  val mermaidCoordinator = remember(context) { MermaidRenderCoordinator(context) }
  DisposableEffect(mermaidCoordinator) {
    onDispose { mermaidCoordinator.close() }
  }
  val markwon = remember(context, sourceDirectoryUri, contentColorInt, mermaidCoordinator) {
    NoteMarkdownRenderer.createPreview(context, sourceDirectoryUri, contentColorInt, mermaidCoordinator)
  }
  val (previewText, truncated) = remember(markdown, page) {
    MarkdownSourcePages.previewPage(markdown, page)
  }
  val rewritten = remember(previewText) { NotePreviewRewrite.forPreview(previewText) }
  val fullPageText = remember(markdown, page) { MarkdownSourcePages.pageAt(markdown, page) }
  Column(modifier = modifier.padding(8.dp).testTag("markdown_source_pane")) {
    Text(
      text = "$sourceName · ${page}",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (previewText.isNotBlank()) {
      NoteRichDiagnosticSummary(
        markdown = fullPageText,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
      )
    }
    if (previewText.isBlank()) {
      Text(
        text = stringResource(R.string.markdown_source_empty),
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      AndroidView(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
        factory = { viewContext ->
          MermaidPreviewHost(viewContext, mermaidCoordinator, contentColorInt)
        },
        update = { host ->
          host.setMarkdown(markwon, rewritten, contentColorInt)
        },
      )
    }
    if (truncated) {
      Text(
        text = stringResource(
          R.string.note_preview_truncated,
          fullPageText.length,
          previewText.length,
        ),
        modifier = Modifier.padding(top = 4.dp).testTag("note_preview_truncated"),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
internal fun NoteRichDiagnosticSummary(markdown: String, modifier: Modifier = Modifier) {
  val diagnostics = remember(markdown) { NoteRichContent.extract(markdown).diagnostics }
  if (diagnostics.isNotEmpty()) {
    Column(
      modifier = modifier.testTag("note_rich_diagnostics"),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      diagnostics.forEach { diagnostic ->
        val message = when (diagnostic) {
          NoteDiagnostic.UNCLOSED_CODE_FENCE -> R.string.note_rich_unclosed
          NoteDiagnostic.UNTERMINATED_FORMULA -> R.string.note_rich_formula_unclosed
        }
        Text(
          text = stringResource(message),
          modifier = Modifier.testTag("note_rich_diagnostic_${diagnostic.name}"),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
internal fun PdfPageViewport(
  bitmap: Bitmap?,
  sourceName: String,
  page: Int,
  annotations: List<PdfAnnotationOverlayItem> = emptyList(),
  annotationImages: Map<String, Bitmap> = emptyMap(),
  annotationHtmlBitmaps: Map<Int, Bitmap> = emptyMap(),
  selectedAnnotationBounds: PdfAnnotationBounds? = null,
  selectedAnnotationResizable: Boolean = false,
  loading: Boolean,
  strokes: List<PenStroke>,
  tool: PenCanvasTool,
  inputEnabled: Boolean,
  onStrokeAdded: (PenStroke) -> Unit,
  onShapeRecognized: (PenRecognizedShape, PenStroke) -> Boolean = { _, _ -> false },
  onStrokeErased: (Int) -> Unit,
  selectedStrokeIndex: Int? = null,
  onSelectedStrokeChanged: (Int?) -> Unit = {},
  onAnnotationSelectionTouch: (MotionEvent, PenCanvasGeometry, Int) -> Boolean = { _, _, _ -> false },
  transform: PdfViewportTransform,
  onTransformChanged: (PdfViewportTransform) -> Unit,
  onBoundsChanged: (PdfViewportBounds) -> Unit,
  pageText: PdfPageText? = null,
  textSelectionEnabled: Boolean = false,
  textSelection: PdfTextSelection? = null,
  onTextSelectionChanged: (PdfTextSelection?) -> Unit = {},
  selectionEnabled: Boolean = false,
  gesturesEnabled: Boolean = true,
  selection: NormalizedOcrRegion? = null,
  onSelectionChanged: (NormalizedOcrRegion?) -> Unit = {},
  annotateTapEnabled: Boolean = false,
  onAnnotateTap: (PdfSelectionPoint) -> Unit = {},
  annotationDrawTool: PdfAnnotationDrawTool? = null,
  onAnnotationDrag: (PdfSelectionPoint, PdfSelectionPoint, Boolean) -> Unit = { _, _, _ -> },
  pageSwipesEnabled: Boolean = false,
  onPageSwipe: (PdfPageSwipeDirection) -> Unit = {},
  allowInkOutsidePage: Boolean = false,
  penStyle: PenStyle = PenStyle.DEFAULT,
  modifier: Modifier = Modifier,
) {
  val latestTransform by rememberUpdatedState(transform)
  val latestOnTransformChanged by rememberUpdatedState(onTransformChanged)
  val latestOnTextSelectionChanged by rememberUpdatedState(onTextSelectionChanged)
  val latestOnSelectionChanged by rememberUpdatedState(onSelectionChanged)
  val latestOnAnnotateTap by rememberUpdatedState(onAnnotateTap)
  val latestOnAnnotationDrag by rememberUpdatedState(onAnnotationDrag)
  val latestOnPageSwipe by rememberUpdatedState(onPageSwipe)
  val latestOnSelectedStrokeChanged by rememberUpdatedState(onSelectedStrokeChanged)
  val latestOnAnnotationSelectionTouch by rememberUpdatedState(onAnnotationSelectionTouch)
  var inkCanvasView by remember { mutableStateOf<PenCanvasView?>(null) }

  BoxWithConstraints(
    modifier = modifier.clipToBounds().testTag("pdf_page_viewport"),
    contentAlignment = Alignment.Center,
  ) {
    if (bitmap != null) {
      val pageAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
      val viewportAspectRatio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
      val contentWidth = if (viewportAspectRatio > pageAspectRatio) {
        maxHeight * pageAspectRatio
      } else {
        maxWidth
      }
      val contentHeight = contentWidth / pageAspectRatio
      val density = LocalDensity.current
      val bounds = with(density) {
        PdfViewportBounds(
          viewportWidthPx = maxWidth.toPx(),
          viewportHeightPx = maxHeight.toPx(),
          pageWidthPx = contentWidth.toPx(),
          pageHeightPx = contentHeight.toPx(),
        )
      }
      LaunchedEffect(page, bounds) { onBoundsChanged(bounds) }

      val pageLayerModifier = Modifier.width(contentWidth).height(contentHeight).graphicsLayer {
        scaleX = transform.scale
        scaleY = transform.scale
        translationX = transform.translationX
        translationY = transform.translationY
      }
      Box(
        modifier = Modifier.fillMaxSize().pointerInput(
          page, bounds, pageText, textSelectionEnabled, selectionEnabled, gesturesEnabled,
          annotateTapEnabled, annotationDrawTool, pageSwipesEnabled,
        ) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (textSelectionEnabled && down.type != PointerType.Mouse) {
              val start = PdfSelectionPoint(down.position.x, down.position.y)
              down.consume()
              var movedBeyondSlop = false
              do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null) {
                  val end = PdfSelectionPoint(change.position.x, change.position.y)
                  movedBeyondSlop = movedBeyondSlop ||
                    kotlin.math.hypot(end.x - start.x, end.y - start.y) > viewConfiguration.touchSlop
                  if (movedBeyondSlop) {
                    val activePageText = pageText
                    val points = activePageText?.let {
                      mapPdfTextSelectionToPagePoints(
                        start = start,
                        end = end,
                        bounds = bounds,
                        transform = latestTransform,
                        pageWidth = it.pageWidth,
                        pageHeight = it.pageHeight,
                      )
                    }
                    latestOnTextSelectionChanged(
                      if (activePageText != null && points != null) {
                        PdfTextSelectionMapper.select(activePageText, points.first, points.second)
                      } else {
                        null
                      },
                    )
                  }
                  if (change.pressed) change.consume()
                }
              } while (event.changes.any { it.id == down.id && it.pressed })
              if (!movedBeyondSlop) latestOnTextSelectionChanged(null)
            } else if (annotationDrawTool != null && down.type != PointerType.Mouse) {
              val start = PdfSelectionPoint(down.position.x, down.position.y)
              var end = start
              down.consume()
              latestOnAnnotationDrag(start, end, false)
              do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null) {
                  end = PdfSelectionPoint(change.position.x, change.position.y)
                  val finished = !change.pressed
                  change.consume()
                  latestOnAnnotationDrag(start, end, finished)
                }
              } while (event.changes.any { it.id == down.id && it.pressed })
            } else if (annotateTapEnabled && down.type != PointerType.Mouse) {
              val tap = PdfSelectionPoint(down.position.x, down.position.y)
              down.consume()
              latestOnAnnotateTap(tap)
              do {
                val event = awaitPointerEvent()
                event.changes.forEach { if (it.pressed) it.consume() }
              } while (event.changes.any { it.id == down.id && it.pressed })
            } else if (selectionEnabled && down.type != PointerType.Mouse) {
              val start = PdfSelectionPoint(down.position.x, down.position.y)
              down.consume()
              var deliveredValid = false
              var lastEnd: PdfSelectionPoint? = null
              do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null) {
                  val endPoint = PdfSelectionPoint(change.position.x, change.position.y)
                  lastEnd = endPoint
                  // Deliver valid regions live; defer invalid to drag end so a
                  // tap does not flash a message before it is confirmed and a
                  // failed re-drag keeps the previous valid selection.
                  if (endPoint.x != start.x || endPoint.y != start.y) {
                    val region = mapPdfSelectionToOcrRegion(
                      start = start,
                      end = endPoint,
                      bounds = bounds,
                      transform = latestTransform,
                    )
                    if (region != null) {
                      deliveredValid = true
                      latestOnSelectionChanged(region)
                    }
                  }
                  if (change.pressed) change.consume()
                }
              } while (event.changes.any { it.id == down.id && it.pressed })
              // Report a tap or fully invalid drag once at drag end so
              // ReaderOcrActions can show the recoverable message.
              val end = lastEnd
              if (!deliveredValid && (end == null ||
                ((end.x == start.x && end.y == start.y) ||
                mapPdfSelectionToOcrRegion(
                  start = start,
                  end = end,
                  bounds = bounds,
                  transform = latestTransform,
                ) == null))
              ) {
                latestOnSelectionChanged(null)
              }
            } else if (gesturesEnabled || pageSwipesEnabled) {
              var swipeHandled = false
              var multiTouchSeen = false
              do {
                val event = awaitPointerEvent()
                val touchChanges = event.changes.filter {
                  it.type == PointerType.Touch && it.pressed && it.previousPressed
                }
                if (touchChanges.size >= 2) multiTouchSeen = true
                val pointers = touchChanges.map { change ->
                  PdfViewportPointer(change.id.value, change.position.x, change.position.y)
                }
                val previousPointers = touchChanges.map { change ->
                  PdfViewportPointer(change.id.value, change.previousPosition.x, change.previousPosition.y)
                }
                val gesture = PdfViewportTransform.calculateTwoPointerGesture(previousPointers, pointers)
                if (gesture != null) {
                  latestOnTransformChanged(PdfViewportTransform.applyGesture(
                    current = latestTransform,
                    viewportWidthPx = bounds.viewportWidthPx,
                    viewportHeightPx = bounds.viewportHeightPx,
                    pageWidthPx = bounds.pageWidthPx,
                    pageHeightPx = bounds.pageHeightPx,
                    centroidX = gesture.centroidX,
                    centroidY = gesture.centroidY,
                    zoomChange = gesture.zoomChange,
                    panX = gesture.panX,
                    panY = gesture.panY,
                  ))
                  touchChanges.forEach { it.consume() }
                }
                if (!swipeHandled && !multiTouchSeen && pageSwipesEnabled && down.type == PointerType.Touch) {
                  val change = touchChanges.firstOrNull { it.id == down.id }
                  if (change != null) {
                    val direction = PdfPageSwipePolicy.direction(
                      deltaX = change.position.x - down.position.x,
                      deltaY = change.position.y - down.position.y,
                      density = density.density,
                    )
                    if (direction != null) {
                      change.consume()
                      inkCanvasView?.cancelActiveGesture()
                      latestOnPageSwipe(direction)
                      swipeHandled = true
                    }
                  }
                } else if (swipeHandled) {
                  event.changes.forEach { if (it.pressed) it.consume() }
                }
              } while (event.changes.any { it.pressed })
            } else {
              do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
              } while (event.changes.any { it.pressed })
            }
          }
        },
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier = pageLayerModifier,
          contentAlignment = Alignment.Center,
        ) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.page_image_description, sourceName, page + 1),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
          )
          PdfAnnotationOverlay(
            annotations,
            images = annotationImages,
            htmlBitmaps = annotationHtmlBitmaps,
            selectedBounds = selectedAnnotationBounds,
            showResizeHandle = selectedAnnotationResizable,
          )
        }
        AndroidView(
          modifier = Modifier.fillMaxSize().testTag("pdf_ink_canvas"),
          factory = { viewContext -> PenCanvasView(viewContext).also { inkCanvasView = it } },
          update = { view ->
            view.strokes = strokes
            view.selectedTool = tool
            view.selectedIndex = selectedStrokeIndex
            view.penColor = penStyle.color
            view.penWidth = penStyle.width
            view.inputEnabled = inputEnabled
            view.pageTransform = PenCanvasPageTransform(
              pageWidthPx = bounds.pageWidthPx,
              pageHeightPx = bounds.pageHeightPx,
              scale = transform.scale,
              translationX = transform.translationX,
              translationY = transform.translationY,
              allowOutsidePage = allowInkOutsidePage,
            )
            view.onStrokeAdded = onStrokeAdded
            view.onShapeRecognized = onShapeRecognized
            view.onStrokeErased = onStrokeErased
            view.onSelectionChanged = latestOnSelectedStrokeChanged
            view.onAnnotationSelectionTouch = latestOnAnnotationSelectionTouch
          },
        )
        Box(modifier = pageLayerModifier, contentAlignment = Alignment.Center) {
          OcrSelectionOverlay(selection)
          PdfTextSelectionOverlay(pageText, textSelection)
        }
      }
    } else if (loading) {
      Text(stringResource(R.string.loading_page), style = MaterialTheme.typography.bodyLarge)
    }
  }
}

@Composable
internal fun NotePanel(
  page: Int,
  editRequest: Int = 0,
  markdown: String,
  dirty: Boolean,
  writable: Boolean,
  saving: Boolean,
  status: String?,
  onMarkdownChange: (String) -> Unit,
  onMarkdownMutation: (String, String) -> Unit = { _, _ -> },
  onEditorTextReset: (String) -> Unit = {},
  onSave: () -> Unit,
  onReload: () -> Unit,
  onSelectionChanged: (TextRange?) -> Unit = {},
  onEditorViewAttached: (EditText) -> Unit = {},
  onEditorViewReleased: (EditText) -> Unit = {},
  historyEnabled: Boolean = false,
  onHistory: () -> Unit = {},
  sourceDirectoryUri: Uri? = null,
  animationEnabled: Boolean,
  noteFontSize: Int = 15,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val selectionCallback = rememberUpdatedState(onSelectionChanged)
  val markdownMutationCallback = rememberUpdatedState(onMarkdownMutation)
  val editorTextResetCallback = rememberUpdatedState(onEditorTextReset)
  val editorAttachedCallback = rememberUpdatedState(onEditorViewAttached)
  val editorReleasedCallback = rememberUpdatedState(onEditorViewReleased)
  val contentColorInt = MaterialTheme.colorScheme.onSurface.toArgb()
  var preview by remember { mutableStateOf(false) }
  val mermaidCoordinator = remember(context, preview) {
    if (preview) MermaidRenderCoordinator(context) else null
  }
  DisposableEffect(mermaidCoordinator) {
    onDispose { mermaidCoordinator?.close() }
  }
  val markwon = remember(context, sourceDirectoryUri, contentColorInt, mermaidCoordinator, noteFontSize) {
    NoteMarkdownRenderer.createPreview(
      context,
      sourceDirectoryUri,
      contentColorInt,
      mermaidCoordinator,
      baseFontSizeSp = noteFontSize.toFloat(),
    )
  }
  val markwonEditor = remember(context, sourceDirectoryUri, contentColorInt) {
    NoteMarkdownRenderer.createEditor(context, sourceDirectoryUri, contentColorInt)
  }
  val executor = remember { Executors.newSingleThreadExecutor() }
  val outlineColor = MaterialTheme.colorScheme.outline.toArgb()

  LaunchedEffect(page, preview) {
    selectionCallback.value(null)
  }

  LaunchedEffect(editRequest) {
    if (editRequest > 0) preview = false
  }

  DisposableEffect(executor) {
    onDispose { executor.shutdownNow() }
  }

  Column(modifier = modifier.padding(8.dp)) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      NotePanelHeader(
        page = page,
        layout = notePanelHeaderLayout(maxWidth.value),
        saving = saving,
        writable = writable,
        dirty = dirty,
        onReload = onReload,
        onSave = onSave,
        historyEnabled = historyEnabled,
        onHistory = onHistory,
      )
    }
    TabRow(selectedTabIndex = if (preview) 1 else 0) {
      Tab(
        selected = !preview,
        onClick = { preview = false },
        modifier = Modifier.testTag("note_tab_edit"),
        text = { Text(stringResource(R.string.note_edit)) },
      )
      Tab(
        selected = preview,
        onClick = { preview = true },
        modifier = Modifier.testTag("note_tab_preview"),
        text = { Text(stringResource(R.string.note_preview)) },
      )
    }
    if (!writable) {
      Text(
        stringResource(R.string.read_only_project),
        modifier = Modifier.padding(top = 5.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.labelMedium,
      )
    }
    status?.let {
      Text(
        it,
        modifier = Modifier.padding(top = 5.dp),
        color = if (isReaderErrorStatus(it)) {
          MaterialTheme.colorScheme.error
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
        style = MaterialTheme.typography.labelMedium,
      )
    }
    AnimatedContent(
      targetState = ReaderNoteContent(page, markdown, preview),
      contentKey = { it.page },
      transitionSpec = {
        val duration = notePageTransitionDurationMillis(animationEnabled)
        fadeIn(animationSpec = tween(duration)) togetherWith fadeOut(animationSpec = tween(duration))
      },
      modifier = Modifier.fillMaxSize().padding(top = 8.dp),
      label = "page-note-transition",
    ) { content ->
      key(content.page, content.preview) {
        if (content.preview) {
          val (previewMarkdown, isTruncated) = remember(content.markdown) {
            NotePage.preview(content.markdown)
          }
          Column(modifier = Modifier.fillMaxSize()) {
            NoteRichDiagnosticSummary(
              markdown = content.markdown,
              modifier = Modifier.fillMaxWidth(),
            )
            AndroidView(
              modifier = Modifier.weight(1f).fillMaxWidth(),
              factory = { viewContext ->
                MermaidPreviewHost(viewContext, requireNotNull(mermaidCoordinator), contentColorInt)
              },
              update = { host ->
                host.setMarkdown(
                  markwon,
                  NotePreviewRewrite.forPreview(previewMarkdown),
                  contentColorInt,
                  noteFontSize.toFloat(),
                )
              },
            )
            if (isTruncated) {
              Text(
                text = stringResource(
                  R.string.note_preview_truncated,
                  content.markdown.length,
                  previewMarkdown.length,
                ),
                modifier = Modifier.padding(top = 4.dp).testTag("note_preview_truncated"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
              LongTokenMarkdownEditText(viewContext).apply {
                onMarkdownSelectionChanged = { selStart, selEnd ->
                  selectionCallback.value(
                    if (selStart != selEnd) TextRange(selStart, selEnd) else null,
                  )
                }
                tag = "note_markdown_editor"
                gravity = Gravity.TOP or Gravity.START
                inputType = InputType.TYPE_CLASS_TEXT or
                  InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                  InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                textSize = noteFontSize.toFloat()
                setTextColor(contentColorInt)
                backgroundTintList = ColorStateList.valueOf(outlineColor)
                isEnabled = writable && !saving && content.page == page
                setPadding(12, 10, 12, 10)
                setMarkdownSourceText(content.markdown)
                setMarkdownSourceSelection(content.markdown.length)
                addTextChangedListener(
                  MarkwonEditorTextWatcher.withPreRender(markwonEditor, executor, this),
                )
                addTextChangedListener(object : TextWatcher {
                  private var previousMarkdown = ""

                  override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    if (!(this@apply).isSettingMarkdownSource) {
                      previousMarkdown = markdownSourceText()
                    }
                  }
                  override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    (this@apply).noteMarkdownTextChanged(before, count)
                  }
                  override fun afterTextChanged(editable: Editable?) {
                    if (!(this@apply).isSettingMarkdownSource && content.page == page) {
                      (this@apply).refreshBreakHintsIfNeeded()
                      val updatedMarkdown = markdownSourceText()
                      if (previousMarkdown != updatedMarkdown) {
                        markdownMutationCallback.value(previousMarkdown, updatedMarkdown)
                      }
                      onMarkdownChange(updatedMarkdown)
                    }
                  }
                })
              }.also(editorAttachedCallback.value)
            },
            update = { editText ->
              editText.setTextColor(contentColorInt)
              editText.backgroundTintList = ColorStateList.valueOf(outlineColor)
              editText.textSize = noteFontSize.toFloat()
              editText.isEnabled = writable && !saving && content.page == page
              if (editText.markdownSourceText() != content.markdown) {
                selectionCallback.value(null)
                editText.setMarkdownSourceText(content.markdown)
                editText.setMarkdownSourceSelection(content.markdown.length)
                editorTextResetCallback.value(editText.markdownSourceText())
              }
            },
            onRelease = { editor -> editorReleasedCallback.value(editor) },
          )
        }
      }
    }
  }
}

private data class ReaderNoteContent(val page: Int, val markdown: String, val preview: Boolean)

@Composable
private fun NotePanelHeader(
  page: Int,
  layout: NotePanelHeaderLayout,
  saving: Boolean,
  writable: Boolean,
  dirty: Boolean,
  onReload: () -> Unit,
  onSave: () -> Unit,
  historyEnabled: Boolean = false,
  onHistory: () -> Unit = {},
) {
  val actions: @Composable (Boolean, Modifier) -> Unit = { vertical, modifier ->
    NotePanelHeaderActions(
      saving = saving,
      writable = writable,
      dirty = dirty,
      vertical = vertical,
      onReload = onReload,
      onSave = onSave,
      historyEnabled = historyEnabled,
      onHistory = onHistory,
      modifier = modifier,
    )
  }

  when (layout) {
    NotePanelHeaderLayout.INLINE -> Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        stringResource(R.string.note_page_title, page),
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      actions(false, Modifier)
    }
    NotePanelHeaderLayout.STACKED_ACTIONS -> Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(stringResource(R.string.note_page_title, page), style = MaterialTheme.typography.titleSmall)
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        actions(false, Modifier)
      }
    }
    NotePanelHeaderLayout.STACKED_CONTROLS -> Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(stringResource(R.string.note_page_title, page), style = MaterialTheme.typography.titleSmall)
      actions(true, Modifier.fillMaxWidth())
    }
  }
}

@Composable
private fun NotePanelHeaderActions(
  saving: Boolean,
  writable: Boolean,
  dirty: Boolean,
  vertical: Boolean,
  onReload: () -> Unit,
  onSave: () -> Unit,
  historyEnabled: Boolean = false,
  onHistory: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val reloadButton: @Composable (Modifier) -> Unit = { buttonModifier ->
    Button(onClick = onReload, enabled = !saving, modifier = buttonModifier) {
      Text(stringResource(R.string.reload))
    }
  }
  val historyButton: @Composable (Modifier) -> Unit = { buttonModifier ->
    TextButton(onClick = onHistory, enabled = !saving && historyEnabled, modifier = buttonModifier) {
      Text(stringResource(R.string.note_history))
    }
  }
  val saveButton: @Composable (Modifier) -> Unit = { buttonModifier ->
    Button(
      onClick = onSave,
      enabled = writable && dirty && !saving,
      modifier = buttonModifier,
    ) {
      Text(stringResource(if (saving) R.string.saving else R.string.save))
    }
  }

  if (vertical) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
      reloadButton(Modifier.fillMaxWidth())
      historyButton(Modifier.fillMaxWidth())
      saveButton(Modifier.fillMaxWidth())
    }
  } else {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      reloadButton(Modifier)
      historyButton(Modifier)
      saveButton(Modifier)
    }
  }
}

@Composable
internal fun NoteHistoryDialog(
  page: Int,
  projectId: String,
  currentDraft: String,
  baseRevision: String,
  onDismiss: () -> Unit,
  onRestore: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var entries by remember(projectId, page) { mutableStateOf<List<NoteVersion>>(emptyList()) }
  var loadError by remember(projectId, page) { mutableStateOf<String?>(null) }
  var selected by remember(projectId, page) { mutableStateOf<NoteVersion?>(null) }
  var preview by remember(projectId, page) { mutableStateOf("") }
  var confirming by remember { mutableStateOf(false) }
  var restoreError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(projectId, page) {
    loadError = null
    entries = withContext(Dispatchers.IO) {
      runCatching { NoteHistoryMirror.entries(context.filesDir, projectId, page) }.getOrElse {
        loadError = context.getString(R.string.note_history_failed, it.message ?: "")
        emptyList()
      }
    }
    selected = entries.firstOrNull()
  }
  LaunchedEffect(selected) {
    preview = selected?.let {
      withContext(Dispatchers.IO) {
        runCatching { NoteHistoryMirror.readVersion(context.filesDir, projectId, it.id) }
          .getOrElse { "" }
      }
    }.orEmpty()
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.note_history_title, page)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (loadError == null && entries.isEmpty()) {
          Text(stringResource(R.string.note_history_empty))
        }
        entries.forEach { entry ->
          val label = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.created)) +
            " · ${entry.bytes}B"
          TextButton(
            modifier = Modifier.testTag("note_history_entry_${entry.id}"),
            onClick = { selected = entry },
          ) {
            Text(
              label,
              color = if (entry.id == selected?.id) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurface,
            )
          }
        }
        if (selected != null) {
          Text(preview.ifBlank { stringResource(R.string.note_history_empty) })
        }
        restoreError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      }
    },
    confirmButton = {
      TextButton(
        modifier = Modifier.testTag("note_history_restore"),
        enabled = selected != null,
        onClick = { confirming = true },
      ) { Text(stringResource(R.string.note_history_restore)) }
    },
    dismissButton = {
      TextButton(modifier = Modifier.testTag("note_history_close"), onClick = onDismiss) {
        Text(stringResource(R.string.close))
      }
    },
  )
  if (confirming && selected != null) {
    AlertDialog(
      onDismissRequest = { confirming = false },
      title = { Text(stringResource(R.string.note_history_confirm_title)) },
      text = { Text(stringResource(R.string.note_history_confirm_detail)) },
      confirmButton = {
        TextButton(
          modifier = Modifier.testTag("note_history_confirm"),
          onClick = {
            val version = selected ?: return@TextButton
            scope.launch {
              try {
                val restored = withContext(Dispatchers.IO) {
                  NoteHistoryMirror.snapshotPage(
                    context.filesDir, projectId, page, currentDraft,
                  )
                  NoteHistoryMirror.restore(
                    context.filesDir, projectId, version.id, page, baseRevision, currentDraft,
                  )
                }
                onRestore(restored)
              } catch (_: IllegalStateException) {
                restoreError = context.getString(R.string.note_history_conflict)
                confirming = false
              } catch (error: Exception) {
                restoreError = context.getString(
                  R.string.note_history_failed, error.message ?: "",
                )
                confirming = false
              }
            }
          },
        ) { Text(stringResource(R.string.note_history_restore)) }
      },
      dismissButton = {
        TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
      },
    )
  }
}

@Composable
internal fun OcrResultDialog(
  page: Int,
  result: OcrResult,
  onDismiss: () -> Unit,
  onExplainRegion: (() -> Unit)? = null,
) {
  val needsReview = OcrResultPolicy.needsVisualReview(result)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.ocr_page_title, page)) },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(stringResource(R.string.ocr_status, result.status, result.engine))
        if (needsReview) {
          Text(stringResource(R.string.ocr_review), color = MaterialTheme.colorScheme.error)
        }
        result.warnings.forEach { warning ->
          Text(warning, color = MaterialTheme.colorScheme.error)
        }
        if (result.blocks.isEmpty()) {
          Text(stringResource(R.string.ocr_empty))
        }
        result.blocks.forEachIndexed { index, block ->
          Text("${index + 1}. ${block.text}")
          Text(
            stringResource(
              R.string.ocr_block_confidence,
              (block.confidence * 100).toInt(),
              block.x.toInt(),
              block.y.toInt(),
              block.width.toInt(),
              block.height.toInt(),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onExplainRegion != null && PageTutorRegionContext.isUsable(result)) {
          TextButton(
            modifier = Modifier.testTag("ocr_result_explain_region"),
            onClick = onExplainRegion,
          ) {
            Text(stringResource(R.string.page_tutor_explain_region))
          }
        }
        TextButton(modifier = Modifier.testTag("ocr_result_close"), onClick = onDismiss) {
          Text(stringResource(R.string.close))
        }
      }
    },
  )
}
