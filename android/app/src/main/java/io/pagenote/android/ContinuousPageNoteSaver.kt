package io.pagenote.android

import android.content.Context
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import io.pagenote.feature.notes.PageNoteEditResult
import io.pagenote.feature.notes.PageNoteEditor
import io.pagenote.feature.notes.PageNoteConflictException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Writes page-scoped continuous-reader drafts away from the scroll callback. */
internal class ContinuousPageNoteSaver(
  private val context: Context,
  private val readerSession: ReaderSessionViewModel,
  private val search: ReaderSearchController,
  private val updateNotes: (String) -> Unit,
  private val updateStatus: (String) -> Unit,
) {
  private val queuedPages = mutableSetOf<Int>()

  fun queue(project: OpenedProject, page: Int, draft: String, expectedRevision: String) {
    if (!readerSession.noteWritable.value ||
      draft == NotePage.pageAt(readerSession.notesMarkdown.value, page) ||
      !queuedPages.add(page)
    ) return
    val projectId = project.manifest.id
    readerSession.launchSessionTask {
      try {
        readerSession.noteSaveMutex.withLock {
          try {
            val saveRevision = readerSession.continuousNoteDraft(
              projectId, project.sourceDirectoryUri.toString(), page,
            )?.baseRevision ?: expectedRevision
            val (result, recoveryCopyName) = withContext(Dispatchers.IO) {
              val store = ProjectNoteStore(context.contentResolver, project.notesUri)
              val latest = store.read()
              if (NotePage.pageAt(latest, page) == draft) {
                return@withContext PageNoteEditResult(latest, PageNotes.revision(draft)) to null
              }
              val edited = PageNoteEditor.replacePage(
                latestMarkdown = latest,
                page = page,
                replacement = draft,
                expectedRevision = saveRevision,
              )
              val retainedCopy = store.write(edited.markdown, expectedOriginal = latest)
              edited to retainedCopy
            }
            val activeProject = readerSession.project.value
            val isStillCurrentProject = readerProjectIdentityMatches(
              expectedProjectId = projectId,
              expectedDirectoryUri = project.sourceDirectoryUri.toString(),
              currentProjectId = activeProject?.manifest?.id,
              currentDirectoryUri = activeProject?.sourceDirectoryUri?.toString(),
            )
            if (isStillCurrentProject) updateNotes(result.markdown)
            val savedPage = NotePage.pageAt(result.markdown, page)
            readerSession.completeContinuousPageSave(
              projectId = projectId,
              sourceDirectoryUri = project.sourceDirectoryUri.toString(),
              page = page,
              savedMarkdown = savedPage,
              baseRevision = result.pageRevision,
            )
            withContext(Dispatchers.IO) {
              runCatching {
                NoteHistoryMirror.snapshotPage(context.filesDir, projectId, page, savedPage)
              }
            }
            search.indexSavedNotePage(projectId, page, savedPage, result.pageRevision)
            if (recoveryCopyName != null) {
              updateStatusIfCurrent(project, projectId, context.getString(R.string.note_saved_recovery_copy, recoveryCopyName))
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (conflict: PageNoteConflictException) {
            updateStatusIfCurrent(project, projectId, conflict.message.orEmpty())
          } catch (conflict: NoteDocumentConflictException) {
            updateStatusIfCurrent(
              project = project,
              projectId = projectId,
              status = conflict.recoveryCopyName?.let { name ->
                context.getString(R.string.note_conflict_recovery_copy, name)
              } ?: context.getString(R.string.note_conflict),
            )
          } catch (error: Exception) {
            updateStatusIfCurrent(
              project,
              projectId,
              context.getString(R.string.note_save_failed, error.message ?: ""),
            )
          }
        }
      } finally {
        queuedPages.remove(page)
      }
    }
  }

  private fun updateStatusIfCurrent(project: OpenedProject, projectId: String, status: String) {
    val activeProject = readerSession.project.value
    if (readerProjectIdentityMatches(
        expectedProjectId = projectId,
        expectedDirectoryUri = project.sourceDirectoryUri.toString(),
        currentProjectId = activeProject?.manifest?.id,
        currentDirectoryUri = activeProject?.sourceDirectoryUri?.toString(),
      )
    ) updateStatus(status)
  }
}
