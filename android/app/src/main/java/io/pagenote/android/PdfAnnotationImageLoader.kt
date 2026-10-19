package io.pagenote.android

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

internal class PdfAnnotationImageLoader(
  private val resolver: ContentResolver,
  private val sourceDirectoryUri: Uri,
) {
  fun hasAsset(assetName: String): Boolean {
    if (PdfAnnotationImageReference.assetName("/attachments/$assetName") != assetName) return false
    return findChild(findChild(sourceDirectoryUri, "assets") ?: return false, assetName) != null
  }

  fun load(assetName: String): Bitmap? {
    if (PdfAnnotationImageReference.assetName("/attachments/$assetName") != assetName) return null
    return runCatching { decodeAsset(assetName) }.getOrNull()
  }

  private fun decodeAsset(assetName: String): Bitmap? {
    val assetsDirectory = findChild(sourceDirectoryUri, "assets") ?: return null
    val assetUri = findChild(assetsDirectory, assetName) ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = resolver.openInputStream(assetUri) ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
    var sampleSize = 1
    while (longestEdge / sampleSize > MAX_BITMAP_EDGE) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = resolver.openInputStream(assetUri)?.use {
      BitmapFactory.decodeStream(it, null, options)
    } ?: return null

    val decodedLongestEdge = maxOf(decoded.width, decoded.height)
    if (decodedLongestEdge <= MAX_BITMAP_EDGE) return decoded
    val scale = MAX_BITMAP_EDGE.toFloat() / decodedLongestEdge
    val scaled = Bitmap.createScaledBitmap(
      decoded,
      (decoded.width * scale).toInt().coerceAtLeast(1),
      (decoded.height * scale).toInt().coerceAtLeast(1),
      true,
    )
    if (scaled !== decoded) decoded.recycle()
    return scaled
  }

  private fun findChild(parentUri: Uri, name: String): Uri? {
    val parentId = DocumentsContract.getDocumentId(parentUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentId)
    val cursor = resolver.query(
      childrenUri,
      arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
      null,
      null,
      null,
    ) ?: throw IOException("The project assets could not be listed")
    cursor.use {
      val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (it.moveToNext()) {
        if (it.getString(nameColumn) == name) {
          return DocumentsContract.buildDocumentUriUsingTree(parentUri, it.getString(idColumn))
        }
      }
    }
    return null
  }

  internal companion object {
    const val MAX_BITMAP_EDGE = 2048
  }
}
