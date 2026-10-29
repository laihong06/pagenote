package io.pagenote.android

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 贴纸资产包格式（zip）校验测试：真实 zip 字节流。 */
class StickerPackArchiveTest {
  private fun zip(vararg entries: Pair<String, ByteArray>, withDirectoryEntry: Boolean = false): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
      if (withDirectoryEntry) {
        zip.putNextEntry(ZipEntry("folder/"))
        zip.closeEntry()
      }
      entries.forEach { (name, bytes) ->
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
      }
    }
    return output.toByteArray()
  }

  @Test
  fun describeEntryAcceptsRootAndOneLevelGroup() {
    val root = StickerPackArchive.describeEntry("cat.png")!!
    assertEquals("cat", root.displayName)
    assertEquals("", root.group)
    assertEquals("cat.png", root.relativePath)

    val grouped = StickerPackArchive.describeEntry("emoji/smile.gif")!!
    assertEquals("smile", grouped.displayName)
    assertEquals("emoji", grouped.group)
    assertEquals("emoji/smile.gif", grouped.relativePath)
  }

  @Test
  fun describeEntryRejectsUnsafeOrDeepPaths() {
    assertNull(StickerPackArchive.describeEntry("/cat.png"))
    assertNull(StickerPackArchive.describeEntry("../cat.png"))
    assertNull(StickerPackArchive.describeEntry("a/b/cat.png"))
    assertNull(StickerPackArchive.describeEntry("cat.txt"))
    assertNull(StickerPackArchive.describeEntry("folder//cat.png"))
    assertNull(StickerPackArchive.describeEntry("cat"))
    assertNull(StickerPackArchive.describeEntry("emoji/"))
  }

  @Test
  fun validateAcceptsImagesAndSkipsDirectoryEntries() {
    val entries = StickerPackArchive.validate(
      zip(
        "cat.png" to ByteArray(16),
        "emoji/smile.gif" to ByteArray(16),
        withDirectoryEntry = true,
      ),
    )
    assertEquals(2, entries.size)
    assertEquals("emoji/smile.gif", entries[1].relativePath)
    assertEquals(16L, entries[0].sizeBytes)
  }

  @Test
  fun validateRejectsNonImageEntries() {
    try {
      StickerPackArchive.validate(zip("readme.txt" to "hi".toByteArray()))
      fail("expected IOException")
    } catch (_: IOException) {
    }
  }

  @Test
  fun validateRejectsTraversalEntries() {
    try {
      StickerPackArchive.validate(zip("../evil.png" to ByteArray(4)))
      fail("expected IOException")
    } catch (_: IOException) {
    }
  }

  @Test
  fun validateRejectsDuplicateDisplayNames() {
    try {
      StickerPackArchive.validate(
        zip(
          "cat.png" to ByteArray(4),
          "cat.gif" to ByteArray(4),
        ),
      )
      fail("expected IOException")
    } catch (_: IOException) {
    }
  }

  @Test
  fun validateRejectsEmptyPack() {
    try {
      StickerPackArchive.validate(zip())
      fail("expected IOException")
    } catch (_: IOException) {
    }
  }

  @Test
  fun validateRejectsOversizedEntry() {
    // 真实写入 20MB+1 全零（deflate 后极小），声明尺寸随流真实生效。
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
      zip.putNextEntry(ZipEntry("big.png"))
      val chunk = ByteArray(1024 * 1024)
      var written = 0L
      while (written <= StickerPackArchive.MAX_ENTRY_BYTES) {
        zip.write(chunk, 0, minOf(chunk.size, (StickerPackArchive.MAX_ENTRY_BYTES + 1 - written).toInt()))
        written += chunk.size
      }
      zip.closeEntry()
    }
    try {
      StickerPackArchive.validate(output.toByteArray())
      fail("expected IOException")
    } catch (_: IOException) {
    }
  }

  @Test
  fun validateRejectsTooManyEntries() {
    val names = (0..StickerPackArchive.MAX_ENTRIES).map { index -> "s$index.png" to ByteArray(1) }
    try {
      StickerPackArchive.validate(zip(*names.toTypedArray()))
      fail("expected IOException")
    } catch (_: IOException) {
    }
  }

  @Test
  fun mimeTypeMapsCoverTheWhitelist() {
    StickerPackArchive.IMAGE_EXTENSIONS.forEach { extension ->
      assertNotNull("missing mime for $extension", StickerPackArchive.mimeTypeFor(extension))
    }
    assertNull(StickerPackArchive.mimeTypeFor("txt"))
    assertEquals("image/jpeg", StickerPackArchive.mimeTypeFor("jpg"))
  }
}
