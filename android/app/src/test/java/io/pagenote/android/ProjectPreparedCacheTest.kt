package io.pagenote.android

import io.pagenote.core.reader.PageRegion
import io.pagenote.core.reader.PreparedCache
import io.pagenote.core.reader.PreparedManifest
import io.pagenote.feature.ocr.OcrBlock
import io.pagenote.feature.ocr.OcrResult
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectPreparedCacheTest {
  private val root = "content://test/src"
  private val region = PageRegion(100.0, 120.0, 400.0, 300.0)
  private val revision = "a".repeat(64)
  private val engine = "test-ocr"
  private val result = OcrResult(
    engine = engine,
    status = "partial",
    blocks = listOf(OcrBlock("hello", 0.91, 101.0, 122.0, 20.0, 10.0)),
    warnings = listOf("review"),
  )
  private val pagePng = byteArrayOf(1, 2, 3)
  private val regionPng = byteArrayOf(4, 5, 6)

  @Test fun emptyTreeIsMiss() {
    assertNull(store(MemoryDocuments()).read(root, "doc", 1, revision, region, engine))
  }

  @Test fun savedEntryRoundTripsResultAndImages() {
    val documents = MemoryDocuments()
    val store = store(documents)
    assertTrue(store.write(root, manifest(), result, pagePng, regionPng) is PreparedOcrCacheWriteResult.Saved)

    val entry = store.read(root, "doc", 1, revision, region, engine)
    assertNotNull(entry)
    assertEquals(result, entry!!.result)
    assertTrue(pagePng.contentEquals(entry.pagePng))
    assertTrue(regionPng.contentEquals(entry.regionPng))
  }

  @Test fun savedManifestIncludesCallerAndOcrWarnings() {
    val documents = MemoryDocuments()
    val store = store(documents)
    val resultWithDuplicateWarnings = result.copy(
      warnings = listOf("caller warning", "review", "review"),
    )
    store.write(root, manifest(), resultWithDuplicateWarnings, pagePng, regionPng)

    val entry = store.read(root, "doc", 1, revision, region, engine)

    assertEquals(listOf("caller warning", "review"), entry!!.manifest.warnings)
  }

  @Test fun changedDocumentPageSourceRegionEngineOrVersionIsMiss() {
    val documents = MemoryDocuments()
    val store = store(documents)
    store.write(root, manifest(), result, pagePng, regionPng)

    assertNull(store.read(root, "other", 1, revision, region, engine))
    assertNull(store.read(root, "doc", 2, revision, region, engine))
    assertNull(store.read(root, "doc", 1, "b".repeat(64), region, engine))
    assertNull(store.read(root, "doc", 1, revision, PageRegion(101.0, 120.0, 400.0, 300.0), engine))
    assertNull(store.read(root, "doc", 1, revision, region, "other-engine"))

    val old = manifest().copy(cacheKey = "0".repeat(64))
    val currentDir = documents.findChild(documents.findChild(root, "prepared")!!,
      PreparedCache.folderName(1, manifest().cacheKey))!!
    documents.put(currentDir, "manifest.json", io.pagenote.core.reader.PreparedManifestCodec.encode(old).toByteArray())
    assertNull(store.read(root, "doc", 1, revision, region, engine))
  }

  @Test fun malformedManifestOrMissingFileIsMiss() {
    val docs = MemoryDocuments()
    val store = store(docs)
    store.write(root, manifest(), result, pagePng, regionPng)
    val folder = PreparedCache.folderName(1, manifest().cacheKey)
    val dir = docs.findChild(root, "prepared")!!
    val entryDir = docs.findChild(dir, folder)!!
    docs.put(entryDir, "manifest.json", "{".toByteArray())
    assertNull(store.read(root, "doc", 1, revision, region, engine))

    docs.put(entryDir, "manifest.json", io.pagenote.core.reader.PreparedManifestCodec.encode(manifest()).toByteArray())
    docs.remove(entryDir, "region.png")
    assertNull(store.read(root, "doc", 1, revision, region, engine))
  }

  @Test fun invalidImageOrResultIsMiss() {
    val docs = MemoryDocuments()
    store(docs).write(root, manifest(), result, pagePng, regionPng)
    val rejectingImages = store(docs, imageValidator = { _, _ -> null })
    assertNull(rejectingImages.read(root, "doc", 1, revision, region, engine))

    val invalid = OcrResult(engine, "failed", imagePreserved = true)
    assertFalse(store(MemoryDocuments()).write(root, manifest(), invalid, pagePng, regionPng)
      is PreparedOcrCacheWriteResult.Saved)

    val invalidResultDocs = MemoryDocuments()
    store(invalidResultDocs).write(root, manifest(), result, pagePng, regionPng)
    val invalidResultDir = invalidResultDocs.findChild(
      invalidResultDocs.findChild(root, "prepared")!!,
      PreparedCache.folderName(1, manifest().cacheKey),
    )!!
    invalidResultDocs.put(invalidResultDir, "blocks.json", "{}".toByteArray())
    assertNull(store(invalidResultDocs).read(root, "doc", 1, revision, region, engine))
  }

  @Test fun writeFailureReturnsNotSaved() {
    val docs = MemoryDocuments().apply { failWrites = true }
    val writeResult = store(docs).write(root, manifest(), result, pagePng, regionPng)
    assertTrue(writeResult is PreparedOcrCacheWriteResult.NotSaved)
  }

  @Test fun manifestIsRequiredAsCompletionMarker() {
    val docs = MemoryDocuments()
    val store = store(docs)
    store.write(root, manifest(), result, pagePng, regionPng)
    val entryDir = docs.findChild(docs.findChild(root, "prepared")!!,
      PreparedCache.folderName(1, manifest().cacheKey))!!
    docs.remove(entryDir, "manifest.json")
    assertNull(store.read(root, "doc", 1, revision, region, engine))

    docs.put(entryDir, "manifest.json", io.pagenote.core.reader.PreparedManifestCodec.encode(manifest()).toByteArray())
    docs.operations.clear()
    assertTrue(store.write(root, manifest(), result, pagePng, regionPng) is PreparedOcrCacheWriteResult.Saved)
    assertEquals("delete:manifest.json", docs.operations.first())
    assertEquals("manifest.json", docs.operations.last())
  }

  @Test fun childDiscoveryFailureStopsReplacement() {
    val docs = MemoryDocuments()
    val store = store(docs)
    assertTrue(store.write(root, manifest(), result, pagePng, regionPng) is PreparedOcrCacheWriteResult.Saved)
    val entryDir = docs.findChild(docs.findChild(root, "prepared")!!,
      PreparedCache.folderName(1, manifest().cacheKey))!!
    val previous = store.read(root, "doc", 1, revision, region, engine)!!
    docs.failFindChildAt = "$entryDir/manifest.json"

    val writeResult = store.write(root, manifest(), result.copy(warnings = listOf("replacement")),
      byteArrayOf(7, 8, 9), byteArrayOf(10, 11, 12))

    assertTrue(writeResult is PreparedOcrCacheWriteResult.NotSaved)
    docs.failFindChildAt = null
    val retained = store.read(root, "doc", 1, revision, region, engine)!!
    assertEquals(previous.result, retained.result)
    assertTrue(previous.pagePng.contentEquals(retained.pagePng))
    assertTrue(previous.regionPng.contentEquals(retained.regionPng))
  }

  private fun manifest() = PreparedManifest(
    documentId = "doc", page = 1, sourceRevision = revision, kind = "pdf", region = region,
    cacheKey = PreparedCache.cacheKey(revision, "region-ocr", region, engine),
    width = 1200, height = 1600, scale = 1.0, warnings = listOf("caller warning"),
    files = listOf("manifest.json", "page.png", "region.png", "ocr.txt", "blocks.json"),
  )

  private fun store(documents: MemoryDocuments,
    imageValidator: (ByteArray, String) -> PreparedImageDimensions? = { _, name ->
      if (name == "page.png") PreparedImageDimensions(1200, 1600) else PreparedImageDimensions(400, 300)
    }) =
    ProjectPreparedCache(documents, PreparedImageValidator(imageValidator))

  private class MemoryDocuments : PreparedDocumentAccess {
    private val directories = mutableSetOf("content://test/src")
    private val files = mutableMapOf<String, ByteArray>()
    val operations = mutableListOf<String>()
    var failWrites = false
    var failFindChildAt: String? = null

    private fun path(parent: String, name: String) = "$parent/$name"
    override fun findChild(parent: String, name: String): String? {
      val child = path(parent, name)
      if (child == failFindChildAt) throw IOException("child query failed")
      return if (child in directories || files.containsKey(child)) child else null
    }
    override fun createDirectory(parent: String, name: String): String {
      val child = path(parent, name)
      directories += child
      operations += "mkdir:$name"
      return child
    }
    override fun createFile(parent: String, mimeType: String, name: String): String {
      if (failWrites) error("write denied")
      val child = path(parent, name)
      files[child] = byteArrayOf()
      operations += name
      return child
    }
    override fun delete(uri: String): Boolean {
      val key = uri
      operations += "delete:${key.substringAfterLast('/')}"
      files.remove(key)
      directories.remove(key)
      return true
    }
    override fun openInput(uri: String) = files[uri]?.inputStream()
    override fun openOutput(uri: String) = if (failWrites) error("write denied") else
      object : java.io.ByteArrayOutputStream() {
        override fun close() { files[uri] = toByteArray() }
      }
    fun put(parent: String, name: String, bytes: ByteArray) { files[path(parent, name)] = bytes }
    fun remove(parent: String, name: String) { files.remove(path(parent, name)) }
  }
}
