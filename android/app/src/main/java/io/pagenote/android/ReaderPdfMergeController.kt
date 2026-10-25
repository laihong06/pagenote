package io.pagenote.android

import android.content.Context
import android.net.Uri
import io.pagenote.feature.notes.NotePage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 合并导出 PDF + convert note 的阅读器动作（移植差距清单 #15 的导出增强）。
 *
 * 从 ReaderHome 抽出以控制组合方法大小。SAF 读写、页码解析与 PDFBox 拼接的
 * 纯逻辑分别在 [PdfMergeExportService]/[PdfPageMergePlan] 中可测。
 */
internal class ReaderPdfMergeController(
  private val context: Context,
  private val scope: CoroutineScope,
  private val host: Host,
) {
  interface Host {
    fun currentProject(): OpenedProject?
    fun rendererPageCount(): Int?
    fun noteWritable(): Boolean
    fun hasUnsavedChanges(): Boolean

    /** 按 revision 流程保存脏页；返回 false 表示保存失败或冲突，应中止导出。 */
    suspend fun saveDirtyNote(): Boolean
    fun notesMarkdown(): String
    fun includeNotes(): Boolean
    fun setStatus(status: String?)
    fun setExporting(value: Boolean)
    fun pendingTempFile(): File?
    fun setPendingTempFile(file: File?)
    fun launchPicker(suggestedName: String)
  }

  fun suggestedName(activeProject: OpenedProject): String =
    "${activeProject.name}-合并页.pdf".replace(Regex("[\\\\/:*?\"<>|]"), "_")

  fun finish(uri: Uri?) {
    val temporary = host.pendingTempFile()
    if (uri == null || temporary == null) {
      temporary?.delete()
      host.setPendingTempFile(null)
      return
    }
    host.setExporting(true)
    scope.launch {
      try {
        val projectAtStart = host.currentProject()
        val wrote = withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            temporary.inputStream().use { input -> input.copyTo(output) }
            true
          } ?: false
        }
        temporary.delete()
        host.setPendingTempFile(null)
        if (!wrote || projectAtStart == null) {
          host.setStatus(
            context.getString(
              R.string.pdf_merge_failed,
              context.getString(R.string.pdf_merge_write_unavailable),
            ),
          )
          return@launch
        }
        host.setStatus(context.getString(R.string.pdf_merge_success))
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        host.setStatus(
          context.getString(
            R.string.pdf_merge_failed,
            error.message ?: error.javaClass.simpleName,
          ),
        )
      } finally {
        host.setExporting(false)
      }
    }
  }

  fun prepare(rangeInput: String) {
    val activeProject = host.currentProject() ?: return
    val pageCount = host.rendererPageCount() ?: return
    if (host.pendingTempFile() != null) return
    val pages = try {
      PdfPageMergePlan.parse(rangeInput, pageCount)
    } catch (error: IllegalArgumentException) {
      host.setStatus(context.getString(R.string.pdf_merge_invalid_range))
      return
    }
    host.setExporting(true)
    scope.launch {
      var temporary: File? = null
      try {
        // 快照语义：先按 revision 流程保存脏页，再取笔记快照。
        if (host.noteWritable() && host.hasUnsavedChanges() && !host.saveDirtyNote()) return@launch
        val notesSnapshot = host.notesMarkdown()
        val notesByPage = pages.associateWith { page ->
          NotePage.pageAt(notesSnapshot, page)
        }
        temporary = withContext(Dispatchers.IO) {
          File.createTempFile("pagenote-merge-", ".pdf", context.cacheDir)
        }
        host.setPendingTempFile(temporary)
        withContext(Dispatchers.IO) {
          PdfMergeExportService().export(
            openSource = {
              context.contentResolver.openInputStream(activeProject.sourceUri)
                ?: throw IllegalStateException("无法读取 PDF 源文件")
            },
            request = PdfMergeExportService.Request(
              pages = pages,
              includeNotes = host.includeNotes(),
              notesByPage = notesByPage,
            ),
            output = temporary.outputStream(),
          )
        }
        host.setStatus(null)
        host.launchPicker(suggestedName(activeProject))
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        temporary?.delete()
        host.setPendingTempFile(null)
        host.setStatus(
          context.getString(
            R.string.pdf_merge_failed,
            error.message ?: error.javaClass.simpleName,
          ),
        )
      } finally {
        host.setExporting(false)
      }
    }
  }
}
