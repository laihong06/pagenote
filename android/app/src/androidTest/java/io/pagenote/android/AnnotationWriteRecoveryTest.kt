package io.pagenote.android

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.feature.pen.PenPoint
import io.pagenote.feature.pen.PenSide
import io.pagenote.feature.pen.PenStroke
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AnnotationWriteRecoveryTest {
  private val resolver get() = InstrumentationRegistry.getInstrumentation().context.contentResolver
  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )
  private val sourceUri: Uri
    get() = DocumentsContract.buildDocumentUriUsingTree(treeUri, ExistingProjectSafTestProvider.SRC_ID)
  private val annotationsUri: Uri
    get() = DocumentsContract.buildDocumentUriUsingTree(treeUri, ExistingProjectSafTestProvider.ANNOTATIONS_ID)

  @Test
  fun annotationWriteRestoresOriginalJsonAfterPartialProviderFailure() {
    installFixture()
    val original = readAnnotations()
    val store = ProjectAnnotationStore(resolver, sourceUri, annotationsUri)
    configureAnnotationWriteFailures(1)

    assertThrows(IOException::class.java) {
      store.writePage("document", 1, JsonArray(listOf(Json.parseToJsonElement(ANNOTATION_ITEM))))
    }

    assertEquals(original, readAnnotations())
  }

  @Test
  fun penWriteRestoresOriginalJsonAfterPartialProviderFailure() {
    installFixture()
    val original = readAnnotations()
    val store = ProjectPenStore(resolver, sourceUri, annotationsUri)
    configureAnnotationWriteFailures(1)

    assertThrows(IOException::class.java) {
      store.writePage(
        PenSide.DOCUMENT,
        1,
        listOf(PenStroke("pen", "#336699", 2.0, listOf(PenPoint(12f, 34f, 1f)))),
      )
    }

    assertEquals(original, readAnnotations())
  }

  @Test
  fun deleteRestoresOriginalJsonAfterPartialProviderFailure() {
    installFixture()
    val original = readAnnotations()
    val store = ProjectAnnotationStore(resolver, sourceUri, annotationsUri)
    configureAnnotationWriteFailures(1)

    assertThrows(IOException::class.java) {
      store.deletePage("document", 2)
    }

    assertEquals(original, readAnnotations())
  }

  @Test
  fun failedRollbackRetainsAndReportsOriginalAnnotationJson() {
    installFixture()
    val original = readAnnotations()
    val store = ProjectAnnotationStore(resolver, sourceUri, annotationsUri)
    configureAnnotationWriteFailures(2)

    val error = assertThrows(NoteWriteRecoveryException::class.java) {
      store.writePage("document", 1, JsonArray(listOf(Json.parseToJsonElement(ANNOTATION_ITEM))))
    }

    assertTrue(!error.rollbackSucceeded)
    val recoveryName = requireNotNull(error.recoveryCopyName)
    assertEquals(original, readRecoveryCopy(recoveryName))
  }

  @Test
  fun successfulWriteReportsRecoveryCopyWhenProviderCannotDeleteIt() {
    installFixture()
    val original = readAnnotations()
    val store = ProjectAnnotationStore(resolver, sourceUri, annotationsUri)
    resolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_CONFIGURE_RECOVERY_DELETE_FAILURE,
      null,
      Bundle().apply { putInt(ExistingProjectSafTestProvider.EXTRA_FAILURES, 1) },
    )

    assertTrue(store.writePage("document", 1, JsonArray(listOf(Json.parseToJsonElement(ANNOTATION_ITEM)))))

    val recoveryName = recoveryCopyNames().single()
    assertTrue(recoveryName.startsWith("pagenote-recovery-"))
    assertEquals(recoveryName, store.lastRetainedRecoveryCopyName)
    assertEquals(original, readRecoveryCopy(recoveryName))
    assertEquals(1, store.readPage("document", 2).size)
    assertTrue(readAnnotations().contains("\"keep\":true"))
  }

  private fun installFixture() {
    resolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, "{}".toByteArray())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, byteArrayOf())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, "".toByteArray())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
      },
    )
  }

  private fun configureAnnotationWriteFailures(count: Int) {
    resolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_CONFIGURE_ANNOTATION_WRITE_FAILURE,
      null,
      Bundle().apply { putInt(ExistingProjectSafTestProvider.EXTRA_FAILURES, count) },
    )
  }

  private fun readAnnotations(): String = resolver.openInputStream(annotationsUri)?.bufferedReader()?.use {
    it.readText()
  } ?: error("annotation JSON should be readable")

  private fun readRecoveryCopy(name: String): String {
    val uri = DocumentsContract.buildDocumentUriUsingTree(
      treeUri,
      "${ExistingProjectSafTestProvider.SRC_ID}/$name",
    )
    return resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
      ?: error("recovery JSON should be readable")
  }

  private fun recoveryCopyNames(): List<String> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
      treeUri,
      ExistingProjectSafTestProvider.SRC_ID,
    )
    return resolver.query(
      childrenUri,
      arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
      null,
      null,
      null,
    )!!.use { cursor ->
      val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      buildList {
        while (cursor.moveToNext()) {
          val name = cursor.getString(nameColumn)
          if (name.startsWith("pagenote-recovery-")) add(name)
        }
      }
    }
  }

  private companion object {
    const val ANNOTATION_ITEM = """{"type":"line","color":"#112233","width":2,"points":[[1,2]]}"""
  }
}
