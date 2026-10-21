package io.pagenote.core.project

import java.io.InputStream
import java.security.MessageDigest

data class OfficeSourceRevision(
  val sha256: String,
  val sizeBytes: Long,
) {
  init {
    require(sha256.matches(Regex("^[0-9a-f]{64}$"))) { "invalid source digest" }
    require(sizeBytes >= 0) { "invalid source size" }
  }

  companion object {
    fun from(input: InputStream): OfficeSourceRevision {
      val digest = MessageDigest.getInstance("SHA-256")
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      var size = 0L
      while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) {
          val byte = input.read()
          if (byte < 0) break
          digest.update(byte.toByte())
          size = Math.addExact(size, 1L)
          continue
        }
        digest.update(buffer, 0, count)
        size = Math.addExact(size, count.toLong())
      }
      return OfficeSourceRevision(digest.digest().toHex(), size)
    }
  }
}

enum class OfficeSaveReadiness {
  NO_CHANGES,
  SOURCE_CONFLICT,
  DESTINATION_UNAVAILABLE,
  RECOVERY_REQUIRED,
  READY,
}

object OfficeSavePolicy {
  fun evaluate(
    openingRevision: OfficeSourceRevision,
    currentRevision: OfficeSourceRevision,
    editorDirty: Boolean,
    destinationWritable: Boolean,
    recoveryCopyReady: Boolean,
  ): OfficeSaveReadiness = when {
    !editorDirty -> OfficeSaveReadiness.NO_CHANGES
    openingRevision != currentRevision -> OfficeSaveReadiness.SOURCE_CONFLICT
    !destinationWritable -> OfficeSaveReadiness.DESTINATION_UNAVAILABLE
    !recoveryCopyReady -> OfficeSaveReadiness.RECOVERY_REQUIRED
    else -> OfficeSaveReadiness.READY
  }
}

private fun ByteArray.toHex(): String {
  val bytes = this
  val digits = "0123456789abcdef"
  return buildString(bytes.size * 2) {
    bytes.forEach { byte ->
      val value = byte.toInt() and 0xff
      append(digits[value ushr 4])
      append(digits[value and 0x0f])
    }
  }
}
