package io.pagenote.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.pagenote.android.search.ProjectSearchIndexer
import io.pagenote.android.search.SearchIndexRepository
import io.pagenote.core.project.PageNotes
import io.pagenote.core.search.SearchDoc
import io.pagenote.core.search.SearchDocumentKind
import io.pagenote.core.search.SearchHit
import io.pagenote.core.search.SearchJumpPolicy
import io.pagenote.core.search.SearchRect
import io.pagenote.feature.ocr.OcrResult
import io.pagenote.feature.ocr.OcrResultPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 批次 7 全文检索的读者会话控制器：索引线程（[indexer]）、搜索状态、
 * 结果跳转与重建。逻辑从 ReaderHome 抽出以控制组合函数字节码体积。
 */
class ReaderSearchController(
  private val context: Context,
  private val session: ReaderSessionViewModel,
  val repository: SearchIndexRepository,
) {
  val indexer = ProjectSearchIndexer(repository)

  var show by mutableStateOf(false)
  var query by mutableStateOf("")
  var results by mutableStateOf<List<SearchHit>>(emptyList())
  var searching by mutableStateOf(false)
  var error by mutableStateOf<String?>(null)
  var focusPage by mutableIntStateOf(-1)
  var focusRect by mutableStateOf<NormalizedOcrRegion?>(null)
  var rebuilding by mutableStateOf(false)

  /** 笔记整本文档入索引（打开/导入/代理整本写入后）。 */
  fun indexNotePages(projectId: String, markdown: String) {
    NotePageIndexer.noteDocs(projectId, markdown).forEach(indexer::submit)
  }

  /** Markdown 原文整本入索引（按页分隔语义）。 */
  fun indexSourcePages(projectId: String, source: String) {
    NotePageIndexer.sourceDocs(projectId, source).forEach(indexer::submit)
  }

  /** 保存后的单页笔记：空白页清除索引，其余按 revision 增量更新。 */
  fun indexSavedNotePage(projectId: String, page: Int, content: String, revision: String) {
    if (content.isBlank()) {
      indexer.removePage(projectId, page, setOf(SearchDocumentKind.NOTE))
    } else {
      indexer.submit(SearchDoc(projectId, page, SearchDocumentKind.NOTE, content, revision))
    }
  }

  /** PDF 源页文本入索引（浏览/提取到可选文本时机会式补齐）。 */
  fun indexSourcePageText(projectId: String, page: Int, text: String) {
    if (text.isBlank()) return
    indexer.submit(
      SearchDoc(projectId, page, SearchDocumentKind.SOURCE, text, PageNotes.revision(text)),
    )
  }

  /** OCR 结果入索引（整页 OCR 无 rect；区域 OCR 带归一化定位矩形）。 */
  fun indexOcrResult(
    projectId: String,
    page: Int,
    result: OcrResult,
    region: NormalizedOcrRegion? = null,
  ) {
    if (result.status !in OCR_INDEXABLE_STATUSES) return
    val rect = region?.let { SearchRect(it.left, it.top, it.right, it.bottom) }
    NotePageIndexer.ocrDoc(projectId, page, OcrResultPolicy.textOf(result), rect)
      ?.let(indexer::submit)
  }

  /** 手写转写 / 公式 LaTeX 落盘内容入索引（带选区定位矩形）。 */
  fun indexTranscript(projectId: String, page: Int, text: String, region: NormalizedOcrRegion) {
    val rect = SearchRect(region.left, region.top, region.right, region.bottom)
    NotePageIndexer.transcriptDoc(projectId, page, text.trim(), rect)?.let(indexer::submit)
  }

  /** 执行当前查询；结果写入 [results]，失败写入 [error] 并返回空列表。 */
  suspend fun search(projectId: String): List<SearchHit> {
    searching = true
    error = null
    try {
      val hits = withContext(Dispatchers.IO) {
        repository.search(projectId = projectId, query = query, kinds = indexer.settings.kinds())
      }
      results = hits
      return hits
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      results = emptyList()
      this.error = error.message?.takeIf(String::isNotBlank)
        ?: context.getString(R.string.search_failed_generic)
      return emptyList()
    } finally {
      searching = false
    }
  }

  /**
   * 结果跳转：页码合法性经 [SearchJumpPolicy]，PDF 视口把命中矩形居中并
   * 短暂高亮；Markdown 项目只做页码跳转。
   */
  suspend fun jump(
    hit: SearchHit,
    pageCount: Int,
    currentScale: Float,
    changePage: suspend (Int) -> Boolean,
  ) {
    val target = SearchJumpPolicy.pageIndex(hit.page, pageCount) ?: run {
      session.noteStatus.value = context.getString(R.string.search_page_out_of_range)
      return
    }
    show = false
    val moved = changePage(target)
    if (!moved) return
    hit.rect?.let { rect ->
      if (session.renderer.value != null) {
        focusPage = target
        focusRect = NormalizedOcrRegion(rect.left, rect.top, rect.right, rect.bottom)
        val (centerX, centerY) = SearchJumpPolicy.focusCenter(rect)
        session.viewportBookmark.value = PdfViewportBookmark(
          scale = currentScale,
          centerX = centerX,
          centerY = centerY,
        )
      }
    }
  }

  /**
   * 设置 → 重建索引：笔记/源页从磁盘重读刷新（revision 幂等）；
   * OCR/转写无法从磁盘重读，范围内保留、范围外清除。返回状态文案。
   */
  suspend fun rebuild(
    project: OpenedProject,
    markdownSource: String?,
    renderer: PdfDocumentSession?,
  ): String {
    rebuilding = true
    try {
      val count = withContext(Dispatchers.IO) {
        val projectId = project.manifest.id
        val scopeKinds = indexer.settings.kinds()
        if (SearchDocumentKind.NOTE in scopeKinds) {
          runCatching { ProjectNoteStore(context.contentResolver, project.notesUri).read() }
            .getOrNull()
            ?.let { noteText -> NotePageIndexer.noteDocs(projectId, noteText) }
            ?.forEach { repository.put(it) }
        }
        if (SearchDocumentKind.SOURCE in scopeKinds) {
          markdownSource?.let { source ->
            NotePageIndexer.sourceDocs(projectId, source).forEach { repository.put(it) }
          }
          renderer?.let { pdf ->
            for (page in 0 until pdf.pageCount) {
              runCatching { pdf.extractSelectableText(page) }
                .getOrNull()
                ?.takeIf { it.text.isNotBlank() }
                ?.let { pageText ->
                  repository.put(
                    SearchDoc(
                      projectId, page + 1, SearchDocumentKind.SOURCE,
                      pageText.text, PageNotes.revision(pageText.text),
                    ),
                  )
                }
            }
          }
        }
        repository.removeKinds(projectId, SearchDocumentKind.ALL - scopeKinds)
        repository.countForProject(projectId)
      }
      return context.getString(R.string.search_index_stats, count)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      throw IllegalStateException(
        context.getString(R.string.search_index_failed, error.message ?: ""),
        error,
      )
    } finally {
      rebuilding = false
    }
  }
}
