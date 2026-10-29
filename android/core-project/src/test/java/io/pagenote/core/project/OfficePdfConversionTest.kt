package io.pagenote.core.project

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficePdfConversionTest {
  private fun revision(bytes: ByteArray): OfficeSourceRevision =
    OfficeSourceRevision.from(ByteArrayInputStream(bytes))

  private class FakeEngine(
    override val engineId: String = "libreoffice-kit",
    override val engineRevision: String = "a27b66ca",
    override val exportSettingsVersion: Int = 1,
    override val supportedFormats: Set<OfficeDocumentFormat> =
      setOf(OfficeDocumentFormat.DOCX, OfficeDocumentFormat.PPTX),
    private val pdf: ByteArray = FAKE_PDF,
    private val error: OfficePdfEngineException? = null,
  ) : OfficePdfEngine {
    var consumedStreams = 0
      private set

    override fun convertToPdf(source: InputStream): ByteArray {
      consumedStreams += 1
      error?.let { throw it }
      source.readBytes() // 消费完整源流
      return pdf
    }
  }

  @Test
  fun `without engine the gate stays closed and no preview is fabricated`() {
    val source = "docx-bytes".toByteArray()
    val result = OfficePdfConversion.convert(
      sourceRevision = revision(source),
      format = OfficeDocumentFormat.DOCX,
      engine = null,
      openSource = { ByteArrayInputStream(source) },
    )
    assertTrue(result is OfficePdfConversionResult.EngineUnavailable)
    assertEquals(OfficeDocumentFormat.DOCX, (result as OfficePdfConversionResult.EngineUnavailable).format)
  }

  @Test
  fun `unproven format is rejected even when an engine is bound`() {
    val source = "ppt-bytes".toByteArray()
    val engine = FakeEngine() // 只证明 DOCX/PPTX
    val result = OfficePdfConversion.convert(
      sourceRevision = revision(source),
      format = OfficeDocumentFormat.PPT,
      engine = engine,
      openSource = { ByteArrayInputStream(source) },
    )
    assertTrue(result is OfficePdfConversionResult.UnsupportedFormat)
    assertEquals(0, engine.consumedStreams)
  }

  @Test
  fun `success produces pdf bytes and a cache key bound to source and engine`() {
    val source = "docx-bytes".toByteArray()
    val engine = FakeEngine()
    val result = OfficePdfConversion.convert(
      sourceRevision = revision(source),
      format = OfficeDocumentFormat.DOCX,
      engine = engine,
      openSource = { ByteArrayInputStream(source) },
    )
    val ready = result as OfficePdfConversionResult.Ready
    assertEquals(FAKE_PDF.contentEquals(ready.pdf), true)
    assertEquals(
      OfficePreviewCacheKey(
        sourceSha256 = revision(source).sha256,
        format = OfficeDocumentFormat.DOCX,
        engineId = "libreoffice-kit",
        engineRevision = "a27b66ca",
        exportSettingsVersion = 1,
      ),
      ready.cacheKey,
    )
    assertEquals(1, engine.consumedStreams)
  }

  @Test
  fun `engine failure keeps the source and reports the reason without a preview`() {
    val source = "docx-bytes".toByteArray()
    val engine = FakeEngine(error = OfficePdfEngineException("renderer crashed", IOException("boom")))
    val result = OfficePdfConversion.convert(
      sourceRevision = revision(source),
      format = OfficeDocumentFormat.DOCX,
      engine = engine,
      openSource = { ByteArrayInputStream(source) },
    )
    val failed = result as OfficePdfConversionResult.Failed
    assertEquals("renderer crashed", failed.reason)
    assertTrue(failed.reason.length <= OfficePdfConversion.MAX_REASON_LENGTH)
  }

  @Test
  fun `cache key invalidates on every component change`() {
    val source = "docx-bytes".toByteArray()
    val base = OfficePreviewCacheKey(
      sourceSha256 = revision(source).sha256,
      format = OfficeDocumentFormat.DOCX,
      engineId = "libreoffice-kit",
      engineRevision = "a27b66ca",
      exportSettingsVersion = 1,
    )
    val engine = FakeEngine()
    val currentRevision = revision(source)

    assertTrue(OfficePreviewCachePolicy.isUsable(base, currentRevision, OfficeDocumentFormat.DOCX, engine))
    val changedSource = "docx-bytes-2".toByteArray()
    assertFalse(
      OfficePreviewCachePolicy.isUsable(base, revision(changedSource), OfficeDocumentFormat.DOCX, engine),
    )
    assertFalse(
      OfficePreviewCachePolicy.isUsable(base, currentRevision, OfficeDocumentFormat.PPTX, engine),
    )
    assertFalse(
      OfficePreviewCachePolicy.isUsable(
        base.copy(engineRevision = "deadbeef"),
        currentRevision,
        OfficeDocumentFormat.DOCX,
        engine,
      ),
    )
    assertFalse(
      OfficePreviewCachePolicy.isUsable(
        base.copy(exportSettingsVersion = 2),
        currentRevision,
        OfficeDocumentFormat.DOCX,
        engine,
      ),
    )
    assertFalse(
      OfficePreviewCachePolicy.isUsable(
        base.copy(engineId = "other-engine"),
        currentRevision,
        OfficeDocumentFormat.DOCX,
        engine,
      ),
    )
    // 引擎未接入时任何缓存键都不可用。
    assertFalse(OfficePreviewCachePolicy.isUsable(base, currentRevision, OfficeDocumentFormat.DOCX, null))
    assertFalse(OfficePreviewCachePolicy.isUsable(null, currentRevision, OfficeDocumentFormat.DOCX, engine))
  }

  @Test
  fun `format detection covers the four desktop extensions only`() {
    assertEquals(OfficeDocumentFormat.DOC, OfficeDocumentFormat.fromExtension("DOC"))
    assertEquals(OfficeDocumentFormat.DOCX, OfficeDocumentFormat.fromExtension(".docx"))
    assertEquals(OfficeDocumentFormat.PPT, OfficeDocumentFormat.fromExtension("src/presentation.ppt"))
    assertEquals(OfficeDocumentFormat.PPTX, OfficeDocumentFormat.fromExtension("PPTX"))
    assertEquals(null, OfficeDocumentFormat.fromExtension("pdf"))
    assertEquals(null, OfficeDocumentFormat.fromExtension(null))
    assertEquals(null, OfficeDocumentFormat.fromExtension(""))
  }

  private companion object {
    val FAKE_PDF = "%PDF-1.4 fake".toByteArray()
  }
}
