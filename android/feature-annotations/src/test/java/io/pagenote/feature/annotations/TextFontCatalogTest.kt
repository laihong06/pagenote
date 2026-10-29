package io.pagenote.feature.annotations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port batch 4: text-tool font catalog (font loader data layer). */
class TextFontCatalogTest {
  @Test
  fun catalogIdsAreUniqueAndDefaultEntryExists() {
    val ids = TextFontCatalog.FONTS.map(TextFont::id)
    assertEquals(ids.size, ids.toSet().size)
    assertTrue(ids.size >= 4)
    assertNotNull(TextFontCatalog.byId(TextFontCatalog.DEFAULT_FONT_ID))
    assertEquals(TextFontCatalog.DEFAULT_FONT_ID, TextFontCatalog.DEFAULT.id)
  }

  @Test
  fun everyEntryCarriesAndroidFamilyAndCssStack() {
    TextFontCatalog.FONTS.forEach { font ->
      assertTrue(font.androidFamily.isNotBlank())
      assertTrue(font.cssStack.isNotBlank())
      assertTrue(font.labelZh.isNotBlank())
      assertTrue(font.labelEn.isNotBlank())
    }
  }

  @Test
  fun unknownIdsAreRejectedButResolveToTheDefault() {
    assertNull(TextFontCatalog.byId("Segoe UI"))
    assertFalse(TextFontCatalog.isValidFontId("not-a-font"))
    // Degradation mirrors the shape extension field policy (D5).
    assertEquals(TextFontCatalog.DEFAULT.androidFamily, TextFontCatalog.androidFamilyFor("not-a-font"))
    assertEquals(TextFontCatalog.DEFAULT.androidFamily, TextFontCatalog.androidFamilyFor(null))
    assertEquals(TextFontCatalog.DEFAULT.cssStack, TextFontCatalog.cssStackFor(null))
    assertEquals(TextFontCatalog.DEFAULT.cssStack, TextFontCatalog.cssStackFor("unknown"))
  }

  @Test
  fun knownIdsResolveToTheirOwnEntries() {
    TextFontCatalog.FONTS.forEach { font ->
      assertEquals(font, TextFontCatalog.byId(font.id))
      assertEquals(font.androidFamily, TextFontCatalog.androidFamilyFor(font.id))
      assertEquals(font.cssStack, TextFontCatalog.cssStackFor(font.id))
      assertEquals(font.labelZh to font.labelEn, TextFontCatalog.label(font.id))
    }
    assertNull(TextFontCatalog.label("unknown"))
  }
}
