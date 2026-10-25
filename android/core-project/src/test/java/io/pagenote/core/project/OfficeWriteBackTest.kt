package io.pagenote.core.project

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeWriteBackTest {
  private fun revision(bytes: ByteArray): OfficeSourceRevision =
    OfficeSourceRevision.from(ByteArrayInputStream(bytes))

  private val opening = revision("original-docx".toByteArray())

  @Test
  fun `clean editor never touches source or destination`() {
    val plan = OfficeWriteBack.plan(
      openingRevision = opening,
      currentRevision = opening,
      editorDirty = false,
      workingCopyBytes = null,
      destinationWritable = false,
      recoveryCopyReady = false,
      recoveryNameBase = "lecture.docx",
    )
    assertEquals(OfficeWriteBackPlan.NoChanges, plan)
  }

  @Test
  fun `changed source conflicts and blocks the write`() {
    val plan = OfficeWriteBack.plan(
      openingRevision = opening,
      currentRevision = revision("changed-elsewhere".toByteArray()),
      editorDirty = true,
      workingCopyBytes = "edited".toByteArray(),
      destinationWritable = true,
      recoveryCopyReady = true,
      recoveryNameBase = "lecture.docx",
    )
    assertEquals(OfficeWriteBackPlan.Blocked(OfficeSaveReadiness.SOURCE_CONFLICT), plan)
  }

  @Test
  fun `read-only destination and missing recovery copy block separately`() {
    val blockedDestination = OfficeWriteBack.plan(
      openingRevision = opening,
      currentRevision = opening,
      editorDirty = true,
      workingCopyBytes = "edited".toByteArray(),
      destinationWritable = false,
      recoveryCopyReady = true,
      recoveryNameBase = "lecture.docx",
    )
    assertEquals(
      OfficeWriteBackPlan.Blocked(OfficeSaveReadiness.DESTINATION_UNAVAILABLE),
      blockedDestination,
    )
    val blockedRecovery = OfficeWriteBack.plan(
      openingRevision = opening,
      currentRevision = opening,
      editorDirty = true,
      workingCopyBytes = "edited".toByteArray(),
      destinationWritable = true,
      recoveryCopyReady = false,
      recoveryNameBase = "lecture.docx",
    )
    assertEquals(
      OfficeWriteBackPlan.Blocked(OfficeSaveReadiness.RECOVERY_REQUIRED),
      blockedRecovery,
    )
  }

  @Test
  fun `ready plan carries the working bytes and a recovery copy name`() {
    val working = "edited-docx".toByteArray()
    val plan = OfficeWriteBack.plan(
      openingRevision = opening,
      currentRevision = opening,
      editorDirty = true,
      workingCopyBytes = working,
      destinationWritable = true,
      recoveryCopyReady = true,
      recoveryNameBase = "lecture.docx",
    )
    val write = plan as OfficeWriteBackPlan.Write
    assertTrue(write.sourceBytes.contentEquals(working))
    assertEquals("lecture.recovery.docx", write.recoveryCopyName)
  }

  @Test
  fun `recovery copy names preserve extension and handle extensionless names`() {
    assertEquals("lecture.recovery.docx", OfficeWriteBack.recoveryCopyName("lecture.docx"))
    assertEquals("a.recovery.pptx", OfficeWriteBack.recoveryCopyName("a.pptx"))
    assertEquals("notes.recovery", OfficeWriteBack.recoveryCopyName("notes"))
    // 以点开头的隐藏文件视作无扩展名。
    assertEquals(".docx.recovery", OfficeWriteBack.recoveryCopyName(".docx"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `ready plan without working bytes is rejected`() {
    OfficeWriteBack.plan(
      openingRevision = opening,
      currentRevision = opening,
      editorDirty = true,
      workingCopyBytes = null,
      destinationWritable = true,
      recoveryCopyReady = true,
      recoveryNameBase = "lecture.docx",
    )
  }
}
