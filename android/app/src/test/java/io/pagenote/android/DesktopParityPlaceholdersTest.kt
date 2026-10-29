package io.pagenote.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopParityPlaceholdersTest {
  @Test
  fun noPendingPlaceholdersRemainInMenuSections() {
    // task 36 本地引擎切片接线后，office_edit / save_return 已是实命令
    // （OfficePdfEngine 边界 + OfficeWriteBack），`pendingCommand()` 占位已清除。
    val main = File("src/main/java/io/pagenote/android/MainActivity.kt").readText()
    val pendingCalls =
      Regex("pendingCommand\\(\\s*\"([a-z_0-9]+)\"").findAll(main).map { it.groupValues[1] }.toSet()
    assertEquals(emptySet<String>(), pendingCalls)
    assertEquals(false, main.contains("fun pendingCommand("))
  }
}
