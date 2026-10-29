package io.pagenote.android

import io.pagenote.core.project.OfficeDocumentFormat
import io.pagenote.core.project.OfficePdfConversionResult
import io.pagenote.core.project.OfficePdfEngine
import io.pagenote.core.project.OfficePdfEngineException
import io.pagenote.core.project.OfficeSourceRevision
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfficePdfConversionServiceTest {
  private lateinit var workingDirectory: File
  private val source = "docx-source-bytes".toByteArray()
  private val revised = "docx-source-bytes-v2".toByteArray()
  private val pdfV1 = "%PDF-1.4 v1".toByteArray()
  private val pdfV2 = "%PDF-1.4 v2".toByteArray()

  private val engine = object : OfficePdfEngine {
    override val engineId = "libreoffice-kit"
    override val engineRevision = "a27b66ca"
    override var exportSettingsVersion = 1
    override val supportedFormats = setOf(OfficeDocumentFormat.DOCX, OfficeDocumentFormat.PPTX)
    var nextPdf: ByteArray = pdfV1
    var fail = false

    override fun convertToPdf(source: java.io.InputStream): ByteArray {
      if (fail) throw OfficePdfEngineException("renderer crashed")
      source.readBytes()
      return nextPdf
    }
  }

  @Before
  fun setUp() {
    workingDirectory = Files.createTempDirectory("office-preview-test").toFile()
  }

  @After
  fun tearDown() {
    workingDirectory.deleteRecursively()
  }

  private fun revision(bytes: ByteArray) = OfficeSourceRevision.from(ByteArrayInputStream(bytes))
  private val sourceRevision = revision(source)

  private fun convert(bytes: ByteArray = source) = OfficePdfConversionService(workingDirectory)
    .convertAndCache(
      currentRevision = revision(bytes),
      format = OfficeDocumentFormat.DOCX,
      engine = engine,
      openSource = { ByteArrayInputStream(bytes) },
    )

  @Test
  fun `without engine no preview file is written`() {
    val outcome = OfficePdfConversionService(workingDirectory).convertAndCache(
      currentRevision = sourceRevision,
      format = OfficeDocumentFormat.DOCX,
      engine = null,
      openSource = { ByteArrayInputStream(source) },
    )
    val unavailable = outcome as OfficePdfConversionService.Outcome.Unavailable
    assertTrue(unavailable.result is OfficePdfConversionResult.EngineUnavailable)
    assertEquals(0, workingDirectory.listFiles()!!.size)
  }

  @Test
  fun `successful conversion writes a keyed preview and reuses it`() {
    val outcome = convert() as OfficePdfConversionService.Outcome.PreviewReady
    assertEquals(pdfV1.contentEquals(outcome.file.readBytes()), true)
    assertTrue(outcome.file.name.startsWith(OfficePdfConversionService.PREVIEW_PREFIX))
    assertEquals(1, workingDirectory.listFiles()!!.count { it.extension == "pdf" })

    // 同一源/引擎：直接复用缓存，不再转换（引擎输出换成 v2 也不读取）。
    engine.nextPdf = pdfV2
    val reused = convert() as OfficePdfConversionService.Outcome.PreviewReady
    assertEquals(outcome.file.absolutePath, reused.file.absolutePath)
    assertTrue(pdfV1.contentEquals(reused.file.readBytes()))
  }

  @Test
  fun `source revision change invalidates the stale preview`() {
    val first = convert() as OfficePdfConversionService.Outcome.PreviewReady
    engine.nextPdf = pdfV2
    val second = convert(revised) as OfficePdfConversionService.Outcome.PreviewReady
    assertFalse(first.file.exists())
    assertTrue(pdfV2.contentEquals(second.file.readBytes()))
  }

  @Test
  fun `engine revision or export settings change rebuilds the preview`() {
    val first = convert() as OfficePdfConversionService.Outcome.PreviewReady
    engine.nextPdf = pdfV2
    engine.exportSettingsVersion = 2
    val second = convert() as OfficePdfConversionService.Outcome.PreviewReady
    assertFalse(first.file.exists())
    assertTrue(pdfV2.contentEquals(second.file.readBytes()))
  }

  @Test
  fun `failed conversion keeps no preview and reports the engine reason`() {
    val outcome = OfficePdfConversionService(workingDirectory).convertAndCache(
      currentRevision = sourceRevision,
      format = OfficeDocumentFormat.DOCX,
      engine = engine.apply { fail = true },
      openSource = { ByteArrayInputStream(source) },
    )
    val unavailable = outcome as OfficePdfConversionService.Outcome.Unavailable
    assertEquals("renderer crashed", (unavailable.result as OfficePdfConversionResult.Failed).reason)
    assertEquals(0, workingDirectory.listFiles()!!.count { it.extension == "pdf" })
  }

  @Test
  fun `failed conversion drops a stale preview instead of passing it off as current`() {
    val stale = convert() as OfficePdfConversionService.Outcome.PreviewReady
    engine.fail = true
    val outcome = OfficePdfConversionService(workingDirectory).convertAndCache(
      currentRevision = revision(revised),
      format = OfficeDocumentFormat.DOCX,
      engine = engine,
      openSource = { ByteArrayInputStream(revised) },
    )
    assertTrue(outcome is OfficePdfConversionService.Outcome.Unavailable)
    assertFalse(stale.file.exists())
  }

  @Test
  fun `unproven format never reaches the engine and writes nothing`() {
    val outcome = OfficePdfConversionService(workingDirectory).convertAndCache(
      currentRevision = revision(source),
      format = OfficeDocumentFormat.DOC,
      engine = engine,
      openSource = { ByteArrayInputStream(source) },
    )
    assertTrue(
      (outcome as OfficePdfConversionService.Outcome.Unavailable).result
        is OfficePdfConversionResult.UnsupportedFormat,
    )
    assertEquals(0, workingDirectory.listFiles()!!.size)
  }

  @Test
  fun `cachedPreview only returns files for the current key`() {
    val service = OfficePdfConversionService(workingDirectory)
    val ready = service.convertAndCache(
      currentRevision = sourceRevision,
      format = OfficeDocumentFormat.DOCX,
      engine = engine,
      openSource = { ByteArrayInputStream(source) },
    ) as OfficePdfConversionService.Outcome.PreviewReady
    assertEquals(ready.file.absolutePath, service.cachedPreview(sourceRevision, OfficeDocumentFormat.DOCX, engine)?.absolutePath)
    assertNull(service.cachedPreview(revision(revised), OfficeDocumentFormat.DOCX, engine))
    assertNull(service.cachedPreview(sourceRevision, OfficeDocumentFormat.PPTX, engine))
    assertNull(service.cachedPreview(sourceRevision, OfficeDocumentFormat.DOCX, null))
  }
}

