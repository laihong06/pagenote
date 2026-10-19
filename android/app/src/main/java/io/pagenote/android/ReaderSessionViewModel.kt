package io.pagenote.android

import android.content.Context
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenPageStore
import io.pagenote.feature.pen.PenStroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class ReaderSessionViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
  val project: MutableState<OpenedProject?> = mutableStateOf(null)
  val renderer: MutableState<PdfDocumentSession?> = mutableStateOf(null)
  val markdownSource: MutableState<String?> = mutableStateOf(null)
  val pageIndex: MutableIntState = mutableIntStateOf(0)
  val viewportBookmark: MutableState<PdfViewportBookmark> = mutableStateOf(PdfViewportBookmark.DEFAULT)
  val notesMarkdown: MutableState<String> = mutableStateOf("")
  val noteDraft: MutableState<String> = mutableStateOf("")
  val noteBaseRevision: MutableState<String> = mutableStateOf(PageNotes.revision(""))
  val noteWritable: MutableState<Boolean> = mutableStateOf(false)
  val noteStatus: MutableState<String?> = mutableStateOf(null)
  internal val pendingNoteExport: MutableState<NoteExportFile?> = mutableStateOf(null)
  internal val isRestoringPendingNoteExport: MutableState<Boolean> = mutableStateOf(false)
  internal val isHandlingNoteExportResult: MutableState<Boolean> = mutableStateOf(false)
  internal val isPendingNoteExportRecovered: MutableState<Boolean> = mutableStateOf(false)
  val penStore: MutableState<ProjectPenStore?> = mutableStateOf(null)
  val penPageStore: MutableState<PenPageStore> = mutableStateOf(PenPageStore())
  val penStrokes: MutableState<List<PenStroke>> = mutableStateOf(emptyList())
  val penWritable: MutableState<Boolean> = mutableStateOf(false)
  val penTool: MutableState<PenCanvasTool> = mutableStateOf(PenCanvasTool.PEN)
  val penStatus: MutableState<String?> = mutableStateOf(null)
  val failedPenSave: MutableState<Pair<Int, List<PenStroke>>?> = mutableStateOf(null)
  val pendingPenSaves: MutableIntState = mutableIntStateOf(0)
  val isOpening: MutableState<Boolean> = mutableStateOf(false)
  val isSaving: MutableState<Boolean> = mutableStateOf(false)
  val errorMessage: MutableState<String?> = mutableStateOf(null)

  val penSaveMutex = Mutex()

  private val sessionJob = SupervisorJob()
  private val sessionScope = CoroutineScope(sessionJob + Dispatchers.Main.immediate)
  private var applicationContext: Context? = null
  private var recoveryStore: NoteDraftRecoveryStore? = null
  private var noteExportSnapshotStore: NoteExportSnapshotStore? = null
  private var draftWriteJob: Job? = null
  private val recoveryMutex = Any()
  private val draftGeneration = AtomicLong()
  private var activeTarget: ProjectOpenTarget? = ReaderSessionCheckpoint.restoreFrom(savedStateHandle)
    ?.toProjectOpenTarget()
  private var restorationCheckpoint = ReaderSessionCheckpoint.restoreFrom(savedStateHandle)
  private var restoreRequestClaimed = false

  fun initializeRecovery(context: Context) {
    val appContext = context.applicationContext
    applicationContext = appContext
    recoveryStore = AtomicFileNoteDraftRecoveryStore(File(appContext.filesDir, "reader-draft-recovery"))
    noteExportSnapshotStore = NoteExportSnapshotStore(File(appContext.filesDir, "reader-note-export"))
    restorePendingNoteExport()
  }

  internal suspend fun storePendingNoteExport(file: NoteExportFile) {
    val store = checkNotNull(noteExportSnapshotStore) { "Note export storage is not initialized" }
    try {
      withContext(Dispatchers.IO) { store.write(file.content) }
      savedStateHandle[KEY_NOTE_EXPORT_MIME] = file.mimeType
      savedStateHandle[KEY_NOTE_EXPORT_FILE_NAME] = file.fileName
      pendingNoteExport.value = file
      isPendingNoteExportRecovered.value = false
    } catch (error: Throwable) {
      withContext(NonCancellable + Dispatchers.IO) { store.clear() }
      clearPendingNoteExportMetadata()
      pendingNoteExport.value = null
      throw error
    }
  }

  internal suspend fun clearPendingNoteExport() {
    withContext(NonCancellable + Dispatchers.IO) {
      runCatching { noteExportSnapshotStore?.clear() }
    }
    pendingNoteExport.value = null
    isPendingNoteExportRecovered.value = false
    clearPendingNoteExportMetadata()
  }

  private fun restorePendingNoteExport() {
    val mimeType = savedStateHandle.get<String>(KEY_NOTE_EXPORT_MIME)
    val fileName = savedStateHandle.get<String>(KEY_NOTE_EXPORT_FILE_NAME)
    if (mimeType == null || fileName == null) {
      pendingNoteExport.value = null
      isRestoringPendingNoteExport.value = false
      isPendingNoteExportRecovered.value = false
      clearPendingNoteExportMetadata()
      return
    }
    val store = noteExportSnapshotStore
    isPendingNoteExportRecovered.value = pendingNoteExport.value == null
    isRestoringPendingNoteExport.value = true
    sessionScope.launch {
      try {
        val content = withContext(Dispatchers.IO) { store?.read() }
        if (pendingNoteExport.value != null || !hasPendingNoteExportMetadata(mimeType, fileName)) return@launch
        if (content == null) {
          clearPendingNoteExportState()
          noteStatus.value = applicationContext?.getString(R.string.reader_menu_export_snapshot_missing)
          return@launch
        }
        pendingNoteExport.value = NoteExportFile(mimeType, fileName, content)
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        if (pendingNoteExport.value == null && hasPendingNoteExportMetadata(mimeType, fileName)) {
          noteStatus.value = applicationContext?.getString(R.string.reader_menu_export_restore_failed)
        }
      } finally {
        isRestoringPendingNoteExport.value = false
      }
    }
  }

  internal suspend fun readPendingNoteExport(): NoteExportFile? {
    val mimeType = savedStateHandle.get<String>(KEY_NOTE_EXPORT_MIME) ?: return null
    val fileName = savedStateHandle.get<String>(KEY_NOTE_EXPORT_FILE_NAME) ?: return null
    val store = noteExportSnapshotStore ?: return null
    val content = withContext(Dispatchers.IO) { store.read() } ?: return null
    return NoteExportFile(mimeType, fileName, content)
  }

  private fun clearPendingNoteExportState() {
    pendingNoteExport.value = null
    isPendingNoteExportRecovered.value = false
    clearPendingNoteExportMetadata()
  }

  private fun hasPendingNoteExportMetadata(mimeType: String, fileName: String): Boolean =
    savedStateHandle.get<String>(KEY_NOTE_EXPORT_MIME) == mimeType &&
      savedStateHandle.get<String>(KEY_NOTE_EXPORT_FILE_NAME) == fileName

  private fun clearPendingNoteExportMetadata() {
    savedStateHandle.remove<String>(KEY_NOTE_EXPORT_MIME)
    savedStateHandle.remove<String>(KEY_NOTE_EXPORT_FILE_NAME)
  }

  fun claimRestorationCheckpoint(): ReaderSessionCheckpoint? {
    if (restoreRequestClaimed) return null
    restoreRequestClaimed = true
    return restorationCheckpoint
  }

  fun checkpointFor(target: ProjectOpenTarget, projectId: String): ReaderSessionCheckpoint? =
    restorationCheckpoint?.takeIf {
      it.accessTreeUri == target.accessTreeUri.toString() &&
        it.rootDocumentId == target.rootDocumentId && it.projectId == projectId
    }

  fun checkpointFor(target: ProjectOpenTarget): ReaderSessionCheckpoint? =
    restorationCheckpoint?.takeIf {
      it.accessTreeUri == target.accessTreeUri.toString() && it.rootDocumentId == target.rootDocumentId
    }

  fun saveCheckpoint(): ReaderSessionCheckpoint? {
    val activeProject = project.value ?: return null
    val target = activeTarget ?: return null
    val projectId = activeProject.manifest.id
    val checkpoint = ReaderSessionCheckpoint(
      accessTreeUri = target.accessTreeUri.toString(),
      rootDocumentId = target.rootDocumentId,
      projectId = projectId,
      pageIndex = pageIndex.intValue.coerceAtLeast(0),
      viewportBookmark = viewportBookmark.value,
      penToolName = penTool.value.name,
    )
    checkpoint.saveTo(savedStateHandle)
    restorationCheckpoint = checkpoint
    return checkpoint
  }

  fun rememberOpenedProject(target: ProjectOpenTarget) {
    activeTarget = target
    saveCheckpoint()
  }

  fun readDraftRecovery(target: ProjectOpenTarget, projectId: String, page: Int): NoteDraftRecoveryRecord? =
    synchronized(recoveryMutex) {
      recoveryStore?.read(recoveryKey(target, projectId, page))
    }

  fun findDraftRecoveries(target: ProjectOpenTarget, projectId: String): List<NoteDraftRecoveryRecord> =
    synchronized(recoveryMutex) {
      recoveryStore?.findForProject(target.accessTreeUri.toString(), target.rootDocumentId, projectId).orEmpty()
    }

  fun clearDraftRecovery(target: ProjectOpenTarget, projectId: String, page: Int) {
    draftGeneration.incrementAndGet()
    draftWriteJob?.cancel()
    synchronized(recoveryMutex) {
      recoveryStore?.clear(recoveryKey(target, projectId, page))
    }
  }

  fun clearCurrentDraftRecovery(page: Int = pageIndex.intValue + 1) {
    val currentProject = project.value ?: return
    val target = activeTarget ?: return
    clearDraftRecovery(target, currentProject.manifest.id, page)
  }

  fun setNoteDraft(value: String) {
    noteDraft.value = value
    scheduleDraftRecoveryWrite()
  }

  fun refreshDraftRecoveryAfterSave(page: Int, savedPage: String) {
    if (pageIndex.intValue + 1 == page && noteDraft.value != savedPage) {
      scheduleDraftRecoveryWrite()
    } else {
      clearCurrentDraftRecovery(page)
      if (pageIndex.intValue + 1 != page) scheduleDraftRecoveryWrite()
    }
  }

  fun flushDraftRecovery() {
    val generation = draftGeneration.incrementAndGet()
    draftWriteJob?.cancel()
    draftWriteJob = null
    val project = project.value ?: return
    val target = activeTarget ?: return
    val currentPage = pageIndex.intValue + 1
    val savedPage = NotePage.pageAt(notesMarkdown.value, currentPage)
    val key = recoveryKey(target, project.manifest.id, currentPage)
    val store = recoveryStore ?: return
    if (noteDraft.value == savedPage) {
      try {
        synchronized(recoveryMutex) {
          if (generation == draftGeneration.get()) store.clear(key)
        }
      } catch (error: Exception) {
        showRecoveryWriteError(error)
      }
      return
    }
    try {
      synchronized(recoveryMutex) {
        if (generation == draftGeneration.get()) {
          store.write(
            NoteDraftRecoveryRecord(
              accessTreeUri = target.accessTreeUri.toString(),
              rootDocumentId = target.rootDocumentId,
              projectId = project.manifest.id,
              page = currentPage,
              draft = noteDraft.value,
              baseRevision = noteBaseRevision.value,
            ),
          )
        }
      }
    } catch (error: Exception) {
      showRecoveryWriteError(error)
    }
  }

  fun launchSessionTask(block: suspend CoroutineScope.() -> Unit): Job = sessionScope.launch(block = block)

  fun <T> asyncSessionTask(block: suspend CoroutineScope.() -> T): Deferred<T> =
    sessionScope.async(block = block)

  fun replaceRenderer(next: PdfDocumentSession?) {
    val previous = renderer.value
    if (previous === next) return
    renderer.value = next
    previous?.close()
  }

  private fun scheduleDraftRecoveryWrite() {
    val generation = draftGeneration.incrementAndGet()
    draftWriteJob?.cancel()
    if (project.value == null || activeTarget == null || recoveryStore == null) return
    draftWriteJob = sessionScope.launch {
      delay(DRAFT_WRITE_DEBOUNCE_MS)
      val currentProject = project.value ?: return@launch
      val target = activeTarget ?: return@launch
      val currentPage = pageIndex.intValue + 1
      val key = recoveryKey(target, currentProject.manifest.id, currentPage)
      val store = recoveryStore ?: return@launch
      val savedPage = NotePage.pageAt(notesMarkdown.value, currentPage)
      val draft = noteDraft.value
      val baseRevision = noteBaseRevision.value
      try {
        withContext(Dispatchers.IO) {
          synchronized(recoveryMutex) {
            if (generation != draftGeneration.get()) return@synchronized
            if (draft == savedPage) {
              store.clear(key)
            } else {
              store.write(
                NoteDraftRecoveryRecord(
                  accessTreeUri = target.accessTreeUri.toString(),
                  rootDocumentId = target.rootDocumentId,
                  projectId = currentProject.manifest.id,
                  page = currentPage,
                  draft = draft,
                  baseRevision = baseRevision,
                ),
              )
            }
          }
        }
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        showRecoveryWriteError(error)
      }
    }
  }

  private fun showRecoveryWriteError(error: Exception) {
    val context = applicationContext ?: return
    noteStatus.value = context.getString(R.string.note_recovery_write_failed, error.message.orEmpty())
  }

  private fun recoveryKey(target: ProjectOpenTarget, projectId: String, page: Int) =
    NoteDraftRecoveryKey(target.accessTreeUri.toString(), target.rootDocumentId, projectId, page)

  private companion object {
    const val DRAFT_WRITE_DEBOUNCE_MS = 250L
    const val KEY_NOTE_EXPORT_MIME = "reader.pending-note-export.mime"
    const val KEY_NOTE_EXPORT_FILE_NAME = "reader.pending-note-export.file-name"
  }

  override fun onCleared() {
    sessionScope.cancel()
    replaceRenderer(null)
    super.onCleared()
  }
}
