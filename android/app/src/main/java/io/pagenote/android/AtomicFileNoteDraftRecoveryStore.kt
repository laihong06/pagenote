package io.pagenote.android

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class AtomicFileNoteDraftRecoveryStore(directory: File) : NoteDraftRecoveryStore {
  private val directory = directory.apply {
    if (!exists() && !mkdirs()) throw IOException("Unable to create note recovery directory")
    if (!isDirectory) throw IOException("Note recovery path is not a directory")
  }

  override fun read(key: NoteDraftRecoveryKey): NoteDraftRecoveryRecord? {
    val input = try {
      atomicFile(key).openRead()
    } catch (_: FileNotFoundException) {
      return null
    }
    val record = input.use(::decode)
    return record.takeIf { it.key == key }
  }

  override fun findForProject(
    accessTreeUri: String,
    rootDocumentId: String,
    projectId: String,
  ): List<NoteDraftRecoveryRecord> = recoveryFiles().mapNotNull { baseFile ->
    val input = try {
      AtomicFile(baseFile).openRead()
    } catch (_: FileNotFoundException) {
      return@mapNotNull null
    }
    input.use(::decode).takeIf {
      it.accessTreeUri == accessTreeUri && it.rootDocumentId == rootDocumentId && it.projectId == projectId
    }
  }.sortedByDescending(NoteDraftRecoveryRecord::updatedAtMillis)

  override fun write(record: NoteDraftRecoveryRecord) {
    val atomicFile = atomicFile(record.key)
    val output = atomicFile.startWrite()
    try {
      val json = JSONObject()
        .put("accessTreeUri", record.accessTreeUri)
        .put("rootDocumentId", record.rootDocumentId)
        .put("projectId", record.projectId)
        .put("page", record.page)
        .put("draft", record.draft)
        .put("baseRevision", record.baseRevision)
        .put("updatedAtMillis", record.updatedAtMillis)
      output.write(json.toString().toByteArray(Charsets.UTF_8))
      atomicFile.finishWrite(output)
    } catch (error: Throwable) {
      atomicFile.failWrite(output)
      throw error
    }
  }

  override fun clear(key: NoteDraftRecoveryKey) {
    atomicFile(key).delete()
  }

  private fun atomicFile(key: NoteDraftRecoveryKey) = AtomicFile(File(directory, key.fileName()))

  private fun recoveryFiles(): List<File> = directory.listFiles().orEmpty()
    .mapNotNull { file ->
      when {
        file.name.endsWith(".json.bak") -> File(file.parentFile, file.name.removeSuffix(".bak"))
        file.name.endsWith(".json") -> file
        else -> null
      }
    }
    .distinctBy(File::getAbsolutePath)

  private fun decode(input: java.io.InputStream): NoteDraftRecoveryRecord =
    input.bufferedReader(Charsets.UTF_8).use { reader ->
      val json = JSONObject(reader.readText())
      NoteDraftRecoveryRecord(
        accessTreeUri = json.getString("accessTreeUri"),
        rootDocumentId = json.getString("rootDocumentId"),
        projectId = json.getString("projectId"),
        page = json.getInt("page"),
        draft = json.getString("draft"),
        baseRevision = json.getString("baseRevision"),
        updatedAtMillis = json.optLong("updatedAtMillis", 0L),
      )
    }
}
