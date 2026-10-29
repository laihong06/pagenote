package io.pagenote.feature.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexModelCatalogTest {
  @Test
  fun pix2texEntryIsTheSelectedDefaultWithDeclaredAssets() {
    val entry = LatexModelCatalog.DEFAULT
    assertSame(LatexModelCatalog.PIX2TEX_VIT, entry)
    assertEquals("pix2tex-vit", entry.id)
    assertEquals("MIT", entry.license)
    assertTrue(entry.encoderPath.startsWith("models/formula/pix2tex-vit/"))
    assertTrue(entry.decoderPath.startsWith("models/formula/pix2tex-vit/"))
    assertTrue(entry.tokensPath.startsWith("models/formula/pix2tex-vit/"))
  }

  @Test
  fun byIdAndValidityFollowTheCatalog() {
    assertEquals(LatexModelCatalog.PIX2TEX_VIT, LatexModelCatalog.byId("pix2tex-vit"))
    assertNotNull(LatexModelCatalog.byId(LatexModelCatalog.DEFAULT_ID))
    assertEquals(null, LatexModelCatalog.byId("mathpix"))
    assertTrue(LatexModelCatalog.isValidId("pix2tex-vit"))
    assertFalse(LatexModelCatalog.isValidId("closed-cloud-api"))
  }
}

class OcrModelCatalogTest {
  @Test
  fun bundledEntryCarriesDetAndRecAssets() {
    val entry = OcrModelCatalog.PP_OCR_BUNDLED
    assertEquals("models/det/inference.onnx", entry.detectionModelPath)
    assertEquals("models/rec/inference.onnx", entry.recognitionModelPath)
    assertEquals("models/rec/inference.yml", entry.recognitionConfigPath)
    assertEquals(setOf(OcrLanguage.CHINESE, OcrLanguage.ENGLISH), entry.languages)
    assertSame(OcrModelCatalog.DEFAULT, OcrModelCatalog.byId(entry.id))
  }

  @Test
  fun languageResolutionUsesSupportingModelAndFallsBack() {
    assertSame(OcrModelCatalog.DEFAULT, OcrModelCatalog.resolveForLanguage("zh"))
    assertSame(OcrModelCatalog.DEFAULT, OcrModelCatalog.resolveForLanguage("en"))
    assertSame(OcrModelCatalog.DEFAULT, OcrModelCatalog.resolveForLanguage("system"))
    // 未知语言回落默认模型而不是失败。
    assertSame(OcrModelCatalog.DEFAULT, OcrModelCatalog.resolveForLanguage("fr"))
  }

  @Test
  fun recognitionSettingsSanitizeUnknownValues() {
    val sanitized = OcrRecognitionSettings(language = "klingon", latexModelId = "mathpix").sanitized()
    assertEquals("system", sanitized.language)
    assertEquals(LatexModelCatalog.DEFAULT_ID, sanitized.latexModelId)
    val kept = OcrRecognitionSettings(language = "en", latexModelId = "pix2tex-vit").sanitized()
    assertEquals("en", kept.language)
    assertEquals("pix2tex-vit", kept.latexModelId)
  }
}
