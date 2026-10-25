package io.pagenote.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import io.pagenote.feature.audio.AudioCapsule
import io.pagenote.feature.audio.AudioCapsuleCodec
import io.pagenote.feature.audio.AudioCapsuleIndex
import io.pagenote.feature.audio.AudioCapsulePolicy
import io.pagenote.feature.audio.AudioQuotaPolicy
import java.io.IOException
import java.util.UUID

/**
 * 音频数据（移植批次 8，SAF 侧）：`<project>/audio.json` 清单与
 * `<project>/audio/` 录音二进制的读写适配。编解码与校验在 feature-audio 的
 * [AudioCapsuleCodec]；清单写入姿态对齐 [ProjectMaterialStore]
 * （已存在文件走 SafRecoverableDocumentWriter 恢复写入，新建失败回滚删除），
 * 音频目录对齐批次 5 贴纸包的 `<project>/stickers/` 先例。
 */
internal class ProjectAudioStore(
  private val resolver: ContentResolver,
  private val sourceDirectoryUri: Uri,
) {
  fun readCapsules(): AudioCapsuleIndex {
    val uri = findManifest() ?: return AudioCapsuleIndex()
    val text = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
      ?: throw IOException("The project audio manifest could not be read")
    return AudioCapsuleIndex(AudioCapsuleCodec.decode(text))
  }

  /** 返回恢复副本名（新建文件或无副本时为 null）。 */
  fun writeCapsules(index: AudioCapsuleIndex): String? {
    val encoded = AudioCapsuleCodec.encode(index.all)
    val existing = findManifest()
    if (existing == null) {
      var created: Uri? = null
      try {
        created = DocumentsContract.createDocument(
          resolver,
          sourceDirectoryUri,
          "application/json",
          AudioCapsuleCodec.FILE_NAME,
        ) ?: throw IOException("The project did not create ${AudioCapsuleCodec.FILE_NAME}")
        if (queryDisplayName(created) != AudioCapsuleCodec.FILE_NAME) {
          throw IOException("The project changed the audio manifest name")
        }
        resolver.openOutputStream(created, "w")?.buffered()?.use { output ->
          output.write(encoded.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("The audio manifest could not be written")
        return null
      } catch (error: Exception) {
        created?.let { uri -> runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
        throw error
      }
    }
    val original = resolver.openInputStream(existing)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
      ?: throw IOException("The project audio manifest could not be read")
    return SafRecoverableDocumentWriter(resolver, existing, AudioCapsuleCodec.FILE_NAME)
      .write(encoded, original)
  }

  /** 在 `<project>/audio/` 下新建录音文件（目录缺失时创建）。 */
  fun createRecordingFile(): Uri {
    val audioDir = findOrCreateAudioDirectory()
    return DocumentsContract.createDocument(
      resolver,
      audioDir,
      RECORDING_MIME,
      "${UUID.randomUUID()}.m4a",
    ) ?: throw IOException("The project did not create the recording file")
  }

  /** 录音文件显示名 → 资产名（`<uuid36>.m4a`）。 */
  fun assetNameOf(recordingUri: Uri): String =
    queryDisplayName(recordingUri)?.takeIf(AudioCapsulePolicy::isValidAssetName)
      ?: run {
        runCatching { DocumentsContract.deleteDocument(resolver, recordingUri) }
        throw IOException("The project changed the recording file name")
      }

  /** 删除录音文件（清单移除后调用）。 */
  fun deleteRecording(asset: String): Boolean {
    val audioDir = findAudioDirectory() ?: return false
    val child = findChild(audioDir, asset) ?: return false
    return DocumentsContract.deleteDocument(resolver, child)
  }

  /** 打开录音只读描述符（MediaPlayer.setDataSource / PCM 解码共用）。 */
  fun openRecordingDescriptor(asset: String): android.os.ParcelFileDescriptor? {
    val audioDir = findAudioDirectory() ?: return null
    val child = findChild(audioDir, asset) ?: return null
    return resolver.openFileDescriptor(child, "r")
  }

  /** 录音资产占用字节（配额计量；目录缺失 = 0）。 */
  fun usageBytes(): Long {
    val audioDir = findAudioDirectory() ?: return 0L
    val parentId = DocumentsContract.getDocumentId(audioDir)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(audioDir, parentId)
    val cursor = resolver.query(
      childrenUri,
      arrayOf(
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_SIZE,
      ),
      null,
      null,
      null,
    ) ?: return 0L
    cursor.use {
      val nameColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      val sizeColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
      var total = 0L
      while (it.moveToNext()) {
        val name = it.getString(nameColumn) ?: continue
        if (!AudioQuotaPolicy.countsTowardUsage(name)) continue
        if (sizeColumn >= 0) total += it.getLong(sizeColumn).coerceAtLeast(0L)
      }
      return total
    }
  }

  private fun findOrCreateAudioDirectory(): Uri {
    findAudioDirectory()?.let { return it }
    return DocumentsContract.createDocument(
      resolver,
      sourceDirectoryUri,
      DocumentsContract.Document.MIME_TYPE_DIR,
      AUDIO_DIRECTORY,
    ) ?: throw IOException("The project audio directory could not be created")
  }

  private fun findAudioDirectory(): Uri? {
    val parentId = DocumentsContract.getDocumentId(sourceDirectoryUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(sourceDirectoryUri, parentId)
    val cursor = resolver.query(
      childrenUri,
      arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
      null,
      null,
      null,
    ) ?: throw IOException("The project directory could not be listed")
    cursor.use {
      val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      while (it.moveToNext()) {
        if (it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) == AUDIO_DIRECTORY) {
          return DocumentsContract.buildDocumentUriUsingTree(sourceDirectoryUri, it.getString(idColumn))
        }
      }
    }
    return null
  }

  private fun findManifest(): Uri? = findChildDirect(AudioCapsuleCodec.FILE_NAME)

  private fun findChildDirect(displayName: String): Uri? {
    val parentId = DocumentsContract.getDocumentId(sourceDirectoryUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(sourceDirectoryUri, parentId)
    val cursor = resolver.query(
      childrenUri,
      arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
      null,
      null,
      null,
    ) ?: throw IOException("The project directory could not be listed")
    cursor.use {
      val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      while (it.moveToNext()) {
        if (it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) == displayName) {
          return DocumentsContract.buildDocumentUriUsingTree(sourceDirectoryUri, it.getString(idColumn))
        }
      }
    }
    return null
  }

  private fun findChild(parentUri: Uri, displayName: String): Uri? {
    val parentId = DocumentsContract.getDocumentId(parentUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentId)
    val cursor = resolver.query(
      childrenUri,
      arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
      null,
      null,
      null,
    ) ?: return null
    cursor.use {
      val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      while (it.moveToNext()) {
        if (it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) == displayName) {
          return DocumentsContract.buildDocumentUriUsingTree(parentUri, it.getString(idColumn))
        }
      }
    }
    return null
  }

  private fun queryDisplayName(uri: Uri): String? = resolver.query(
    uri,
    arrayOf(OpenableColumns.DISPLAY_NAME),
    null,
    null,
    null,
  )?.use {
    val column = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    if (column >= 0 && it.moveToFirst()) it.getString(column)?.takeIf(String::isNotBlank) else null
  }

  private companion object {
    const val AUDIO_DIRECTORY = "audio"
    const val RECORDING_MIME = "audio/mp4"
  }
}
