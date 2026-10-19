package io.pagenote.android

import java.io.IOException

internal sealed interface RecoveryCopyPreparation<out T> {
  data object Unsupported : RecoveryCopyPreparation<Nothing>
  data class Available<T>(val copy: T) : RecoveryCopyPreparation<T>

  companion object {
    fun <P, T> fromProvider(
      findParent: () -> P?,
      supportsCreate: (P) -> Boolean,
      create: (P) -> T?,
    ): RecoveryCopyPreparation<T> {
      val parent = findParent() ?: return Unsupported
      if (!supportsCreate(parent)) return Unsupported
      return Available(create(parent) ?: throw IOException("SAF provider could not create a recovery copy"))
    }
  }
}

internal class RecoverableDocumentWrite<T>(
  private val readTarget: () -> String,
  private val overwriteTarget: (String) -> Unit,
  private val createBackup: (String) -> RecoveryCopyPreparation<T>,
  private val deleteBackup: (T) -> Unit,
  private val backupName: (T) -> String,
  private val documentLabel: String = "project note",
) {
  fun write(updated: String, expectedOriginal: String): String? {
    val original = readTarget()
    if (original != expectedOriginal) throw NoteDocumentConflictException()

    val backup = when (val preparation = createBackup(original)) {
      RecoveryCopyPreparation.Unsupported -> null
      is RecoveryCopyPreparation.Available -> preparation.copy
    }
    val latest = try {
      readTarget()
    } catch (error: Exception) {
      val retained = cleanupBackup(backup)
      if (retained != null) throw NoteRecoveryCopyRetainedException(retained, error, documentLabel)
      throw error
    }
    if (latest != expectedOriginal) {
      val retained = cleanupBackup(backup)
      throw NoteDocumentConflictException(retained, documentLabel)
    }

    try {
      overwriteTarget(updated)
    } catch (writeFailure: Exception) {
      val rollbackFailure = runCatching { overwriteTarget(original) }.exceptionOrNull()
      if (rollbackFailure == null) {
        val retained = cleanupBackup(backup)
        if (retained != null) {
          throw NoteWriteRecoveryException(retained, true, writeFailure, documentLabel)
        }
        throw writeFailure
      }

      writeFailure.addSuppressed(rollbackFailure)
      throw NoteWriteRecoveryException(backup?.let(backupName), false, writeFailure, documentLabel)
    }
    return cleanupBackup(backup)
  }

  private fun cleanupBackup(backup: T?): String? {
    if (backup == null) return null
    return runCatching { deleteBackup(backup) }.exceptionOrNull()?.let { backupName(backup) }
  }
}

class NoteDocumentConflictException(
  val recoveryCopyName: String? = null,
  documentLabel: String = "project note",
) : IOException(
  if (recoveryCopyName == null) {
    "$documentLabel changed during write preparation"
  } else {
    "$documentLabel changed during write preparation; recovery copy remains: $recoveryCopyName"
  },
)

class NoteRecoveryCopyRetainedException(
  val recoveryCopyName: String,
  cause: Throwable,
  documentLabel: String = "project note",
) : IOException("recovery copy for $documentLabel remains in the project folder: $recoveryCopyName", cause)

class NoteWriteRecoveryException(
  val recoveryCopyName: String?,
  val rollbackSucceeded: Boolean,
  cause: Throwable,
  documentLabel: String = "note",
) : IOException(
  if (rollbackSucceeded) {
    "$documentLabel write failed and original content was restored; recovery copy remains: $recoveryCopyName"
  } else if (recoveryCopyName == null) {
    "$documentLabel write failed and could not be restored; no recovery copy is available"
  } else {
    "$documentLabel write failed and could not be restored; recovery copy: $recoveryCopyName"
  },
  cause,
)
