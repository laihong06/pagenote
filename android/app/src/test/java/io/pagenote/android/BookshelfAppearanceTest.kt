package io.pagenote.android

import org.junit.Assert.*
import org.junit.Test

class BookshelfAppearanceTest {
  @Test fun appearanceRoundTripsWithoutChangingProjectIdentity() {
    val appearance = BookAppearance("My notes", "image", "cover-1.png", 3, "rose")
    assertEquals(appearance, BookAppearance.decode(appearance.encode()))
    assertEquals("My notes", appearance.titleOr("Original"))
  }

  @Test fun unsafeOrUnknownSidecarsDoNotBecomeAssetPaths() {
    for (asset in listOf("../secret.png", "assets/cover.png", "/tmp/cover.png", "a\\b.png")) {
      assertThrows(IllegalArgumentException::class.java) {
        BookAppearance.decode("""{"version":1,"cover":"image","asset":"$asset"}""")
      }
    }
    assertThrows(IllegalArgumentException::class.java) { BookAppearance.decode("""{"version":2}""") }
    assertThrows(IllegalArgumentException::class.java) { BookAppearance.decode("""{"version":1,"page":-1}""") }
    assertEquals(BookAppearance(), BookAppearance.decode("""{"version":1,"future":"ignored"}"""))
  }

  @Test fun defaultCoverAndLocalSearchPolicy() {
    assertEquals("pdf", BookAppearance().resolvedCover("pdf"))
    assertEquals("paper", BookAppearance().resolvedCover("md"))
    assertEquals("paper", BookAppearance(cover = "paper").resolvedCover("pdf"))
    assertTrue(bookshelfMatches("模式识别 Notes", " NOTES "))
    assertFalse(bookshelfMatches("模式识别", "PDF"))
  }
}
