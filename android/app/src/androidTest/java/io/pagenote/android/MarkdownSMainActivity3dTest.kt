package io.pagenote.android

import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.MarkdownSBlockParser
import io.pagenote.feature.notes.NotePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MarkdownSMainActivity3dTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  @Test
  fun saved3dStateSurvivesLeavingReaderAndActivityRecreation() {
    val projectName = "3D acceptance ${UUID.randomUUID()}"
    val page = "前文\n\n```markdowns\n" +
      "{\"version\":1,\"id\":\"3d-reopen\",\"kind\":\"genetic-lab\",\"genes\":[1,0,1,0,1,0,1,0,1,0,1,0]}\n" +
      "```\n\n后文"
    val treeUri = existingProjectTestTreeUri()
    val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    val manifest = """{"format":"pagenote-project","version":1,"id":"${"e".repeat(64)}","name":"$projectName","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}"""
    resolver.call(ExistingProjectSafTestProvider.AUTHORITY, ExistingProjectSafTestProvider.METHOD_INSTALL, null, Bundle().apply {
      putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest.toByteArray())
      putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, minimalReaderTestPdf())
      putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, PageNotes.join(listOf(page, "第二页保留")).toByteArray())
      putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
    })

    openFixtureProjectFromBookshelf(compose, treeUri, projectName)
    val session = ViewModelProvider(compose.activity)[ReaderSessionViewModel::class.java]
    compose.waitUntil(15_000) { session.noteDraft.value.contains("3d-reopen") }
    compose.waitUntil(15_000) { compose.activity.window.decorView.findViewWithTag<View>("note_markdown_editor") != null }
    compose.waitForIdle()
    compose.onNodeWithTag("note_tab_preview").performSemanticsAction(SemanticsActions.OnClick)
    compose.waitUntil(15_000) { findScene() != null }
    val scene = checkNotNull(findScene())
    compose.runOnIdle {
      val reader = checkNotNull(compose.activity.window.decorView.findViewWithTag<MarkdownSNoteRenderer>("markdown_s_native_reader"))
      val rect = Rect(0, 0, scene.width, scene.height)
      reader.offsetDescendantRectToMyCoords(scene, rect)
      reader.scrollTo(0, rect.top)
      val now = SystemClock.uptimeMillis()
      val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, scene.width * 0.7f, scene.height * 0.5f, 0)
      val move = MotionEvent.obtain(now, now + 70, MotionEvent.ACTION_MOVE, scene.width * 0.3f, scene.height * 0.5f, 0)
      val up = MotionEvent.obtain(now, now + 140, MotionEvent.ACTION_UP, scene.width * 0.3f, scene.height * 0.5f, 0)
      scene.dispatchTouchEvent(down); scene.dispatchTouchEvent(move); scene.dispatchTouchEvent(up)
      down.recycle(); move.recycle(); up.recycle()
    }
    compose.waitUntil(10_000) { MarkdownSBlockParser.parse(session.noteDraft.value).single().parseResult.state!!.angle != null }
    val savedDraft = session.noteDraft.value
    val savedAngle = MarkdownSBlockParser.parse(savedDraft).single().parseResult.state!!.angle!!
    compose.onNodeWithText(compose.activity.getString(R.string.save)).performSemanticsAction(SemanticsActions.OnClick)
    val project = ProjectDocumentOpener(resolver).open(treeUri)
    val store = ProjectNoteStore(resolver, project.notesUri)
    compose.waitUntil(10_000) { NotePage.pageAt(store.read(), 1) == savedDraft }

    compose.activityRule.scenario.recreate()
    compose.waitUntil(30_000) { compose.onAllNodesWithTag("reader_page_status").fetchSemanticsNodes().isNotEmpty() }
    val restored = ViewModelProvider(compose.activity)[ReaderSessionViewModel::class.java]
    assertEquals(savedAngle, MarkdownSBlockParser.parse(restored.noteDraft.value).single().parseResult.state!!.angle!!, 0.0)
    compose.onNodeWithTag("reader_bookshelf").performSemanticsAction(SemanticsActions.OnClick)
    compose.waitUntil(20_000) { compose.onAllNodesWithTag("bookshelf_book_$projectName").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("bookshelf_book_$projectName").performSemanticsAction(SemanticsActions.OnClick)
    compose.waitUntil(30_000) { compose.onAllNodesWithTag("reader_page_status").fetchSemanticsNodes().isNotEmpty() }
    val reopened = ProjectNoteStore(resolver, ProjectDocumentOpener(resolver).open(treeUri).notesUri).read()
    assertEquals(savedDraft, NotePage.pageAt(reopened, 1))
    assertEquals("第二页保留", NotePage.pageAt(reopened, 2))
    assertTrue(reopened.contains("前文") && reopened.contains("后文"))
    val reopenedSession = ViewModelProvider(compose.activity)[ReaderSessionViewModel::class.java]
    compose.waitUntil(15_000) { reopenedSession.noteDraft.value == savedDraft }
    compose.onNodeWithTag("note_tab_preview").performSemanticsAction(SemanticsActions.OnClick)
    compose.waitUntil(15_000) { findScene() != null }
    if (compose.onAllNodesWithTag("reader_menu_collapse").fetchSemanticsNodes().isNotEmpty()) {
      compose.onNodeWithTag("reader_menu_collapse").performSemanticsAction(SemanticsActions.OnClick)
    }
    val reopenedScene = checkNotNull(findScene())
    assertEquals(savedAngle, reopenedScene.rotationAngle(), 0.000001)
    SystemClock.sleep(1_500)
    val beforeScroll = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
    val readerBounds = Rect()
    compose.runOnIdle {
      val reader = checkNotNull(compose.activity.window.decorView.findViewWithTag<MarkdownSNoteRenderer>("markdown_s_native_reader"))
      reader.getGlobalVisibleRect(readerBounds)
      val rect = Rect(0, 0, reopenedScene.width, reopenedScene.height)
      reader.offsetDescendantRectToMyCoords(reopenedScene, rect)
      reader.scrollTo(0, rect.top)
    }
    SystemClock.sleep(2_000)
    val visibleScene = Rect()
    compose.runOnIdle { assertTrue(reopenedScene.getGlobalVisibleRect(visibleScene)) }
    val downAt = SystemClock.uptimeMillis()
    val y = visibleScene.exactCenterY()
    val x1 = visibleScene.left + visibleScene.width() * 0.75f
    val x2 = visibleScene.left + visibleScene.width() * 0.25f
    val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
    listOf(
      MotionEvent.obtain(downAt, downAt, MotionEvent.ACTION_DOWN, x1, y, 0),
      MotionEvent.obtain(downAt, downAt + 80, MotionEvent.ACTION_MOVE, x2, y, 0),
      MotionEvent.obtain(downAt, downAt + 160, MotionEvent.ACTION_UP, x2, y, 0),
    ).forEach { event ->
      event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
      assertTrue("触摸事件应注入系统窗口", ui.injectInputEvent(event, true))
      event.recycle()
    }
    compose.waitUntil(10_000) {
      val next = MarkdownSBlockParser.parse(reopenedSession.noteDraft.value).single().parseResult.state!!.angle
      next != null && kotlin.math.abs(next - savedAngle) > 0.05
    }
    SystemClock.sleep(500)
    val readerAfterDrag = checkNotNull(compose.activity.window.decorView.findViewWithTag<MarkdownSNoteRenderer>("markdown_s_native_reader"))
    val scrollBeforeVertical = readerAfterDrag.scrollY
    val verticalDownAt = SystemClock.uptimeMillis()
    val verticalX = visibleScene.exactCenterX()
    val verticalY = visibleScene.exactCenterY() - 100f
    listOf(
      MotionEvent.obtain(verticalDownAt, verticalDownAt, MotionEvent.ACTION_DOWN, verticalX, verticalY, 0),
      MotionEvent.obtain(verticalDownAt, verticalDownAt + 80, MotionEvent.ACTION_MOVE, verticalX, verticalY + 90f, 0),
      MotionEvent.obtain(verticalDownAt, verticalDownAt + 160, MotionEvent.ACTION_MOVE, verticalX, verticalY + 180f, 0),
      MotionEvent.obtain(verticalDownAt, verticalDownAt + 240, MotionEvent.ACTION_UP, verticalX, verticalY + 180f, 0),
    ).forEach { event ->
      event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
      assertTrue(ui.injectInputEvent(event, true))
      event.recycle()
    }
    compose.waitUntil(5_000) { readerAfterDrag.scrollY < scrollBeforeVertical }
    compose.runOnIdle {
      val rect = Rect(0, 0, reopenedScene.width, reopenedScene.height)
      readerAfterDrag.offsetDescendantRectToMyCoords(reopenedScene, rect)
      readerAfterDrag.scrollTo(0, rect.top)
    }
    SystemClock.sleep(500)
    val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
    var samples = 0
    var changed = 0
    for (y in (readerBounds.top - 700).coerceAtLeast(300) until readerBounds.top - 80 step 12) {
      for (x in readerBounds.left + 30 until readerBounds.right - 30 step 12) {
        samples++
        if (beforeScroll.getPixel(x, y) != bitmap.getPixel(x, y)) changed++
      }
    }
    beforeScroll.recycle()
    assertTrue("滚动笔记不应覆盖上方文档区域：$changed/$samples 像素变化", samples > 0 && changed < samples / 20)
    val screenshot = File(compose.activity.getExternalFilesDir(null), "main-activity-3d.png")
    screenshot.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
      "cp ${screenshot.absolutePath} /data/local/tmp/pagenote-main-activity-3d.png",
    ).close()
  }

  private fun findScene(): MarkdownSChromosomeSceneView? =
    compose.activity.window.decorView.findViewWithTag<View>("markdowns_three_scene") as? MarkdownSChromosomeSceneView
}
