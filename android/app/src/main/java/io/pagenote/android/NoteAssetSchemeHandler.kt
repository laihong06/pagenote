package io.pagenote.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.noties.markwon.image.ImageItem
import io.noties.markwon.image.SchemeHandler

private const val MAX_IMAGE_SOURCE_LABEL_LENGTH = 192
private val PROJECT_IMAGE_NAME_PATTERN = Regex("[a-f0-9-]+\\.[a-z0-9]+", RegexOption.IGNORE_CASE)

internal fun noteImageSourceSummary(raw: String): String {
  val uri = Uri.parse(raw)
  return when (uri.scheme?.lowercase()) {
    "http", "https" -> buildString {
      append(uri.host ?: "remote image")
      uri.encodedPath?.takeIf(String::isNotBlank)?.let { append(it.take(128)) }
    }
    "data" -> raw.substringAfter(':').substringBefore(',').substringBefore(';')
      .takeIf(String::isNotBlank) ?: "data image"
    NotePreviewRewrite.DEFAULT_ASSET_SCHEME -> {
      val name = (uri.lastPathSegment ?: uri.host).orEmpty()
        .substringBefore('?')
        .substringBefore('#')
      name.takeIf(PROJECT_IMAGE_NAME_PATTERN::matches)
        ?: "project asset"
    }
    else -> uri.scheme?.let { "$it:" }
      ?: raw.takeIf(PROJECT_IMAGE_NAME_PATTERN::matches)
      ?: "image"
  }
}

class NoteAssetSchemeHandler(
  private val context: Context,
  private val resolver: ContentResolver,
  private val sourceDirectoryUri: Uri?,
  private val textColor: Int,
) : SchemeHandler() {
  override fun handle(raw: String, uri: Uri): ImageItem {
    val name = uri.schemeSpecificPart?.substringAfterLast('/')
      ?.takeIf(PROJECT_IMAGE_NAME_PATTERN::matches)
      ?: return missingImage(raw, uri)
    val base = sourceDirectoryUri ?: return missingImage(name, uri)
    return try {
      val assetsUri = findChild(base, "assets") ?: return missingImage(name, uri)
      val fileUri = findChild(assetsUri, name) ?: return missingImage(name, uri)
      val stream = resolver.openInputStream(fileUri)
        ?: return missingImage(name, uri)
      ImageItem.withDecodingNeeded(uri.toString(), stream)
    } catch (_: Exception) {
      missingImage(name, uri)
    }
  }

  override fun supportedSchemes(): Collection<String> =
    listOf(NotePreviewRewrite.DEFAULT_ASSET_SCHEME)

  private fun missingImage(source: String, uri: Uri): ImageItem {
    val label = noteImageSourceSummary(source.takeIf { it.isNotBlank() } ?: uri.toString())
    return ImageItem.withResult(
      NoteImagePlaceholderDrawable(
        context,
        context.getString(R.string.note_rich_image_missing, label.take(MAX_IMAGE_SOURCE_LABEL_LENGTH)),
        textColor,
      ),
    )
  }

  private fun findChild(parent: Uri, name: String): Uri? {
    val parentId = DocumentsContract.getDocumentId(parent)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId)
    val cursor = resolver.query(
      childrenUri,
      arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
      null, null, null,
    ) ?: return null
    cursor.use {
      val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      while (it.moveToNext()) {
        if (it.getString(nameColumn) == name) {
          return DocumentsContract.buildDocumentUriUsingTree(parent, it.getString(idColumn))
        }
      }
    }
    return null
  }
}

class OfflineRemoteImageSchemeHandler(
  private val context: Context,
  private val textColor: Int,
) : SchemeHandler() {
  override fun handle(raw: String, uri: Uri): ImageItem {
    val source = noteImageSourceSummary(raw)
    return ImageItem.withResult(
      NoteImagePlaceholderDrawable(
        context,
        context.getString(R.string.note_rich_image_remote, source.take(MAX_IMAGE_SOURCE_LABEL_LENGTH)),
        textColor,
      ),
    )
  }

  override fun supportedSchemes(): Collection<String> = listOf("http", "https")
}
