package io.pagenote.android

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoverableDocumentWriteTest {
  @Test
  fun restoresOriginalContentAfterPartialWriteFailure() {
    var document = "original"
    var attempts = 0
    val deletedBackups = mutableListOf<String>()
    val writer = RecoverableDocumentWrite(
      readTarget = { document },
      overwriteTarget = { content ->
        attempts += 1
        if (attempts == 1) {
          document = ""
          throw IOException("provider failed after truncating")
        }
        document = content
      },
      createBackup = { RecoveryCopyPreparation.Available("recovery.md") },
      deleteBackup = deletedBackups::add,
      backupName = { it },
    )

    assertThrows(IOException::class.java) {
      writer.write(updated = "replacement", expectedOriginal = "original")
    }

    assertEquals("original", document)
    assertEquals(listOf("recovery.md"), deletedBackups)
  }

  @Test
  fun retainsRecoveryCopyWhenRollbackAlsoFails() {
    var attempts = 0
    var deletedBackups = 0
    val writer = RecoverableDocumentWrite(
      readTarget = { "original" },
      overwriteTarget = {
        attempts += 1
        throw IOException(if (attempts == 1) "update failed" else "rollback failed")
      },
      createBackup = { RecoveryCopyPreparation.Available("pagenote-recovery-123.md") },
      deleteBackup = { deletedBackups += 1 },
      backupName = { it },
    )

    val error = assertThrows(NoteWriteRecoveryException::class.java) {
      writer.write(updated = "replacement", expectedOriginal = "original")
    }

    assertEquals("pagenote-recovery-123.md", error.recoveryCopyName)
    assertEquals(false, error.rollbackSucceeded)
    assertEquals(0, deletedBackups)
    assertTrue(error.cause is IOException)
  }

  @Test
  fun writesNewContentAndRemovesBackupOnSuccess() {
    var document = "original"
    val deletedBackups = mutableListOf<String>()
    val writer = RecoverableDocumentWrite(
      readTarget = { "original" },
      overwriteTarget = { document = it },
      createBackup = { RecoveryCopyPreparation.Available("recovery.md") },
      deleteBackup = deletedBackups::add,
      backupName = { it },
    )

    assertEquals(null, writer.write(updated = "replacement", expectedOriginal = "original"))

    assertEquals("replacement", document)
    assertEquals(listOf("recovery.md"), deletedBackups)
  }

  @Test
  fun abortsIfDocumentChangesWhileRecoveryCopyIsCreated() {
    var reads = 0
    var writes = 0
    val deletedBackups = mutableListOf<String>()
    val writer = RecoverableDocumentWrite(
      readTarget = {
        reads += 1
        if (reads == 1) "original" else "external edit"
      },
      overwriteTarget = { writes += 1 },
      createBackup = { RecoveryCopyPreparation.Available("recovery.md") },
      deleteBackup = deletedBackups::add,
      backupName = { it },
    )

    assertThrows(NoteDocumentConflictException::class.java) {
      writer.write(updated = "replacement", expectedOriginal = "original")
    }

    assertEquals(0, writes)
    assertEquals(listOf("recovery.md"), deletedBackups)
  }

  @Test
  fun abortsBeforeTargetWriteWhenSupportedProviderCannotPrepareBackup() {
    var writes = 0
    val writer = RecoverableDocumentWrite(
      readTarget = { "original" },
      overwriteTarget = { writes += 1 },
      createBackup = {
        RecoveryCopyPreparation.fromProvider(
          findParent = { "parent" },
          supportsCreate = { true },
          create = { null },
        )
      },
      deleteBackup = {},
      backupName = { it },
    )

    assertThrows(IOException::class.java) {
      writer.write(updated = "replacement", expectedOriginal = "original")
    }

    assertEquals(0, writes)
  }

  @Test
  fun abortsBeforeTargetWriteWhenRecoveryParentLookupFails() {
    var writes = 0
    val writer = RecoverableDocumentWrite(
      readTarget = { "original" },
      overwriteTarget = { writes += 1 },
      createBackup = {
        RecoveryCopyPreparation.fromProvider(
          findParent = { throw IOException("parent lookup failed") },
          supportsCreate = { true },
          create = { "recovery.md" },
        )
      },
      deleteBackup = {},
      backupName = { it },
    )

    assertThrows(IOException::class.java) {
      writer.write(updated = "replacement", expectedOriginal = "original")
    }

    assertEquals(0, writes)
  }

  @Test
  fun abortsBeforeTargetWriteWhenRecoveryCapabilityQueryFails() {
    var writes = 0
    var createCalls = 0
    val writer = RecoverableDocumentWrite(
      readTarget = { "original" },
      overwriteTarget = { writes += 1 },
      createBackup = {
        RecoveryCopyPreparation.fromProvider(
          findParent = { "parent" },
          supportsCreate = { throw IOException("flags query failed") },
          create = {
            createCalls += 1
            "recovery.md"
          },
        )
      },
      deleteBackup = {},
      backupName = { it },
    )

    assertThrows(IOException::class.java) {
      writer.write(updated = "replacement", expectedOriginal = "original")
    }

    assertEquals(0, writes)
    assertEquals(0, createCalls)
  }

  @Test
  fun allowsBestEffortWriteWhenProviderExplicitlyDoesNotSupportRecoveryCopies() {
    var document = "original"
    val writer = RecoverableDocumentWrite(
      readTarget = { document },
      overwriteTarget = { document = it },
      createBackup = {
        RecoveryCopyPreparation.fromProvider(
          findParent = { "parent" },
          supportsCreate = { false },
          create = { "recovery.md" },
        )
      },
      deleteBackup = {},
      backupName = { it },
    )

    writer.write(updated = "replacement", expectedOriginal = "original")

    assertEquals("replacement", document)
  }

  @Test
  fun reportsRecoveryCopyLeftAfterSuccessfulWriteCleanupFails() {
    var document = "original"
    val writer = RecoverableDocumentWrite(
      readTarget = { if (document == "replacement") "original" else document },
      overwriteTarget = { document = it },
      createBackup = { RecoveryCopyPreparation.Available("recovery.md") },
      deleteBackup = { throw IOException("cleanup failed") },
      backupName = { it },
    )

    val retainedCopyName = writer.write(updated = "replacement", expectedOriginal = "original")

    assertEquals("replacement", document)
    assertEquals("recovery.md", retainedCopyName)
  }

  @Test
  fun reportsRecoveryCopyLeftAfterRollbackSucceedsButCleanupFails() {
    var attempts = 0
    val writer = RecoverableDocumentWrite(
      readTarget = { "original" },
      overwriteTarget = {
        attempts += 1
        if (attempts == 1) throw IOException("update failed")
      },
      createBackup = { RecoveryCopyPreparation.Available("recovery.md") },
      deleteBackup = { throw IOException("cleanup failed") },
      backupName = { it },
    )

    val error = assertThrows(NoteWriteRecoveryException::class.java) {
      writer.write(updated = "replacement", expectedOriginal = "original")
    }

    assertEquals(true, error.rollbackSucceeded)
    assertEquals("recovery.md", error.recoveryCopyName)
  }

  @Test
  fun reportsRecoveryCopyLeftWhenConflictCleanupFails() {
    var reads = 0
    val writer = RecoverableDocumentWrite(
      readTarget = { if (++reads == 1) "original" else "external edit" },
      overwriteTarget = {},
      createBackup = { RecoveryCopyPreparation.Available("recovery.md") },
      deleteBackup = { throw IOException("cleanup failed") },
      backupName = { it },
    )

    val error = assertThrows(NoteDocumentConflictException::class.java) {
      writer.write(updated = "replacement", expectedOriginal = "original")
    }

    assertEquals("recovery.md", error.recoveryCopyName)
  }

  @Test
  fun reportsRecoveryCopyLeftWhenSnapshotReadAndCleanupFail() {
    var reads = 0
    val writer = RecoverableDocumentWrite(
      readTarget = {
        if (++reads == 1) "original" else throw IOException("read failed")
      },
      overwriteTarget = {},
      createBackup = { RecoveryCopyPreparation.Available("recovery.md") },
      deleteBackup = { throw IOException("cleanup failed") },
      backupName = { it },
    )

    val error = assertThrows(NoteRecoveryCopyRetainedException::class.java) {
      writer.write(updated = "replacement", expectedOriginal = "original")
    }

    assertEquals("recovery.md", error.recoveryCopyName)
    assertTrue(error.cause is IOException)
  }
}
