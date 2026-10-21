package io.pagenote.android

import android.graphics.BitmapFactory
import io.pagenote.core.reader.PageRegion
import io.pagenote.core.reader.PreparedCache
import io.pagenote.core.reader.PreparedManifest
import io.pagenote.core.reader.PreparedManifestCodec
import io.pagenote.core.reader.PreparedManifestStore
import io.pagenote.feature.ocr.OcrResult
import io.pagenote.feature.ocr.OcrResultJsonCodec
import io.pagenote.feature.ocr.OcrResultPolicy
import java.io.ByteArrayInputStream
import java.io.IOException

data class PreparedOcrCacheEntry(
  val manifest: PreparedManifest,
  val result: OcrResult,
  val pagePng: ByteArray,
  val regionPng: ByteArray,
)

interface PreparedRegionCacheStore {
  fun read(
    srcDirectoryUri: String,
    documentId: String,
    page: Int,
    sourceRevision: String,
    region: PageRegion,
    engine: String,
  ): PreparedOcrCacheEntry?

  fun write(
    srcDirectoryUri: String,
    manifest: PreparedManifest,
    result: OcrResult,
    pagePng: ByteArray,
    regionPng: ByteArray,
  ): PreparedOcrCacheWriteResult
}

sealed interface PreparedOcrCacheWriteResult {
  data object Saved : PreparedOcrCacheWriteResult
  data class NotSaved(val reason: String) : PreparedOcrCacheWriteResult
}

data class PreparedImageDimensions(val width: Int, val height: Int)

fun interface PreparedImageValidator {
  fun dimensions(png: ByteArray, fileName: String): PreparedImageDimensions?
}

