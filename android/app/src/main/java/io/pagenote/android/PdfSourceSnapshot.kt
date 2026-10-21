package io.pagenote.android

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

internal fun copyPdfSourceSnapshot(input: InputStream, target: File): String {
  val digest = MessageDigest.getInstance("SHA-256")
  try {
    FileOutputStream(target, false).use { output ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        digest.update(buffer, 0, count)
        output.write(buffer, 0, count)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  } catch (error: Exception) {
    target.delete()
    throw error
  }
}
