package io.pagenote.android

import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import android.widget.Button
import android.widget.TextView
import android.widget.SeekBar
import android.view.MotionEvent
import android.view.View
import android.graphics.Rect
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sceneview.SceneView
import io.github.sceneview.node.GeometryNode
import io.noties.markwon.Markwon
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.MarkdownSState
import io.pagenote.feature.notes.NotePage
import io.pagenote.feature.notes.PageNoteEditor
import io.pagenote.feature.notes.MarkdownSBlockParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MarkdownSNoteRendererTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun autoRotationPausesWhenTheActivityIsStopped() {
    lateinit var scene: MarkdownSChromosomeSceneView
    composeRule.setContent {
      AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
        MarkdownSChromosomeSceneView(context, List(12) { it % 2 }, 0.15, true, true) { _, _ -> }.also { scene = it }
      })
    }
    composeRule.waitForIdle()
    SystemClock.sleep(500)
    composeRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
    SystemClock.sleep(300)
    val stoppedAngle = scene.rotationAngle()
    SystemClock.sleep(500)
    assertEquals("应用停止时旋转应暂停", stoppedAngle, scene.rotationAngle(), 0.0001)
    composeRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
    SystemClock.sleep(500)
    assertTrue("应用恢复后旋转应继续", kotlin.math.abs(scene.rotationAngle() - stoppedAngle) > 0.05)
  }

  @Test
  fun autoRotationPausesOutsideTheVisibleNoteViewport() {
    lateinit var scroll: android.widget.ScrollView
    lateinit var scene: MarkdownSChromosomeSceneView
    composeRule.setContent {
      AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
        scroll = android.widget.ScrollView(context)
        val content = android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.VERTICAL }
        content.addView(View(context), android.widget.LinearLayout.LayoutParams(-1, 4_000))
        scene = MarkdownSChromosomeSceneView(context, List(12) { it % 2 }, 0.15, true, true) { _, _ -> }
        content.addView(scene, android.widget.LinearLayout.LayoutParams(-1, 700))
        scroll.addView(content)
        scroll
      })
    }
    composeRule.waitForIdle()
    SystemClock.sleep(1_000)
    val hiddenStart = scene.rotationAngle()
    SystemClock.sleep(500)
    val hiddenEnd = scene.rotationAngle()
    assertEquals("离开可见区域时旋转应暂停", hiddenStart, hiddenEnd, 0.0001)
    composeRule.runOnIdle { scroll.scrollTo(0, 4_000) }
    composeRule.waitForIdle()
    SystemClock.sleep(500)
    assertTrue("重新进入可见区域时旋转应恢复", kotlin.math.abs(scene.rotationAngle() - hiddenEnd) > 0.05)
  }

  @Test
  fun opening3dSceneDoesNotForceTheScreenToStayAwake() {
    val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    val previous = composeRule.activity.window.attributes.flags and flag
    composeRule.setContent {
      AndroidView(factory = { context ->
        MarkdownSChromosomeSceneView(context, List(12) { it % 2 }, 0.15, false, true) { _, _ -> }
      })
    }
    composeRule.waitForIdle()
    assertEquals("阅读 3D 笔记不应强制屏幕常亮", previous, composeRule.activity.window.attributes.flags and flag)
  }

  @Test
  fun editingGenesReusesTheExistingFilamentScene() {
    val state = MarkdownSState.parse(
      """{"version":1,"id":"reuse","kind":"genetic-lab","genes":[1,0,1,0,1,0,1,0,1,0,1,0]}""",
    )
    lateinit var lab: MarkdownSGeneticLabView
    composeRule.setContent {
      AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
        MarkdownSGeneticLabView(context) { _, _ -> }.also { lab = it }
      }, update = { it.bind(state, android.graphics.Color.BLACK, android.graphics.Color.WHITE, true) })
    }
    composeRule.waitForIdle()
    composeRule.runOnIdle {
      val before = checkNotNull(lab.findViewWithTag<MarkdownSChromosomeSceneView>("markdowns_three_scene"))
      val gene = before.childNodes.single().childNodes.filterIsInstance<GeometryNode>().single { it.name == "markdowns_gene_0" }
      val previousMaterial = gene.materialInstance
      tapView(checkNotNull(lab.findViewWithTag<View>("markdowns_chromosome")), 0.06f)
      val after = checkNotNull(lab.findViewWithTag<MarkdownSChromosomeSceneView>("markdowns_three_scene"))
      assertSame("基因变化不应销毁并重建 Filament 场景", before, after)
      assertNotSame("位点变化应更新对应 3D 材质", previousMaterial, gene.materialInstance)
    }
  }

  @Test
  fun rendererInitializationFailureShowsInteractiveFallback() {
    val state = MarkdownSState.parse(
      """{"version":1,"id":"fallback","kind":"genetic-lab","genes":[1,0,1,0,1,0,1,0,1,0,1,0]}""",
    )
    lateinit var lab: MarkdownSGeneticLabView
    var updated: MarkdownSState? = null
    composeRule.setContent {
      AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
        MarkdownSGeneticLabView(context, sceneFactory = { _, _, _, _, _, _ -> throw IllegalStateException("GPU unavailable") }) { _, next ->
          updated = next
        }.also { lab = it }
      }, update = { it.bind(state, android.graphics.Color.BLACK, android.graphics.Color.WHITE, true) })
    }
    composeRule.waitForIdle()
    composeRule.runOnIdle {
      val messages = ArrayList<View>()
      lab.findViewsWithText(messages, "3D 渲染不可用，显示二维染色体示意。", View.FIND_VIEWS_WITH_TEXT)
      assertTrue("初始化失败应明确显示降级说明", messages.isNotEmpty())
      val fallback = checkNotNull(lab.findViewWithTag<View>("markdowns_three_scene"))
      assertTrue("失败后应显示二维替代视图", fallback !is SceneView)
      tapView(checkNotNull(lab.findViewWithTag<View>("markdowns_chromosome")), 0.06f)
    }
    composeRule.waitUntil(5_000) { updated != null }
    assertEquals(0, updated!!.genes.first())
  }

  @Test
  fun filamentSceneTouchSelectsARealGeneNode() {
    lateinit var scene: MarkdownSChromosomeSceneView
    var selected: Int? = null
    composeRule.setContent {
      AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
        MarkdownSChromosomeSceneView(context, List(12) { it % 2 }, 0.15, false, true) { gene, _ -> selected = gene }
          .also { scene = it }
      })
    }
    composeRule.waitForIdle()
    SystemClock.sleep(1_500)
    composeRule.runOnIdle {
      var target: Pair<Float, Float>? = null
      val now = SystemClock.uptimeMillis()
      search@ for (y in 0 until scene.height step 12) for (x in 0 until scene.width step 12) {
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0)
        val name = scene.collisionSystem.hitTest(event).firstOrNull { it.node.isTouchable }?.node?.name
        event.recycle()
        if (name?.startsWith("markdowns_gene_") == true || name?.startsWith("markdowns_base_") == true) {
          target = x.toFloat() to y.toFloat()
          break@search
        }
      }
      val (x, y) = checkNotNull(target) { "3D 场景中没有可触摸的基因位点" }
      val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
      val up = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP, x, y, 0)
      scene.dispatchTouchEvent(down)
      scene.dispatchTouchEvent(up)
      down.recycle()
      up.recycle()
    }
    composeRule.waitUntil(5_000) { selected != null }
    assertTrue((selected ?: -1) in 0..11)
  }

  @Test
  fun filamentGeneTouchUpdatesOnlyItsMarkdownFence() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val original = "Before\n\n```markdowns\n" +
      "{\"version\":1,\"id\":\"3d-touch\",\"kind\":\"genetic-lab\",\"genes\":[1,0,1,0,1,0,1,0,1,0,1,0]}\n" +
      "```\n\nAfter"
    var markdown = original
    lateinit var renderer: MarkdownSNoteRenderer
    composeRule.setContent {
      AndroidView(modifier = Modifier.fillMaxSize(), factory = { MarkdownSNoteRenderer(it).also { view -> renderer = view } }, update = {
        it.setMarkdown(Markwon.create(context), markdown, android.graphics.Color.BLACK, android.graphics.Color.WHITE, 16f, false, true,
          onMarkdownChanged = { _, next -> markdown = next })
      })
    }
    composeRule.waitForIdle()
    lateinit var scene: MarkdownSChromosomeSceneView
    composeRule.runOnIdle {
      scene = checkNotNull(renderer.findViewWithTag("markdowns_three_scene"))
      scrollToView(renderer, scene)
    }
    SystemClock.sleep(1_500)
    composeRule.runOnIdle {
      val now = SystemClock.uptimeMillis()
      var target: Pair<Float, Float>? = null
      search@ for (y in 0 until scene.height step 8) for (x in 0 until scene.width step 8) {
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0)
        val name = scene.collisionSystem.hitTest(event).firstOrNull { it.node.isTouchable }?.node?.name
        event.recycle()
        if (name?.startsWith("markdowns_gene_") == true || name?.startsWith("markdowns_base_") == true) {
          target = x.toFloat() to y.toFloat()
          break@search
        }
      }
      val (x, y) = checkNotNull(target) { "3D 场景中没有可触摸的基因位点" }
      val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
      val up = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP, x, y, 0)
      scene.dispatchTouchEvent(down)
      scene.dispatchTouchEvent(up)
      down.recycle()
      up.recycle()
    }
    composeRule.waitUntil(5_000) { markdown != original }
    val before = MarkdownSBlockParser.parse(original).single().parseResult.state!!
    val after = MarkdownSBlockParser.parse(markdown).single().parseResult.state!!
    assertEquals(1, before.genes.indices.count { before.genes[it] != after.genes[it] })
    assertTrue(markdown.startsWith("Before\n\n```markdowns\n"))
    assertTrue(markdown.endsWith("\n\nAfter"))
  }

  @Test
  fun filamentSceneSurvivesRepeatedAttachAndDispose() {
    val shown = mutableStateOf(true)
    lateinit var scene: MarkdownSChromosomeSceneView
    composeRule.setContent {
      if (shown.value) {
        AndroidView(factory = { context ->
          MarkdownSChromosomeSceneView(context, List(12) { it % 2 }, 0.15, false, true) { _, _ -> }.also { scene = it }
        })
      }
    }
    repeat(12) {
      composeRule.runOnIdle { scene.dispose(); shown.value = false }
      composeRule.waitForIdle()
      composeRule.runOnIdle { shown.value = true }
      composeRule.waitForIdle()
    }
    composeRule.runOnIdle { shown.value = false }
    composeRule.waitForIdle()
  }

  @Test
  fun nativeLabStateRoundTripsThroughTheProjectMarkdownFile() {
    val page = "开头保留。\n\n```markdowns\n" +
      "{\"version\":1,\"id\":\"android-roundtrip\",\"kind\":\"genetic-lab\",\"genes\":[1,0,1,0,1,0,1,0,1,0,1,0]}\n" +
      "```\n\n结尾保留。"
    val secondPage = "其他页面仍在原位。"
    val allNotes = PageNotes.join(listOf(page, secondPage))
    val testResolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    val treeUri = existingProjectTestTreeUri()
    val targetActivity = composeRule.activity
    val permissionFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
      android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    targetActivity.grantUriPermission(
      targetActivity.packageName, treeUri,
      permissionFlags or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
    )
    testResolver.takePersistableUriPermission(treeUri, permissionFlags)
    val manifest = """{"format":"pagenote-project","version":1,"id":"${"d".repeat(64)}","name":"Markdown S round trip","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}"""
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      android.os.Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest.toByteArray(Charsets.UTF_8))
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, minimalReaderTestPdf())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, allNotes.toByteArray(Charsets.UTF_8))
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
      },
    )
    val project = ProjectDocumentOpener(testResolver).open(treeUri)
    val noteStore = ProjectNoteStore(testResolver, project.notesUri)
    val draft = mutableStateOf(page)
    val screenshotDir = File(targetActivity.getExternalFilesDir(null), "markdowns-evidence").apply { mkdirs() }

    fun showNotePanel() {
      composeRule.setContent {
        NotePanel(
          page = 1,
          markdown = draft.value,
          dirty = draft.value != page,
          writable = true,
          saving = false,
          status = null,
          onMarkdownChange = { draft.value = it },
          onSave = {
            val latest = noteStore.read()
            val changed = PageNoteEditor.replacePage(
              latestMarkdown = latest,
              page = 1,
              replacement = draft.value,
              expectedRevision = PageNotes.revision(page),
            ).markdown
            noteStore.write(changed, expectedOriginal = latest)
          },
          onReload = {},
          animationEnabled = false,
        )
      }
    }

    showNotePanel()
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitForIdle()
    val renderer = composeRule.activity.window.decorView
      .findViewWithTag<MarkdownSNoteRenderer>("markdown_s_native_reader")
      ?: error("NotePanel preview should host the native Markdown S renderer")
    val before = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
    File(screenshotDir, "note-panel-before.png").outputStream().use {
      before.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
    }
    before.recycle()

    composeRule.runOnIdle {
      tapView(checkNotNull(renderer.findViewWithTag<View>("markdowns_chromosome")), 0.06f)
    }
    composeRule.waitUntil(5_000) { draft.value != page }
    val updated = draft.value
    assertTrue(updated.startsWith("开头保留。\n\n```markdowns\n"))
    assertEquals(0, MarkdownSBlockParser.parse(updated).single().parseResult.state!!.genes.first())
    assertTrue(updated.endsWith("\n\n结尾保留。"))
    val landscapeHeading = checkNotNull(renderer.findViewWithTag<TextView>("markdowns_section_适应度地形"))
    composeRule.runOnIdle { scrollToView(renderer, landscapeHeading) }
    composeRule.waitForIdle()
    val initiallyVisibleLandscape = Rect()
    assertTrue("测试前应滚动到适应度地形", landscapeHeading.getGlobalVisibleRect(initiallyVisibleLandscape))
    val sectionScrollY = renderer.scrollY
    composeRule.runOnIdle { dragSlider(renderer, "采样位置", 0.72f) }
    composeRule.waitForIdle()
    val visibleLandscape = Rect()
    val updatedLandscapeHeading = checkNotNull(renderer.findViewWithTag<TextView>("markdowns_section_适应度地形"))
    assertTrue("状态更新后应留在原章节", updatedLandscapeHeading.getGlobalVisibleRect(visibleLandscape))
    assertEquals("组件更新不应把阅读位置跳回顶部", sectionScrollY, renderer.scrollY)
    val updatedState = MarkdownSBlockParser.parse(draft.value).single().parseResult.state!!
    assertTrue("滑块变更应持久化新的采样位置", updatedState.position!! > 65.0)
    composeRule.runOnIdle {
      scrollToView(renderer, checkNotNull(renderer.findViewWithTag<View>("markdowns_three_scene")))
      dragView(renderer, "markdowns_three_scene")
    }
    composeRule.waitUntil(5_000) { MarkdownSBlockParser.parse(draft.value).single().parseResult.state!!.angle != null }
    val sceneAngle = MarkdownSBlockParser.parse(draft.value).single().parseResult.state!!.angle!!
    val savedDraft = draft.value
    SystemClock.sleep(2_000)
    val after = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
    File(screenshotDir, "note-panel-after-interaction.png").outputStream().use {
      after.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
    }
    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
      "cp ${File(screenshotDir, "note-panel-after-interaction.png").absolutePath} /data/local/tmp/pagenote-note-panel-3d.png",
    ).close()
    after.recycle()

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(10_000) { NotePage.pageAt(noteStore.read(), 1) == savedDraft }
    val reopenedProject = ProjectDocumentOpener(testResolver).open(treeUri)
    val reopenedNotes = ProjectNoteStore(testResolver, reopenedProject.notesUri).read()
    assertEquals(savedDraft, NotePage.pageAt(reopenedNotes, 1))
    assertEquals(secondPage, NotePage.pageAt(reopenedNotes, 2))

    draft.value = NotePage.pageAt(reopenedNotes, 1)
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitForIdle()
    val restored = composeRule.activity.window.decorView
      .findViewWithTag<MarkdownSNoteRenderer>("markdown_s_native_reader")
      ?: error("Reopened note preview should recreate the native component")
    assertEquals(0, MarkdownSBlockParser.parse(reopenedNotes).first().parseResult.state!!.genes.first())
    assertEquals(updatedState.position!!, MarkdownSBlockParser.parse(reopenedNotes).first().parseResult.state!!.position!!, 0.0)
    assertEquals(sceneAngle, MarkdownSBlockParser.parse(reopenedNotes).first().parseResult.state!!.angle!!, 0.0)
    assertTrue(restored.findViewWithTag<View>("markdowns_chromosome") != null)
  }

  @Test
  fun nativeLabInteractionUpdatesOnlyItsFenceAndHtmlFenceStaysSource() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val markwon = Markwon.create(context)
    val original = "Before\n\n```markdowns\n" +
      "{\"version\":1,\"id\":\"android-lab\",\"kind\":\"genetic-lab\",\"genes\":[1,0,1,0,1,0,1,0,1,0,1,0]}\n" +
      "```\n\n```mds-html\n<script>window.wasExecuted=true</script>\n```\n\nAfter"
    val originalGenes = MarkdownSBlockParser.parse(original).single().parseResult.state!!.genes
    var renderedMarkdown = original
    lateinit var rendererView: MarkdownSNoteRenderer

    composeRule.setContent {
      AndroidView(
        factory = { MarkdownSNoteRenderer(it).also { view -> rendererView = view } },
        update = { view ->
          view.setMarkdown(
            markwon, renderedMarkdown, android.graphics.Color.BLACK,
            android.graphics.Color.WHITE, 16f, false, true,
            onMarkdownChanged = { _, updated -> renderedMarkdown = updated },
          )
        },
      )
    }

    composeRule.waitForIdle()
    composeRule.runOnIdle {
      tapView(checkNotNull(rendererView.findViewWithTag<View>("markdowns_chromosome")), 0.06f)
    }
    composeRule.waitUntil(5_000) { renderedMarkdown != original }
    assertTrue(renderedMarkdown.startsWith("Before\n\n```markdowns\n"))
    assertEquals(0, MarkdownSBlockParser.parse(renderedMarkdown).single().parseResult.state!!.genes.first())
    assertTrue(renderedMarkdown.contains("```mds-html\n<script>window.wasExecuted=true</script>\n```"))
    assertTrue(renderedMarkdown.endsWith("After"))
    composeRule.runOnIdle {
      brushChromosome(checkNotNull(rendererView.findViewWithTag<View>("markdowns_chromosome")), 0.72f, 0.32f)
    }
    composeRule.waitForIdle()
    val brushedGenes = MarkdownSBlockParser.parse(renderedMarkdown).single().parseResult.state!!.genes
    (3..8).forEach { index -> assertEquals("brush 应逐个位点翻转 $index", 1 - originalGenes[index], brushedGenes[index]) }
    assertTrue(renderedMarkdown.startsWith("Before\n\n```markdowns\n"))
    assertTrue(renderedMarkdown.contains("```mds-html\n<script>window.wasExecuted=true</script>\n```"))
    assertTrue(renderedMarkdown.endsWith("After"))

    val evidenceDir = File(context.getExternalFilesDir(null), "markdowns-evidence").apply { mkdirs() }
    val sections = listOf(
      "编码与变异" to "01-chromosome",
      "种群与个体" to "02-population",
      "适应度地形" to "03-fitness-landscape",
      "选择轮盘" to "04-selection-wheel",
      "基因与交叉" to "05-crossover",
      "变异概率与演化" to "06-mutation",
      "演化记录" to "07-generation-chart",
      "三维染色体" to "08-chromosome-3d",
    )
    sections.forEach { (title, filename) ->
      composeRule.runOnIdle {
        when (filename) {
          "03-fitness-landscape" -> dragSlider(rendererView, "采样位置", 0.72f)
          "04-selection-wheel" -> dragWheel(rendererView)
          "05-crossover" -> dragSlider(rendererView, "交叉切点", 0.7f)
          "06-mutation" -> {
            dragSlider(rendererView, "变异概率", 0.65f)
          }
          "07-generation-chart" -> dragSlider(rendererView, "演化世代", 0.95f)
          "08-chromosome-3d" -> dragView(rendererView, "markdowns_three_scene")
        }
      }
      if (filename == "06-mutation") {
        composeRule.waitForIdle()
        composeRule.runOnIdle {
          dragSlider(rendererView, "演化世代", 0.95f)
        }
        composeRule.waitForIdle()
        val saved = MarkdownSBlockParser.parse(renderedMarkdown).single().parseResult.state!!
        assertEquals(0.2, saved.mutationRate!!, 0.011)
        assertTrue("世代滑块应写回 Markdown", (saved.generation ?: 0) > 0)
        val generationLabel = ArrayList<View>()
        rendererView.findViewsWithText(generationLabel, "第 ${saved.generation} / 40 代", View.FIND_VIEWS_WITH_TEXT)
        assertTrue("世代标签应反映已写回的值", generationLabel.isNotEmpty())
      }
      if (filename == "04-selection-wheel") {
        composeRule.waitForIdle()
        val beforeKeyboard = MarkdownSBlockParser.parse(renderedMarkdown).single().parseResult.state!!.wheelAngle
        composeRule.runOnIdle {
          val wheel = checkNotNull(rendererView.findViewWithTag<View>("markdowns_fitness_wheel"))
          wheel.requestFocus()
          wheel.onKeyDown(
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
            android.view.KeyEvent(SystemClock.uptimeMillis(), "", 0, android.view.KeyEvent.KEYCODE_DPAD_RIGHT),
          )
        }
        composeRule.waitForIdle()
        val afterKeyboard = MarkdownSBlockParser.parse(renderedMarkdown).single().parseResult.state!!.wheelAngle
        assertTrue("轮盘左右方向键应移动选择", beforeKeyboard != afterKeyboard)
      }
      if (filename == "08-chromosome-3d") {
        composeRule.waitForIdle()
        repeat(2) {
          composeRule.runOnIdle {
            checkNotNull(rendererView.findViewWithTag<Button>("markdowns_rotation_toggle")).performClick()
          }
          composeRule.waitForIdle()
        }
        val saved = MarkdownSBlockParser.parse(renderedMarkdown).single().parseResult.state!!
        assertTrue("三维旋转角应写回 Markdown", saved.angle != null)
        assertEquals(true, saved.paused)
      }
      composeRule.runOnIdle {
        val section = rendererView.findViewWithTag<TextView>("markdowns_section_$title")
        checkNotNull(section)
        rendererView.post {
          val bounds = Rect(0, 0, section.width, section.height)
          rendererView.offsetDescendantRectToMyCoords(section, bounds)
          rendererView.scrollTo(0, bounds.top)
        }
      }
      InstrumentationRegistry.getInstrumentation().waitForIdleSync()
      SystemClock.sleep(350)
      if (filename == "08-chromosome-3d") SystemClock.sleep(2_000)
      composeRule.waitForIdle()
      composeRule.runOnIdle {
        val section = checkNotNull(rendererView.findViewWithTag<TextView>("markdowns_section_$title"))
        val visibleBounds = Rect()
        assertTrue("$title 标题应滚动到视口", section.getGlobalVisibleRect(visibleBounds))
      }
      val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
      File(evidenceDir, "$filename.png").outputStream().use { stream ->
        screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
      }
      if (filename == "08-chromosome-3d") {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
          "cp ${File(evidenceDir, "$filename.png").absolutePath} /data/local/tmp/pagenote-scene-3d.png",
        ).close()
      }
      screenshot.recycle()
    }
  }

  @Test
  fun chromosomeChapterUsesFilamentSceneWithOneTouchableNodePerSharedGene() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val state = MarkdownSState.parse(
      """{"version":1,"id":"scene-test","kind":"genetic-lab","genes":[1,0,1,0,1,0,1,0,1,0,1,0]}""",
    )
    lateinit var lab: MarkdownSGeneticLabView

    composeRule.setContent {
      AndroidView(
        factory = { MarkdownSGeneticLabView(it) { _, _ -> }.also { view -> lab = view } },
        update = { it.bind(state, android.graphics.Color.BLACK, android.graphics.Color.WHITE, enabled = true) },
      )
    }
    composeRule.waitForIdle()

    val scene = checkNotNull(lab.findViewWithTag<View>("markdowns_three_scene"))
    assertTrue("第 8 章应使用原生 Filament SceneView", scene is SceneView)
    val root = (scene as SceneView).childNodes.single()
    val geneNodes = root.childNodes.filter { it.name?.startsWith("markdowns_gene_") == true }
    assertEquals("每个 Markdown genes 项都应有一个可交互 3D 节点", state.genes.size, geneNodes.size)
    assertEquals(state.genes.indices.map { "markdowns_gene_$it" }, geneNodes.map { it.name })
  }

  @Test
  fun notePanelPreviewRoutesComponentChangesIntoExistingMarkdownDraftCallback() {
    val original = "Before\n\n```markdowns\n" +
      "{\"version\":1,\"id\":\"note-panel-lab\",\"kind\":\"genetic-lab\",\"genes\":[1,0,1,0,1,0,1,0,1,0,1,0]}\n" +
      "```\n\nAfter"
    var noteDraft = original

    composeRule.setContent {
      NotePanel(
        page = 1,
        markdown = noteDraft,
        dirty = noteDraft != original,
        writable = true,
        saving = false,
        status = null,
        onMarkdownChange = { noteDraft = it },
        onSave = {},
        onReload = {},
        animationEnabled = false,
      )
    }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitForIdle()
    composeRule.runOnIdle {
      val host = checkNotNull(composeRule.activity.window.decorView.findViewWithTag<MarkdownSNoteRenderer>("markdown_s_native_reader"))
      tapView(checkNotNull(host.findViewWithTag<View>("markdowns_chromosome")), 0.06f)
    }
    composeRule.waitUntil(5_000) { noteDraft != original }
    assertTrue(noteDraft.startsWith("Before\n\n```markdowns\n"))
    assertEquals(0, MarkdownSBlockParser.parse(noteDraft).single().parseResult.state!!.genes.first())
    assertTrue(noteDraft.endsWith("After"))
  }

  private fun dragSlider(host: MarkdownSNoteRenderer, description: String, fraction: Float) {
    val slider = checkNotNull(host.findViewWithTag<SeekBar>("markdowns_slider_$description"))
    val y = slider.height / 2f
    val start = slider.width * 0.1f
    val end = slider.width * fraction.coerceIn(0.1f, 0.95f)
    val down = SystemClock.uptimeMillis()
    slider.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, start, y, 0))
    slider.dispatchTouchEvent(MotionEvent.obtain(down, down + 80, MotionEvent.ACTION_MOVE, end, y, 0))
    slider.dispatchTouchEvent(MotionEvent.obtain(down, down + 160, MotionEvent.ACTION_UP, end, y, 0))
  }

  private fun dragWheel(host: MarkdownSNoteRenderer) {
    val wheel = checkNotNull(host.findViewWithTag<View>("markdowns_fitness_wheel"))
    val x = wheel.width * 0.8f
    val y = wheel.height / 2f
    val down = SystemClock.uptimeMillis()
    wheel.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
    wheel.dispatchTouchEvent(MotionEvent.obtain(down, down + 80, MotionEvent.ACTION_MOVE, wheel.width * 0.5f, y * 0.2f, 0))
    wheel.dispatchTouchEvent(MotionEvent.obtain(down, down + 160, MotionEvent.ACTION_UP, wheel.width * 0.5f, y * 0.2f, 0))
  }

  private fun dragView(host: MarkdownSNoteRenderer, tag: String) {
    val view = checkNotNull(host.findViewWithTag<View>(tag))
    val startX = view.width * 0.72f
    val endX = view.width * 0.32f
    val y = view.height * 0.48f
    val down = SystemClock.uptimeMillis()
    view.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, startX, y, 0))
    view.dispatchTouchEvent(MotionEvent.obtain(down, down + 80, MotionEvent.ACTION_MOVE, endX, y, 0))
    view.dispatchTouchEvent(MotionEvent.obtain(down, down + 160, MotionEvent.ACTION_UP, endX, y, 0))
  }

  private fun tapView(view: View, horizontalFraction: Float) {
    val x = view.width * horizontalFraction
    val y = view.height * 0.5f
    val down = SystemClock.uptimeMillis()
    view.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
    view.dispatchTouchEvent(MotionEvent.obtain(down, down + 80, MotionEvent.ACTION_UP, x, y, 0))
  }

  private fun scrollToView(host: MarkdownSNoteRenderer, target: View) {
    val bounds = Rect(0, 0, target.width, target.height)
    host.offsetDescendantRectToMyCoords(target, bounds)
    host.scrollTo(0, bounds.top)
  }

  private fun brushChromosome(view: View, startFraction: Float, endFraction: Float) {
    val start = SystemClock.uptimeMillis()
    val y = view.height * 0.5f
    view.dispatchTouchEvent(MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, view.width * startFraction, y, 0))
    view.dispatchTouchEvent(MotionEvent.obtain(start, start + 80, MotionEvent.ACTION_MOVE, view.width * endFraction, y, 0))
    view.dispatchTouchEvent(MotionEvent.obtain(start, start + 160, MotionEvent.ACTION_UP, view.width * endFraction, y, 0))
  }
}
