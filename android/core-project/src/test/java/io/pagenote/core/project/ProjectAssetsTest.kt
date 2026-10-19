package io.pagenote.core.project

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectAssetsTest {
  @Test
  fun exportImportRoundTripPreservesRefs() {
    val uploads = Files.createTempDirectory("pagenote-assets-u").toFile()
    val projectRoot = Files.createTempDirectory("pagenote-assets-p").toFile()
    val name = "123e4567-e89b-12d3-a456-426614174000.png"
    File(uploads, name).writeBytes(byteArrayOf(9))
    val text = "![x](/attachments/$name)"

    val exported = ProjectAssets.exportAttachments(text, uploads, projectRoot)
    assertEquals("![x](assets/$name)", exported)
    assertTrue(File(projectRoot, "src/assets/$name").isFile)

    val downstream = Files.createTempDirectory("pagenote-assets-u2").toFile()
    val imported = ProjectAssets.importAssets(exported, projectRoot, downstream)
    assertEquals(text, imported)
    assertTrue(File(downstream, name).isFile)
  }

  @Test
  fun exportSkipsMissingButRewrites() {
    val uploads = Files.createTempDirectory("pagenote-assets-miss-u").toFile()
    val projectRoot = Files.createTempDirectory("pagenote-assets-miss-p").toFile()
    val text = "![a](/attachments/123e4567-e89b-12d3-a456-426614174000.png)"

    val exported = ProjectAssets.exportAttachments(text, uploads, projectRoot)

    assertEquals("![a](assets/123e4567-e89b-12d3-a456-426614174000.png)", exported)
    assertFalse(File(projectRoot, "src/assets/123e4567-e89b-12d3-a456-426614174000.png").exists())
  }
}
