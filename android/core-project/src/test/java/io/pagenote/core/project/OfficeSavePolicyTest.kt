package io.pagenote.core.project

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OfficeSavePolicyTest {
  @Test
  fun revisionHashesAllSourceBytes() {
    val revision = OfficeSourceRevision.from(ByteArrayInputStream("abc".toByteArray()))

    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", revision.sha256)
    assertEquals(3L, revision.sizeBytes)
    assertNotEquals(revision, OfficeSourceRevision.from(ByteArrayInputStream("abd".toByteArray())))
  }

  @Test
  fun cleanEditorDoesNotNeedWriteBack() {
    val revision = revision("same")

    assertEquals(
      OfficeSaveReadiness.NO_CHANGES,
      OfficeSavePolicy.evaluate(revision, revision, editorDirty = false, destinationWritable = false, recoveryCopyReady = false),
    )
  }

  @Test
  fun externalSourceChangeBlocksWriteBack() {
    assertEquals(
      OfficeSaveReadiness.SOURCE_CONFLICT,
      OfficeSavePolicy.evaluate(revision("opened"), revision("changed"), editorDirty = true, destinationWritable = true, recoveryCopyReady = true),
    )
  }

  @Test
  fun readOnlyDestinationBlocksWriteBack() {
    val revision = revision("same")

    assertEquals(
      OfficeSaveReadiness.DESTINATION_UNAVAILABLE,
      OfficeSavePolicy.evaluate(revision, revision, editorDirty = true, destinationWritable = false, recoveryCopyReady = true),
    )
  }

  @Test
  fun missingRecoveryCopyBlocksWriteBack() {
    val revision = revision("same")

    assertEquals(
      OfficeSaveReadiness.RECOVERY_REQUIRED,
      OfficeSavePolicy.evaluate(revision, revision, editorDirty = true, destinationWritable = true, recoveryCopyReady = false),
    )
  }

  @Test
  fun unchangedWritableSourceWithRecoveryIsReady() {
    val revision = revision("same")

    assertEquals(
      OfficeSaveReadiness.READY,
      OfficeSavePolicy.evaluate(revision, revision, editorDirty = true, destinationWritable = true, recoveryCopyReady = true),
    )
  }

  private fun revision(text: String) = OfficeSourceRevision.from(ByteArrayInputStream(text.toByteArray()))
}
