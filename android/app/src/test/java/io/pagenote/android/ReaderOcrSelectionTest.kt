package io.pagenote.android

import io.pagenote.core.reader.PageRegion
import io.pagenote.core.reader.PreparedCache
import io.pagenote.core.reader.PreparedManifest
import io.pagenote.feature.ocr.OcrBlock
import io.pagenote.feature.ocr.OcrResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderOcrSelectionTest {
  private val selection = NormalizedOcrRegion(100, 150, 600, 700)
  private val sourceRevision = "a".repeat(64)
  private val engine = "test-ocr"
  private val result = OcrResult(
    engine = engine,
    status = "ok",
    blocks = listOf(OcrBlock("recognized text", 0.96, 100.0, 150.0, 200.0, 50.0)),
  )

  @Test fun regionActionEntersSelectionAndCancelRestoresInput() {
    val selecting = ReaderOcrState().enterSelection(pageIndex = 2)

    assertEquals(ReaderOcrMode.SELECTING, selecting.mode)
    assertTrue(selecting.inputLocked)
    assertEquals(2, selecting.pageIndex)

    val cancelled = selecting.cancel()

    assertEquals(ReaderOcrState(), cancelled)
    assertFalse(cancelled.inputLocked)
  }

  @Test fun recognizeRequiresSelectionAndResultDismissalRestoresInput() {
    assertNull(ReaderOcrState().enterSelection(0).beginRecognition())

    val running = ReaderOcrState().enterSelection(0).withSelection(selection).beginRecognition()!!
    assertEquals(ReaderOcrMode.RECOGNIZING, running.mode)
    assertTrue(running.inputLocked)

    val resultState = running.showResult()
    assertEquals(ReaderOcrMode.RESULT, resultState.mode)
    assertTrue(resultState.inputLocked)
    assertEquals(ReaderOcrState(), resultState.dismissResult())
  }

  @Test fun invalidDragShowsRecoverableMessageWithoutInvokingOcr() {
    val fresh = ReaderOcrState().enterSelection(0)
    assertFalse(fresh.selectionInvalid)

    val invalid = fresh.withSelection(null)
    assertNull(invalid.selection)
    assertTrue(invalid.selectionInvalid)
    assertNull(invalid.beginRecognition())

    val recovered = invalid.withSelection(selection)
    assertEquals(selection, recovered.selection)
    assertFalse(recovered.selectionInvalid)
  }

  @Test fun invalidDragKeepsTheLastValidSelection() {
    val selected = ReaderOcrState().enterSelection(0).withSelection(selection)

    val afterInvalidDrag = selected.withSelection(null)

    assertEquals(selection, afterInvalidDrag.selection)
    assertFalse(afterInvalidDrag.selectionInvalid)
    assertTrue(afterInvalidDrag.beginRecognition() != null)
  }

  @Test fun cacheHitBypassesInferenceAndImageEncoding() = runBlocking {
    val cached = cacheEntry()
    val cache = FakePreparedRegionCacheStore(cached)
    var inferenceCalls = 0
    var imageEncodingCalls = 0

    val outcome = processor(cache).recognize(
      srcDirectoryUri = "content://test/src",
      documentId = "doc",
      page = 3,
      sourceRevision = sourceRevision,
      selection = selection,
      refresh = false,
      imageWidth = 800,
      imageHeight = 1200,
      renderScale = 2.0,
      engine = engine,
      notCachedWarning = "not cached",
      recognize = { inferenceCalls += 1; result },
      encodeImages = { imageEncodingCalls += 1; PreparedOcrImages(byteArrayOf(1), byteArrayOf(2)) },
    )

    assertTrue(outcome.fromCache)
    assertEquals(cached.result, outcome.result)
    assertEquals(0, inferenceCalls)
    assertEquals(0, imageEncodingCalls)
    assertEquals(1, cache.readCalls)
    assertEquals(0, cache.writeCalls)
  }

  @Test fun writtenManifestCarriesOcrCounts() = runBlocking {
    val cache = FakePreparedRegionCacheStore()

    processor(cache).recognize(
      srcDirectoryUri = "content://test/src",
      documentId = "doc",
      page = 3,
      sourceRevision = sourceRevision,
      selection = selection,
      refresh = true,
      imageWidth = 800,
      imageHeight = 1200,
      renderScale = 2.0,
      engine = engine,
      notCachedWarning = "not cached",
      recognize = { result },
      encodeImages = { PreparedOcrImages(byteArrayOf(1), byteArrayOf(2)) },
    )

    val written = cache.writtenManifest ?: error("manifest was not written")
    assertEquals("recognized text".length, written.ocrCharacters)
    assertEquals("recognized text".length, written.textCharacters)
    assertEquals(1, written.blockCount)
    assertEquals(2.0, written.scale, 0.0)
  }

  @Test fun refreshSkipsCacheReadButWritesResult() = runBlocking {
    val cached = cacheEntry()
    val cache = FakePreparedRegionCacheStore(cached)
    var inferenceCalls = 0

    val outcome = processor(cache).recognize(
      srcDirectoryUri = "content://test/src",
      documentId = "doc",
      page = 3,
      sourceRevision = sourceRevision,
      selection = selection,
      refresh = true,
      imageWidth = 800,
      imageHeight = 1200,
      renderScale = 2.0,
      engine = engine,
      notCachedWarning = "not cached",
      recognize = { inferenceCalls += 1; result },
      encodeImages = { PreparedOcrImages(byteArrayOf(1), byteArrayOf(2)) },
    )

    assertFalse(outcome.fromCache)
    assertEquals(1, inferenceCalls)
    assertEquals(0, cache.readCalls)
    assertEquals(1, cache.writeCalls)
  }

  @Test fun cacheWriteFailurePreservesOcrOutputAndAddsWarning() = runBlocking {
    val cache = FakePreparedRegionCacheStore(writeResult = PreparedOcrCacheWriteResult.NotSaved("read only"))

    val outcome = processor(cache).recognize(
      srcDirectoryUri = "content://test/src",
      documentId = "doc",
      page = 3,
      sourceRevision = sourceRevision,
      selection = selection,
      refresh = false,
      imageWidth = 800,
      imageHeight = 1200,
      renderScale = 2.0,
      engine = engine,
      notCachedWarning = "recognized but not cached",
      recognize = { result },
      encodeImages = { PreparedOcrImages(byteArrayOf(1), byteArrayOf(2)) },
    )

    assertFalse(outcome.fromCache)
    assertEquals("recognized text", outcome.result.blocks.single().text)
    assertTrue(outcome.result.warnings.contains("recognized but not cached"))
    assertEquals(1, cache.writeCalls)
  }

  @Test fun sourceHashFailureSkipsCacheAndReportsNotCached() = runBlocking {
    val cache = FakePreparedRegionCacheStore()
    var inferenceCalls = 0

    val outcome = processor(cache).recognize(
      srcDirectoryUri = "content://test/src",
      documentId = "doc",
      page = 3,
      sourceRevision = null,
      selection = selection,
      refresh = false,
      imageWidth = 800,
      imageHeight = 1200,
      renderScale = 2.0,
      engine = engine,
      notCachedWarning = "source revision unavailable; not cached",
      recognize = { inferenceCalls += 1; result },
      encodeImages = { error("image encoding should not run") },
    )

    assertFalse(outcome.fromCache)
    assertEquals(1, inferenceCalls)
    assertEquals(0, cache.readCalls)
    assertEquals(0, cache.writeCalls)
    assertTrue(outcome.result.warnings.contains("source revision unavailable; not cached"))
  }

  private fun processor(cache: PreparedRegionCacheStore) = RegionOcrProcessor(cache)

  private fun cacheEntry(): PreparedOcrCacheEntry {
    val pageRegion = selection.toPageRegion()
    return PreparedOcrCacheEntry(
      manifest = PreparedManifest(
        documentId = "doc",
        page = 3,
        sourceRevision = sourceRevision,
        kind = "pdf",
        region = pageRegion,
        cacheKey = PreparedCache.cacheKey(sourceRevision, "region-ocr", pageRegion, engine),
        width = 800,
        height = 1200,
        scale = 1.0,
        files = listOf("manifest.json", "page.png", "region.png", "ocr.txt", "blocks.json"),
      ),
      result = result,
      pagePng = byteArrayOf(1),
      regionPng = byteArrayOf(2),
    )
  }

  private class FakePreparedRegionCacheStore(
    var cached: PreparedOcrCacheEntry? = null,
    var writeResult: PreparedOcrCacheWriteResult = PreparedOcrCacheWriteResult.Saved,
  ) : PreparedRegionCacheStore {
    var readCalls = 0
    var writeCalls = 0
    var writtenManifest: PreparedManifest? = null

    override fun read(
      srcDirectoryUri: String,
      documentId: String,
      page: Int,
      sourceRevision: String,
      region: PageRegion,
      engine: String,
    ): PreparedOcrCacheEntry? {
      readCalls += 1
      return cached
    }

    override fun write(
      srcDirectoryUri: String,
      manifest: PreparedManifest,
      result: OcrResult,
      pagePng: ByteArray,
      regionPng: ByteArray,
    ): PreparedOcrCacheWriteResult {
      writeCalls += 1
      writtenManifest = manifest
      return writeResult
    }
  }
}
