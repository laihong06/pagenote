package io.pagenote.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

internal class BookCoverLoader(private val context: Context) {
  suspend fun load(book: BookshelfBook): Bitmap = permits.withPermit {
    withContext(Dispatchers.IO) {
      val type = book.appearance.resolvedCover(book.project.sourceExtension)
      require(type != "paper")
      val access = SafPreparedDocumentAccess(context.contentResolver)
      val uri = if (type == "image") {
        val assets = access.findChild(book.project.sourceDirectoryUri.toString(), "assets") ?: throw IOException("Missing assets")
        Uri.parse(access.findChild(assets, checkNotNull(book.appearance.asset)) ?: throw IOException("Missing cover image"))
      } else book.project.sourceUri
      val stamp = context.contentResolver.query(uri, arrayOf(
        android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        android.provider.DocumentsContract.Document.COLUMN_SIZE,
      ), null, null, null)?.use {
        if (it.moveToFirst()) "${it.getLong(it.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED))}:${it.getLong(it.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_SIZE))}" else ""
      }.orEmpty()
      val key = MessageDigest.getInstance("SHA-256").digest(
        "v1|${book.key}|${book.revision}|$stamp|${book.appearance.encode()}".toByteArray(),
      ).joinToString("") { "%02x".format(it) }
      val directory = File(context.cacheDir, "bookshelf-covers").apply { mkdirs() }
      val file = File(directory, "$key.png")
      if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { return@withContext it }
      currentCoroutineContext().ensureActive()
      val bitmap = if (type == "pdf") withBookshelfPdf(context, uri) { pdf ->
        val index = if (book.appearance.cover == "default") 0 else book.appearance.page
        require(index < pdf.pageCount) { "Cover page no longer exists" }
        pdf.openPage(index).use { page ->
          val scale = minOf(400f / page.width.coerceAtLeast(1), 1200f / page.height.coerceAtLeast(1))
          Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
            it.eraseColor(Color.WHITE)
            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
          }
        }
      } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unreadable cover image" }
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (bounds.outWidth / options.inSampleSize > 1200 || bounds.outHeight / options.inSampleSize > 1200) options.inSampleSize *= 2
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, options) }
          ?: throw IOException("Cannot decode cover image")
      }
      currentCoroutineContext().ensureActive()
      // Bound both disk usage and simultaneous native PDF/image allocations.
      val temp = File.createTempFile("cover-", ".tmp", directory)
      try {
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        temp.renameTo(file)
      } finally { temp.delete() }
      val files = directory.listFiles().orEmpty().filter { it.extension == "png" }.sortedByDescending { it.lastModified() }
      var total = 0L
      files.forEach { total += it.length(); if (total > 48L * 1024 * 1024) it.delete() }
      bitmap
    }
  }

  companion object { private val permits = Semaphore(2) }
}

internal fun <T> withBookshelfPdf(context: Context, uri: Uri, block: (PdfRenderer) -> T): T {
  val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("Cannot open PDF")
  val renderer = try { PdfRenderer(descriptor) } catch (error: Exception) {
    descriptor.close()
    // Some SAF providers expose pipes rather than seekable file descriptors.
    val stage = File.createTempFile("shelf-pdf-", ".pdf", context.cacheDir)
    try {
      context.contentResolver.openInputStream(uri)!!.use { input ->
        stage.outputStream().use { output ->
          val buffer = ByteArray(64 * 1024)
          var total = 0L
          while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= 512L * 1024 * 1024) { "PDF exceeds cover staging limit" }
            output.write(buffer, 0, count)
          }
        }
      }
      val stagedDescriptor = ParcelFileDescriptor.open(stage, ParcelFileDescriptor.MODE_READ_ONLY)
      try { return PdfRenderer(stagedDescriptor).use(block) }
      catch (stagedError: Exception) { stagedDescriptor.close(); throw stagedError }
    } finally { stage.delete() }
  }
  return renderer.use(block)
}
