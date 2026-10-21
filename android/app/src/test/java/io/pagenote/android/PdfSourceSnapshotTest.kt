package io.pagenote.android

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfSourceSnapshotTest {
  @Test fun sourceSnapshotHashMatchesCopiedBytes() {
    val sourceBytes = "%PDF-1.7\nsource snapshot".toByteArray()
    val snapshot = Files.createTempFile("pagenote-source-", ".pdf").toFile()

    try {
      val revision = copyPdfSourceSnapshot(ByteArrayInputStream(sourceBytes), snapshot)

      assertArrayEquals(sourceBytes, snapshot.readBytes())
      assertEquals(sha256(sourceBytes), revision)
    } finally {
      snapshot.delete()
    }
  }

  private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }
}
