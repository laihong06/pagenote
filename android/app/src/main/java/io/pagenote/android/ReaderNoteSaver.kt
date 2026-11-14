package io.pagenote.android

import android.content.Context
import android.os.SystemClock
import io.pagenote.feature.notes.NotePage
import io.pagenote.feature.notes.PageNoteConflictException
import io.pagenote.feature.notes.PageNoteEditor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Writes snapshots serially and only reports success once the latest draft is persisted. */
internal class ReaderNoteSaver(
  private val context: Context,
  private val session: ReaderSessionViewModel,
  private val search: ReaderSearchController,
) {
  private val lastHistorySnapshot = mutableMapOf<Pair<String, Int>, Long>()

  /** Wait for any older write and recheck the latest draft before allowing navigation. */
  suspend fun flushBeforeNavigation(): Boolean {
    val targetProject = session.project.value ?: return false
    val targetPage = session.pageIndex.intValue + 1
    do {
      if (!saveCurrentNote()) return false
      if (session.project.value !== targetProject || session.pageIndex.intValue + 1 != targetPage) return false
    } while (session.noteDraft.value != NotePage.pageAt(session.notesMarkdown.value, targetPage))
    return true
  }

  suspend fun saveCurrentNote(): Boolean {
    val activeProject = session.project.value ?: return false
    val targetPage = session.pageIndex.intValue + 1
    fun isSameProject(): Boolean {
      val currentProject = session.project.value
      return readerProjectIdentityMatches(
        expectedProjectId = activeProject.manifest.id,
        expectedDirectoryUri = activeProject.sourceDirectoryUri.toString(),
        currentProjectId = currentProject?.manifest?.id,
        currentDirectoryUri = currentProject?.sourceDirectoryUri?.toString(),
      )
    }
    fun updateStatus(status: String?) { if (isSameProject()) session.noteStatus.value = status }
    fun isSamePage() = isSameProject() && session.pageIndex.intValue + 1 == targetPage
    return session.asyncSessionTask {
      val saved = session.noteSaveCoordinator.flush(
        hasPending = {
          isSamePage() && session.noteDraft.value != NotePage.pageAt(session.notesMarkdown.value, targetPage)
        },
        writePending = {
          if (!session.noteWritable.value) {
            updateStatus(context.getString(R.string.note_read_only))
            false
          } else {
            val draftToSave = session.noteDraft.value
            val expectedRevision = session.noteBaseRevision.value
            session.isNoteSaving.value = true
            session.isSaving.value = true
            session.noteStatus.value = null
            try {
              val (result, recoveryCopyName) = withContext(Dispatchers.IO) {
                val store = ProjectNoteStore(context.contentResolver, activeProject.notesUri)
                val latest = store.read()
                val edited = PageNoteEditor.replacePage(
                  latestMarkdown = latest, page = targetPage,
                  replacement = draftToSave, expectedRevision = expectedRevision,
                )
                edited to store.write(edited.markdown, expectedOriginal = latest)
              }
              if (!isSameProject()) {
                false
              } else {
                session.notesMarkdown.value = result.markdown
                session.completeContinuousPageSave(
                  projectId = activeProject.manifest.id,
                  sourceDirectoryUri = activeProject.sourceDirectoryUri.toString(),
                  page = targetPage,
                  savedMarkdown = NotePage.pageAt(result.markdown, targetPage),
                  baseRevision = result.pageRevision,
                )
                session.refreshDraftRecoveryAfterSave(targetPage, NotePage.pageAt(result.markdown, targetPage))
                session.noteStatus.value = recoveryCopyName?.let { context.getString(R.string.note_saved_recovery_copy, it) }
                val historyKey = activeProject.manifest.id to targetPage
                val now = SystemClock.elapsedRealtime()
                val previousSnapshot = lastHistorySnapshot[historyKey]
                if (previousSnapshot == null || now - previousSnapshot >= 60_000L) {
                  lastHistorySnapshot[historyKey] = now
                  val historyText = NotePage.pageAt(result.markdown, targetPage)
                  session.launchSessionTask {
                    val captured = withContext(Dispatchers.IO) {
                      runCatching {
                        NoteHistoryMirror.snapshotPage(
                          context.filesDir, activeProject.manifest.id, targetPage, historyText,
                        )
                      }.isSuccess
                    }
                    if (!captured && lastHistorySnapshot[historyKey] == now) lastHistorySnapshot.remove(historyKey)
                  }
                }
                search.indexSavedNotePage(activeProject.manifest.id, targetPage, draftToSave, result.pageRevision)
                true
              }
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (conflict: PageNoteConflictException) {
              updateStatus(conflict.message)
              false
            } catch (conflict: NoteDocumentConflictException) {
              updateStatus(conflict.recoveryCopyName?.let { context.getString(R.string.note_conflict_recovery_copy, it) }
                ?: context.getString(R.string.note_conflict))
              false
            } catch (recovery: NoteRecoveryCopyRetainedException) {
              updateStatus(context.getString(R.string.note_recovery_copy_retained, recovery.recoveryCopyName))
              false
            } catch (recovery: NoteWriteRecoveryException) {
              updateStatus(recovery.recoveryCopyName?.let {
                context.getString(if (recovery.rollbackSucceeded) R.string.note_write_recovery_rollback else R.string.note_write_recovery_failed, it)
              } ?: context.getString(R.string.note_save_failed, recovery.message ?: ""))
              false
            } catch (error: Exception) {
              updateStatus(context.getString(R.string.note_save_failed, error.message ?: ""))
              false
            } finally {
              session.isSaving.value = false
              session.isNoteSaving.value = false
            }
          }
        },
      )
      saved && isSamePage()
    }.await()
  }
}
