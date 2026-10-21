package io.pagenote.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopParityPlaceholdersTest {
  @Test
  fun pendingPlaceholdersAreOfficeEditAndSaveReturnOnly() {
    val main = File("src/main/java/io/pagenote/android/MainActivity.kt").readText()
    val pendingCalls =
      Regex("pendingCommand\\(\\s*\"([a-z_0-9]+)\"").findAll(main).map { it.groupValues[1] }.toSet()
    assertEquals(setOf("office_edit", "save_return"), pendingCalls)
  }
}