class ProjectPreparedCache(
  private val documents: PreparedDocumentAccess,
  private val imageValidator: PreparedImageValidator = BitmapFactoryPreparedImageValidator,
) : PreparedRegionCacheStore {
  override fun read(
    srcDirectoryUri: String,
    documentId: String,
    page: Int,
    sourceRevision: String,
    region: PageRegion,
    engine: String,
  ): PreparedOcrCacheEntry? = runCatching {
    require(documentId.isNotBlank() && page >= 1 && engine.isNotBlank())
    val key = PreparedCache.cacheKey(sourceRevision, "region-ocr", region, engine)
    val preparedDir = documents.findChild(srcDirectoryUri, PREPARED_DIR) ?: return null
    val entryDir = documents.findChild(preparedDir, PreparedCache.folderName(page, key)) ?: return null
    val manifestBytes = readFile(entryDir, MANIFEST_FILE) ?: return null
    val manifest = PreparedManifestCodec.decode(manifestBytes.toString(Charsets.UTF_8))
    if (!hasExactFileList(manifest) || !manifestMatches(manifest, documentId, page, sourceRevision, region, key)) {
      return null
    }
    val files = REQUIRED_FILES.associateWith { name -> readFile(entryDir, name) ?: return null }
    val pageDimensions = imageValidator.dimensions(files.getValue(PAGE_IMAGE), PAGE_IMAGE) ?: return null
    val regionDimensions = imageValidator.dimensions(files.getValue(REGION_IMAGE), REGION_IMAGE) ?: return null
    if (pageDimensions.width != manifest.width || pageDimensions.height != manifest.height ||
      regionDimensions.width <= 0 || regionDimensions.height <= 0
    ) return null
    val ocr = OcrResultJsonCodec.decode(files.getValue(OCR_RESULT).toString(Charsets.UTF_8))
    OcrResultPolicy.validate(ocr)
    if (ocr.status !in CACHEABLE_STATUSES || ocr.engine != engine) return null
    if (files.getValue(OCR_TEXT).toString(Charsets.UTF_8) != OcrResultPolicy.textOf(ocr)) return null
    PreparedOcrCacheEntry(manifest, ocr, files.getValue(PAGE_IMAGE), files.getValue(REGION_IMAGE))
  }.getOrNull()

  override fun write(
    srcDirectoryUri: String,
    manifest: PreparedManifest,
    result: OcrResult,
    pagePng: ByteArray,
    regionPng: ByteArray,
  ): PreparedOcrCacheWriteResult = try {
    validateWrite(manifest, result, pagePng, regionPng)
    val preparedDir = documents.findChild(srcDirectoryUri, PREPARED_DIR)
      ?: documents.createDirectory(srcDirectoryUri, PREPARED_DIR)
    val folderName = PreparedCache.folderName(manifest.page, manifest.cacheKey)
    val entryDir = documents.findChild(preparedDir, folderName)
      ?: documents.createDirectory(preparedDir, folderName)

    // The previous manifest is the completion marker; remove it before touching entry files.
    documents.findChild(entryDir, MANIFEST_FILE)?.let {
      if (!documents.delete(it)) throw IOException("Could not remove previous completion manifest")
    }
    val contents = linkedMapOf(
      PAGE_IMAGE to pagePng,
      REGION_IMAGE to regionPng,
      OCR_TEXT to OcrResultPolicy.textOf(result).toByteArray(Charsets.UTF_8),
      OCR_RESULT to OcrResultJsonCodec.encode(result).toByteArray(Charsets.UTF_8),
    )
    contents.forEach { (name, bytes) -> replaceFile(entryDir, name, mimeType(name), bytes) }
    replaceFile(
      entryDir,
      MANIFEST_FILE,
      "application/json",
      PreparedManifestCodec.encode(manifest.copy(
        files = REQUIRED_FILES,
        warnings = (manifest.warnings + result.warnings).distinct(),
      )).toByteArray(Charsets.UTF_8),
    )
    PreparedOcrCacheWriteResult.Saved
  } catch (error: Exception) {
    PreparedOcrCacheWriteResult.NotSaved(error.message ?: "Project cache write failed")
  }

  private fun validateWrite(manifest: PreparedManifest, result: OcrResult, pagePng: ByteArray, regionPng: ByteArray) {
    PreparedManifestStore.validate(manifest)
    OcrResultPolicy.validate(result)
    require(manifest.kind == "pdf" && manifest.originalPreserved && manifest.visualReviewRequired)
    require(manifest.cacheKey == PreparedCache.cacheKey(
      manifest.sourceRevision, "region-ocr", manifest.region, result.engine,
    )) { "cache key does not match source, region, engine, or cache version" }
    require(result.status in CACHEABLE_STATUSES) { "only successful or partial OCR results can be cached" }
    require(hasExactFileList(manifest)) { "manifest must list all required cache files" }
    val pageDimensions = imageValidator.dimensions(pagePng, PAGE_IMAGE)
      ?: throw IllegalArgumentException("invalid page PNG")
    val regionDimensions = imageValidator.dimensions(regionPng, REGION_IMAGE)
      ?: throw IllegalArgumentException("invalid region PNG")
    require(pageDimensions.width == manifest.width && pageDimensions.height == manifest.height) {
      "page PNG dimensions do not match manifest"
    }
    require(regionDimensions.width > 0 && regionDimensions.height > 0) { "invalid region PNG dimensions" }
  }

  private fun manifestMatches(
    manifest: PreparedManifest,
    documentId: String,
    page: Int,
    sourceRevision: String,
    region: PageRegion,
    key: String,
  ): Boolean {
    val expected = manifest.copy(
      documentId = documentId,
      page = page,
      sourceRevision = sourceRevision,
      region = region,
      cacheKey = key,
    )
    return PreparedManifestStore.isHit(manifest, expected) && manifest.kind == "pdf" &&
      manifest.originalPreserved && manifest.visualReviewRequired
  }

  private fun hasExactFileList(manifest: PreparedManifest) = manifest.files.toSet() == REQUIRED_FILES.toSet() &&
    manifest.files.size == REQUIRED_FILES.size

  private fun readFile(directory: String, name: String): ByteArray? {
    val uri = documents.findChild(directory, name) ?: return null
    return documents.openInput(uri)?.use { it.readBytes() }
  }

  private fun replaceFile(directory: String, name: String, mimeType: String, bytes: ByteArray) {
    documents.findChild(directory, name)?.let {
      if (!documents.delete(it)) throw IOException("Could not replace $name")
    }
    val uri = documents.createFile(directory, mimeType, name)
    val output = documents.openOutput(uri) ?: throw IOException("Could not open $name for writing")
    output.use { it.write(bytes) }
  }

  private fun mimeType(name: String) = when (name) {
    PAGE_IMAGE, REGION_IMAGE -> "image/png"
    OCR_TEXT -> "text/plain"
    else -> "application/json"
  }

  companion object {
    private const val PREPARED_DIR = "prepared"
    private const val MANIFEST_FILE = "manifest.json"
    private const val PAGE_IMAGE = "page.png"
    private const val REGION_IMAGE = "region.png"
    private const val OCR_TEXT = "ocr.txt"
    private const val OCR_RESULT = "blocks.json"
    private val REQUIRED_FILES = listOf(MANIFEST_FILE, PAGE_IMAGE, REGION_IMAGE, OCR_TEXT, OCR_RESULT)
    private val CACHEABLE_STATUSES = setOf("ok", "partial")
  }
}

private object BitmapFactoryPreparedImageValidator : PreparedImageValidator {
  override fun dimensions(png: ByteArray, fileName: String): PreparedImageDimensions? {
    if (png.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeStream(ByteArrayInputStream(png), null, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType != "image/png") return null
    if (bounds.outWidth.toLong() * bounds.outHeight > MAX_IMAGE_PIXELS) return null
    val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
    return try {
      if (bitmap.width != bounds.outWidth || bitmap.height != bounds.outHeight) return null
      PreparedImageDimensions(bitmap.width, bitmap.height)
    } finally {
      bitmap.recycle()
    }
  }

  private const val MAX_IMAGE_PIXELS = 16L * 1024 * 1024
}
