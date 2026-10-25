package io.pagenote.android

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 合并核心的结构契约测试。
 *
 * 标准十四字体（PDType1Font）在 JVM 单测环境缺少 pdfbox-android 的 AFM 资源
 * （Android 上由 PDFBoxResourceLoader 注入），因此用 MediaBox 尺寸标识页面，
 * 不渲染文本；文本/笔记页渲染路径由 Android 仪器测试覆盖。
 */
class PdfNotesMergeExporterTest {
  private fun sourcePdf(pageCount: Int): ByteArray {
    val document = PDDocument()
    try {
      repeat(pageCount) { index ->
        // 每页宽度唯一（200+index），便于断言合并顺序。
        document.addPage(PDPage(PDRectangle(200f + index, 792f)))
      }
      val output = ByteArrayOutputStream()
      document.save(output)
      return output.toByteArray()
    } finally {
      document.close()
    }
  }

  private fun widthsOf(pdf: ByteArray): List<Float> =
    PDDocument.load(ByteArrayInputStream(pdf)).use { document ->
      (0 until document.numberOfPages).map { page -> document.getPage(page).mediaBox.width }
    }

  @Test
  fun `selected pages are merged in the given order without notes`() {
    val output = ByteArrayOutputStream()
    PdfNotesMergeExporter.merge(
      source = ByteArrayInputStream(sourcePdf(3)),
      pages = listOf(3, 1),
      notePdfBytesByPage = emptyMap(),
      output = output,
    )
    assertEquals(listOf(202f, 200f), widthsOf(output.toByteArray()))
  }

  @Test
  fun `note pages are appended right after their source page`() {
    val noteDocument = PDDocument()
    val notePdf = try {
      noteDocument.addPage(PDPage(PDRectangle(300f, 792f)))
      val output = ByteArrayOutputStream()
      noteDocument.save(output)
      output.toByteArray()
    } finally {
      noteDocument.close()
    }
    val output = ByteArrayOutputStream()
    PdfNotesMergeExporter.merge(
      source = ByteArrayInputStream(sourcePdf(2)),
      pages = listOf(1, 2),
      notePdfBytesByPage = mapOf(2 to notePdf),
      output = output,
    )
    // 源页 1、源页 2（宽 201）、随后是第 2 页的笔记页（宽 300）。
    assertEquals(listOf(200f, 201f, 300f), widthsOf(output.toByteArray()))
  }

  @Test
  fun `empty selection note map leaves only source pages`() {
    val output = ByteArrayOutputStream()
    PdfNotesMergeExporter.merge(
      source = ByteArrayInputStream(sourcePdf(2)),
      pages = listOf(2),
      notePdfBytesByPage = mapOf(2 to ByteArray(0)),
      output = output,
    )
    assertEquals(listOf(201f), widthsOf(output.toByteArray()))
  }

  @Test
  fun `out of range pages are rejected instead of silently dropped`() {
    val output = ByteArrayOutputStream()
    val error = runCatching {
      PdfNotesMergeExporter.merge(
        source = ByteArrayInputStream(sourcePdf(1)),
        pages = listOf(2),
        notePdfBytesByPage = emptyMap(),
        output = output,
      )
    }.exceptionOrNull()
    assertTrue("expected IllegalStateException, got $error", error is IllegalStateException)
  }
}
