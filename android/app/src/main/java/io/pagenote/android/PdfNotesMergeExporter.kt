package io.pagenote.android

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * 合并导出核心（PDFBox）：把所选源页按给定顺序拼入新文档，
 * 并在每页之后插入该页的"笔记页 PDF"（convert note 的产物，可为空）。
 *
 * 纯 JVM 可测：笔记页以现成 PDF 字节传入。源文档在保存前保持打开以共享
 * 页资源；[source] 只被消费一次，重读由调用方通过流回调控制。
 */
internal object PdfNotesMergeExporter {

  fun merge(
    source: InputStream,
    pages: List<Int>,
    notePdfBytesByPage: Map<Int, ByteArray>,
    output: OutputStream,
  ) {
    // importPage 与源文档共享页资源：所有参与文档保持打开，保存完成后再统一关闭。
    val noteDocuments = notePdfBytesByPage.filterValues { it.isNotEmpty() }
      .mapValues { (_, bytes) -> PDDocument.load(bytes) }
    try {
      PDDocument.load(source).use { src ->
        PDDocument().use { out ->
          pages.forEach { page ->
            check(page in 1..src.numberOfPages) { "PDF page out of range: $page" }
            out.importPage(src.getPage(page - 1))
            noteDocuments[page]?.let { note ->
              for (index in 0 until note.numberOfPages) {
                out.importPage(note.getPage(index))
              }
            }
          }
          out.save(output)
        }
      }
    } finally {
      noteDocuments.values.forEach { document -> runCatching { document.close() } }
    }
  }

  fun loadPageSize(openSource: () -> InputStream, page: Int): PDRectangle {
    openSource().use { source ->
      PDDocument.load(source).use { document ->
        check(page in 1..document.numberOfPages) { "PDF page out of range: $page" }
        return document.getPage(page - 1).mediaBox
      }
    }
  }
}

/**
 * convert note 的 Android 侧渲染：把一页笔记纯文本排成若干 PDF 页
 * （android.graphics.pdf.PdfDocument + 系统字体，支持 CJK）。
 * 返回的字节流交回 [PdfNotesMergeExporter.merge] 拼接。
 */
internal object NotePagePdfRenderer {
  const val MARGIN_POINTS = 36f
  const val TEXT_SIZE_POINTS = 11f
  const val LINE_SPACING = 1.45f

  fun renderNotePages(
    plainText: String,
    pageWidthPoints: Float,
    pageHeightPoints: Float,
  ): List<ByteArray> {
    require(pageWidthPoints > 0f && pageHeightPoints > 0f) { "note page size must be positive" }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textSize = TEXT_SIZE_POINTS
      color = Color.BLACK
      isSubpixelText = true
    }
    val lineHeight = TEXT_SIZE_POINTS * LINE_SPACING
    val contentWidth = pageWidthPoints - MARGIN_POINTS * 2
    val maxLinesPerPage =
      (((pageHeightPoints - MARGIN_POINTS * 2) / lineHeight).toInt()).coerceAtLeast(1)

    val wrapped = wrapLines(plainText, paint, contentWidth)
    if (wrapped.isEmpty()) return emptyList()
    return wrapped.chunked(maxLinesPerPage).mapIndexed { pageIndex, pageLines ->
      val document = PdfDocument()
      try {
        val pageInfo = PdfDocument.PageInfo.Builder(
          pageWidthPoints.toInt().coerceAtLeast(1),
          pageHeightPoints.toInt().coerceAtLeast(1),
          pageIndex + 1,
        ).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        pageLines.forEachIndexed { index, line ->
          canvas.drawText(
            line,
            MARGIN_POINTS,
            MARGIN_POINTS + TEXT_SIZE_POINTS + index * lineHeight,
            paint,
          )
        }
        document.finishPage(page)
        val output = ByteArrayOutputStream()
        document.writeTo(output)
        output.toByteArray()
      } finally {
        document.close()
      }
    }
  }

  /** 逐字测量断行；超长无空格文本（CJK）也能折行。 */
  internal fun wrapLines(text: String, paint: Paint, maxWidth: Float): List<String> {
    if (text.isBlank()) return emptyList()
    val lines = mutableListOf<String>()
    text.split('\n').forEach { rawLine ->
      if (rawLine.isBlank()) {
        lines += ""
        return@forEach
      }
      val current = StringBuilder()
      rawLine.forEach { char ->
        val candidate = current.toString() + char
        if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
          lines += current.toString()
          current.setLength(0)
        }
        current.append(char)
      }
      if (current.isNotEmpty()) lines += current.toString()
    }
    return lines
  }
}

/**
 * 合并导出的编排入口（Android 运行时）：解析好的页码 + 可选笔记页渲染 + 拼接输出。
 * [openSource] 可能被多次调用（读取页面尺寸与合并各一次）；SAF 读写由 MainActivity 处理。
 */
internal class PdfMergeExportService {
  data class Request(
    val pages: List<Int>,
    val includeNotes: Boolean,
    val notesByPage: Map<Int, String>,
  )

  fun export(openSource: () -> InputStream, request: Request, output: OutputStream) {
    val noteBytes = mutableMapOf<Int, ByteArray>()
    if (request.includeNotes) {
      request.pages.forEach { page ->
        val plain = io.pagenote.feature.notes.NotePlainText.plainify(
          request.notesByPage[page].orEmpty(),
        )
        if (plain.isEmpty()) return@forEach
        val size = PdfNotesMergeExporter.loadPageSize(openSource, page)
        NotePagePdfRenderer.renderNotePages(
          plainText = plain,
          pageWidthPoints = size.width,
          pageHeightPoints = size.height,
        ).takeIf(List<ByteArray>::isNotEmpty)
          ?.let { rendered -> noteBytes[page] = combineNotePages(rendered) }
      }
    }
    PdfNotesMergeExporter.merge(openSource(), request.pages, noteBytes, output)
  }

  /** 单页笔记可能展开成多个 PDF 页；拼接为一个纯笔记文档后整体插入。 */
  private fun combineNotePages(rendered: List<ByteArray>): ByteArray {
    if (rendered.size == 1) return rendered.single()
    val noteDocuments = rendered.map { bytes -> PDDocument.load(bytes) }
    try {
      val output = ByteArrayOutputStream()
      PDDocument().use { combined ->
        noteDocuments.forEach { doc ->
          for (index in 0 until doc.numberOfPages) {
            combined.importPage(doc.getPage(index))
          }
        }
        combined.save(output)
      }
      return output.toByteArray()
    } finally {
      noteDocuments.forEach { document -> runCatching { document.close() } }
    }
  }
}
