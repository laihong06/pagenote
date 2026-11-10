package io.pagenote.android

import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderCanvasFocusTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  @Test fun focusHidesNotesAndRestoresUnsavedDraft() {
    val name = "Canvas focus"
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    val manifest = """{"format":"pagenote-project","version":1,"id":"${"f".repeat(64)}","name":"$name","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}"""
    resolver.call(ExistingProjectSafTestProvider.AUTHORITY, ExistingProjectSafTestProvider.METHOD_INSTALL, null, Bundle().apply {
      putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest.toByteArray())
      putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, minimalReaderTestPdf())
      putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, "Original".toByteArray())
      putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
    })
    openFixtureProjectFromBookshelf(compose, existingProjectTestTreeUri(), name)
    val titleWidth = compose.onNodeWithTag("reader_project_title").fetchSemanticsNode().boundsInRoot.width
    assertTrue("Reader title must remain visible", titleWidth / compose.activity.resources.displayMetrics.density >= 24f)
    val session = ViewModelProvider(compose.activity)[ReaderSessionViewModel::class.java]
    compose.runOnIdle { session.setNoteDraft("unsaved canvas focus draft") }
    compose.onNodeWithTag("reader_canvas_focus").performSemanticsAction(SemanticsActions.OnClick)
    compose.onNodeWithTag("note_tab_preview").assertIsNotDisplayed()
    compose.onNodeWithTag("pdf_ink_canvas").assertExists()
    compose.onNodeWithTag("reader_canvas_focus").performSemanticsAction(SemanticsActions.OnClick)
    compose.onNodeWithTag("note_tab_preview").assertExists()
    assertEquals("unsaved canvas focus draft", session.noteDraft.value)
  }
}
