package io.pagenote.android

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal class NoteExportSnapshotStore(directory: File) {
  private val atomicFile = AtomicFile(File(directory, "pending-export-content"))

  fun write(content: String) {
    val directory = atomicFile.baseFile.parentFile ?: error("Note export snapshot has no parent directory")
    if (!directory.exists() && !directory.mkdirs()) {
      throw IOException("Unable to create note export snapshot directory")
    }
    if (!directory.isDirectory) throw IOException("Note export snapshot path is not a directory")
    val output = atomicFile.startWrite()
    try {
      output.write(content.toByteArray(Charsets.UTF_8))
      atomicFile.finishWrite(output)
    } catch (error: Throwable) {
      atomicFile.failWrite(output)
      throw error
    }
  }

  fun read(): String? {
    val input = try {
      atomicFile.openRead()
    } catch (_: FileNotFoundException) {
      return null
    }
    return input.bufferedReader(Charsets.UTF_8).use { it.readText() }
  }

  fun clear() = atomicFile.delete()
}
