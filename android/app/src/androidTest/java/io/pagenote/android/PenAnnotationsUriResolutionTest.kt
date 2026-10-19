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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PenAnnotationsUriResolutionTest {
  private val resolver get() = InstrumentationRegistry.getInstrumentation().context.contentResolver
  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )
  private val sourceUri: Uri
    get() = DocumentsContract.buildDocumentUriUsingTree(treeUri, ExistingProjectSafTestProvider.SRC_ID)

  @Test
  fun annotationsCreatedAfterOpenAreSharedByAnnotationAndPenStores() {
    installFixtureWithoutAnnotations()
    assertTrue(annotationNames().isEmpty())

    val penStore = ProjectPenStore(resolver, sourceUri, null)
    val annotationStore = ProjectAnnotationStore(resolver, sourceUri, null)
    val textAnnotation = JsonArray(listOf(Json.parseToJsonElement(
      """{"type":"rect","color":"#112233","width":3,"points":[[0,0],[100,100]]}""",
    )))
    assertTrue(annotationStore.writePage("document", 1, textAnnotation))

    penStore.writePage(
      PenSide.DOCUMENT,
      1,
      listOf(PenStroke("pen", "#336699", 2.0, listOf(PenPoint(12f, 34f, 1f)))),
    )

    assertEquals(listOf("annotations.json"), annotationNames())
    val saved = ProjectAnnotationStore(resolver, sourceUri, null).readPage("document", 1)
    assertEquals(listOf("rect", "pen"), saved.map { it.jsonObject["type"]!!.jsonPrimitive.content })
    assertEquals(textAnnotation.single(), saved.first())
  }

  @Test
  fun missingAnnotationsFileIsNotCreatedForAnEmptyPenPage() {
    installFixtureWithoutAnnotations()
    val store = ProjectPenStore(resolver, sourceUri, null)

    store.writePage(PenSide.DOCUMENT, 1, emptyList())

    assertTrue(annotationNames().isEmpty())
    assertEquals(0, createDocumentCallCount())
  }

  @Test
  fun failedAnnotationsDirectoryQueryDoesNotCreateAFile() {
    installFixtureWithoutAnnotations()
    val store = ProjectPenStore(resolver, sourceUri, null)
    resolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_CONFIGURE_CHILD_QUERY_FAILURE,
      null,
      Bundle().apply { putInt(ExistingProjectSafTestProvider.EXTRA_FAILURES, 1) },
    )

    assertThrows(IOException::class.java) {
      store.writePage(
        PenSide.DOCUMENT,
        1,
        listOf(PenStroke("pen", "#336699", 2.0, listOf(PenPoint(12f, 34f, 1f)))),
      )
    }

    assertEquals(0, createDocumentCallCount())
    assertTrue(annotationNames().isEmpty())
  }

  private fun installFixtureWithoutAnnotations() {
    resolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, "{}".toByteArray())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, byteArrayOf())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, byteArrayOf())
        putBoolean(ExistingProjectSafTestProvider.EXTRA_OMIT_REVIEWS, true)
        putBoolean(ExistingProjectSafTestProvider.EXTRA_OMIT_ANNOTATIONS, true)
      },
    )
  }

  private fun annotationNames(): List<String> {
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
          if (name.startsWith("annotations")) add(name)
        }
      }
    }
  }

  private fun createDocumentCallCount(): Int = resolver.call(
    ExistingProjectSafTestProvider.AUTHORITY,
    ExistingProjectSafTestProvider.METHOD_GET_CREATE_DOCUMENT_COUNT,
    null,
    null,
  )!!.getInt(ExistingProjectSafTestProvider.EXTRA_COUNT)
}
