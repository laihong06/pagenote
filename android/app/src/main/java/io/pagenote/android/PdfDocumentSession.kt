package io.pagenote.android

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import io.pagenote.core.reader.PdfPagePolicy
import io.pagenote.core.reader.PdfTextContent
import io.pagenote.core.reader.PdfTextExtractionPolicy
import io.pagenote.core.reader.PdfTextExtractionResult
import io.pagenote.core.reader.PdfTextExtractionStatus
import io.pagenote.core.reader.PdfTextMapper
import io.pagenote.core.reader.PdfTextRect
import io.pagenote.core.reader.PdfPageText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

data class PdfRenderedPage(val bitmap: Bitmap, val scale: Double)

class PdfDocumentSession private constructor(
  private val descriptor: ParcelFileDescriptor,
  private val renderer: PdfRenderer,
  private val applicationContext: Context,
  val sourceRevision: String?,
  private val snapshotFile: File?,
) : Closeable {
  private var closed = false
  private val pdfBoxContentCache = object : LinkedHashMap<Int, PdfBoxPageContent?>(3, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PdfBoxPageContent?>?): Boolean =
      size > 3
  }

  val pageCount: Int get() = renderer.pageCount

  @Synchronized
  fun extractText(pageIndex: Int): PdfTextExtractionResult {
    check(pageIndex in 0 until renderer.pageCount) { "PDF page out of range" }
    val status = PdfTextExtractionPolicy.statusForApi(Build.VERSION.SDK_INT)
    if (status != PdfTextExtractionStatus.AVAILABLE) {
      return PdfTextExtractionResult.unavailableForOcr()
    }

    if (Build.VERSION.SDK_INT >= PdfTextExtractionPolicy.MIN_NATIVE_API) {
      val page = renderer.openPage(pageIndex)
      try {
        return extractTextApi35(page)
      } finally {
        page.close()
      }
    }
    val selectableText = extractPdfBoxPageContent(pageIndex)?.selectableText
      ?: return PdfTextExtractionResult.unavailableForOcr()
    return PdfTextMapper.extract(selectableText)
  }

  @Synchronized
  fun extractSelectableText(pageIndex: Int): PdfPageText? {
    check(pageIndex in 0 until renderer.pageCount) { "PDF page out of range" }
    return extractPdfBoxPageContent(pageIndex)?.selectableText
  }

  @Synchronized
  fun hasRasterImageContent(pageIndex: Int): Boolean {
    check(pageIndex in 0 until renderer.pageCount) { "PDF page out of range" }
    return extractPdfBoxPageContent(pageIndex)?.hasRasterImages == true
  }

  @Synchronized
  fun render(pageIndex: Int): Bitmap = renderWithMetadata(pageIndex).bitmap

  @Synchronized
  fun renderThumbnail(pageIndex: Int): Bitmap {
    check(!closed) { "PDF session closed" }
    require(pageIndex in 0 until renderer.pageCount) { "PDF page out of range" }
    val page = renderer.openPage(pageIndex)
    try {
      val scale = 360.0 / maxOf(page.width, page.height)
      val bitmap = Bitmap.createBitmap(
        (page.width * scale).toInt().coerceAtLeast(1),
        (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888,
      )
      try {
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
      } catch (error: Exception) {
        bitmap.recycle()
        throw error
      }
    } finally { page.close() }
  }

  @Synchronized
  fun renderWithMetadata(pageIndex: Int): PdfRenderedPage {
    check(pageIndex in 0 until renderer.pageCount) { "PDF page out of range" }
    val page = renderer.openPage(pageIndex)
    try {
      val scale = PdfPagePolicy.renderScale(page.width.toDouble(), page.height.toDouble())
      val (width, height) = PdfPagePolicy.renderedSize(page.width.toDouble(), page.height.toDouble())
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      try {
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return PdfRenderedPage(bitmap, scale)
      } catch (error: Exception) {
        bitmap.recycle()
        throw error
      }
    } finally {
      page.close()
    }
  }

  @Synchronized
  override fun close() {
    if (closed) return
    closed = true
    pdfBoxContentCache.clear()
    try {
      renderer.close()
    } finally {
      try {
        descriptor.close()
      } finally {
        snapshotFile?.delete()
      }
    }
  }

  private fun extractPdfBoxPageContent(pageIndex: Int): PdfBoxPageContent? {
    if (pdfBoxContentCache.containsKey(pageIndex)) return pdfBoxContentCache[pageIndex]
    val extracted = snapshotFile?.let { PdfBoxTextExtractor.inspect(applicationContext, it, pageIndex) }
    pdfBoxContentCache[pageIndex] = extracted
    return extracted
  }

  @RequiresApi(35)
  private fun extractTextApi35(page: PdfRenderer.Page): PdfTextExtractionResult {
    val contents = page.textContents.map { item ->
      PdfTextContent(
        text = item.text,
        bounds = item.bounds.map { rect ->
          PdfTextRect(
            left = rect.left.toDouble(),
            top = rect.top.toDouble(),
            right = rect.right.toDouble(),
            bottom = rect.bottom.toDouble(),
          )
        },
      )
    }
    return PdfTextMapper.extract(page.width.toDouble(), page.height.toDouble(), contents)
  }

  companion object {
    private const val TAG = "PdfDocumentSession"

    suspend fun open(context: Context, uri: Uri, cacheDir: File): PdfDocumentSession {
      val resolver: ContentResolver = context.contentResolver
      var snapshot: File? = null
      try {
        val (descriptor, revision, snapshotFile) = withContext(Dispatchers.IO) {
          val input = resolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取 PDF 文件")
          val created = File.createTempFile("pagenote-pdf-", ".pdf", cacheDir)
          snapshot = created
          val copiedRevision = input.use { copyPdfSourceSnapshot(it, created) }
          val createdDescriptor =
            ParcelFileDescriptor.open(created, ParcelFileDescriptor.MODE_READ_ONLY)
          Triple(createdDescriptor, copiedRevision, created)
        }
        return createSession(context.applicationContext, descriptor, revision, snapshotFile)
      } catch (error: Exception) {
        snapshot?.delete()
        Log.w(TAG, "PDF snapshot copy failed; using direct-SAF fallback", error)
        val descriptor = resolver.openFileDescriptor(uri, "r")
          ?: throw IllegalStateException("无法打开 PDF 文件")
        return createSession(context.applicationContext, descriptor, sourceRevision = null, snapshotFile = null)
      }
    }

    private fun createSession(
      applicationContext: Context,
      descriptor: ParcelFileDescriptor,
      sourceRevision: String?,
      snapshotFile: File?,
    ): PdfDocumentSession {
      try {
        val renderer = PdfRenderer(descriptor)
        if (renderer.pageCount < 1) {
          renderer.close()
          throw IllegalArgumentException("PDF 中没有页面")
        }
        return PdfDocumentSession(descriptor, renderer, applicationContext, sourceRevision, snapshotFile)
      } catch (error: Exception) {
        descriptor.close()
        snapshotFile?.delete()
        throw error
      }
    }
  }
}
