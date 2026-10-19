package io.pagenote.android

import android.text.Spanned
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.image.AsyncDrawableSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class MarkdownMermaidPreviewTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun sourcePreviewKeepsSurroundingMarkdownAndRendersDiagram() {
    val markdown = "Before diagram\n\n```mermaid\ngraph TD\nA[Start] --> B[Finish]\n```\n\nAfter diagram"
    val hostRef = AtomicReference<MermaidPreviewHost?>()
    composeRule.setContent { MermaidTestPreview(markdown, hostRef) }
    val image = awaitImage(hostRef) { it !is NoteImagePlaceholderDrawable }
    val text = requireNotNull(hostRef.get()).markdownView.text as Spanned
    assertTrue("diagram image should finish with a native drawable", image.hasResult())
    assertFalse("valid source should not become an error placeholder", image.result is NoteImagePlaceholderDrawable)
    assertTrue("diagram should stay within its preview column", image.result.intrinsicWidth <= requireNotNull(hostRef.get()).width)
    assertTrue("diagram should have a visible height", image.result.intrinsicHeight > 10)
    assertTrue(text.toString().contains("Before diagram"))
    assertTrue(text.toString().contains("After diagram"))
  }

  @Test
  fun mermaidBlockDoesNotOverlapFollowingParagraphAtTabletNotePaneWidth() {
    val source = """
      flowchart LR
          A["真实类别"] --> B["有病：阳性样本"]
          A --> C["无病：阴性样本"]
          B --> D["判为阳性：TP"]
          B --> E["判为阴性：FN"]
          C --> F["判为阳性：FP"]
          C --> G["判为阴性：TN"]
          D --> H["进入灵敏度的分子"]
          E --> H
          G --> I["进入特异度的分子"]
          F --> I
    """.trimIndent()
    val dollar = '$'
    val markdown = """
      # 第12页｜灵敏度与特异度的意义

      第11页已经用新冠检测的混淆矩阵说明，分类结果需要区分真实类别与预测类别。第12页进一步回答：为什么不能只用总体命中率概括检测性能，以及怎样分别衡量模型识别病人和识别健康人的能力。

      ```mermaid
      $source
      ```

      在二分类任务中，通常把需要识别的目标类别指定为阳性，其余类别指定为阴性。设 ${dollar}TP${dollar} 为真实阳性且预测阳性的样本数，${dollar}FN${dollar} 为真实阳性但预测阴性的样本数，${dollar}FP${dollar} 为真实阴性但预测阳性的样本数，${dollar}TN${dollar} 为真实阴性且预测阴性的样本数。灵敏度（sensitivity，Sn）定义为所有真实阳性样本中被正确判为阳性的比例：

      ${dollar}${dollar}
      \\mathrm{Sn}=\\mathrm{TPR}=\\frac{\\mathrm{TP}}{\\mathrm{TP}+\\mathrm{FN}},
      ${dollar}${dollar}

      其中漏诊率为 ${dollar}\\mathrm{FNR}=1-\\mathrm{Sn}${dollar}。从医学诊断角度看，灵敏度高表示检测方法能发现更多真正患病的人，较少把病人遗漏。因此，疾病筛查通常重视灵敏度，因为漏掉病人可能带来更大的后续风险。

      特异度（specificity，Sp）定义为所有真实阴性样本中被正确判为阴性的比例：

      ${dollar}${dollar}
      \\mathrm{Sp}=1-\\mathrm{FPR}=\\frac{\\mathrm{TN}}{\\mathrm{TN}+\\mathrm{FP}},
      ${dollar}${dollar}

      灵敏度和特异度反映两个不同条件下的正确率，因此二者通常存在权衡。若把所有人都判为阳性，则阳性样本全部被找到，${dollar}\\mathrm{Sn}=1${dollar}，但健康人也被全部误判，${dollar}\\mathrm{Sp}=0${dollar}；若把所有人都判为阴性，则没有健康人被误报，${dollar}\\mathrm{Sp}=1${dollar}，但所有病人均被漏掉，${dollar}\\mathrm{Sn}=0${dollar}。
    """.trimIndent()
    val hostRef = AtomicReference<MermaidPreviewHost?>()
    composeRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme(onSurface = androidx.compose.ui.graphics.Color.Magenta)) {
        NotePanel(
          page = 12,
          markdown = markdown,
          dirty = false,
          writable = true,
          saving = false,
          status = null,
          onMarkdownChange = {},
          onSave = {},
          onReload = {},
          animationEnabled = false,
          modifier = Modifier.fillMaxHeight().fillMaxWidth(0.44f),
        )
      }
    }
    composeRule.onNodeWithText("Preview").performClick()
    composeRule.waitForIdle()
    composeRule.activityRule.scenario.onActivity { activity ->
      hostRef.set(findMermaidHost(activity.window.decorView))
    }

    val image = awaitImage(hostRef) { it !is NoteImagePlaceholderDrawable }
    composeRule.waitUntil(timeoutMillis = 5_000) {
      val host = hostRef.get() ?: return@waitUntil false
      val text = host.markdownView.text as? Spanned ?: return@waitUntil false
      val span = mermaidSpans(text).singleOrNull() ?: return@waitUntil false
      val layout = host.markdownView.layout ?: return@waitUntil false
      val start = text.getSpanStart(span)
      if (start < 0) return@waitUntil false
      val line = layout.getLineForOffset(start)
      layout.getLineBottom(line) - layout.getLineTop(line) >= span.drawable.bounds.height()
    }
    val host = requireNotNull(hostRef.get())
    val spanned = host.markdownView.text as Spanned
    val text = spanned.toString()
    assertTrue("Chinese labels should render into a native drawable", image.hasResult())
    assertFalse("valid source should not become an error placeholder", image.result is NoteImagePlaceholderDrawable)
    assertTrue(text.contains("第11页已经用新冠检测"))
    assertTrue(text.contains("在二分类任务中"))

    val imageSpan = mermaidSpans(spanned).single()
    val imageStart = spanned.getSpanStart(imageSpan)
    val layout = requireNotNull(host.markdownView.layout)
    val bounds = Rect(imageSpan.drawable.bounds)
    val svgDrawable = requireNotNull(imageSpan.drawable.result)
    val drawableBitmap = Bitmap.createBitmap(
      bounds.width() + 64,
      bounds.height() + 64,
      Bitmap.Config.ARGB_8888,
    )
    val drawableOrigin = 32
    val originalBounds = Rect(svgDrawable.bounds)
    svgDrawable.setBounds(
      drawableOrigin,
      drawableOrigin,
      drawableOrigin + bounds.width(),
      drawableOrigin + bounds.height(),
    )
    svgDrawable.draw(Canvas(drawableBitmap))
    val renderedBounds = Rect(svgDrawable.bounds)
    var drawablePixelsOutsideBounds = 0
    for (y in 0 until drawableBitmap.height) {
      for (x in 0 until drawableBitmap.width) {
        val outside = x < renderedBounds.left || x >= renderedBounds.right ||
          y < renderedBounds.top || y >= renderedBounds.bottom
        if (outside && Color.alpha(drawableBitmap.getPixel(x, y)) > 0) {
          drawablePixelsOutsideBounds += 1
        }
      }
    }
    drawableBitmap.recycle()
    svgDrawable.bounds = originalBounds
    val imageLine = layout.getLineForOffset(imageStart)
    val imageLeft = host.markdownView.totalPaddingLeft + layout.getPrimaryHorizontal(imageStart).toInt() -
      host.markdownView.scrollX
    val imageTop = host.markdownView.extendedPaddingTop + layout.getLineBottom(imageLine) - bounds.bottom -
      host.markdownView.scrollY
    val afterStart = text.indexOf("在二分类任务中")
    val afterLine = layout.getLineForOffset(afterStart)
    val afterLineTop = host.markdownView.extendedPaddingTop + layout.getLineTop(afterLine) -
      host.markdownView.scrollY
    val imageBottom = imageTop + bounds.height()
    val imageLineHeight = layout.getLineBottom(imageLine) - layout.getLineTop(imageLine)
    val lineStart = layout.getLineStart(imageLine)
    val lineEnd = layout.getLineEnd(imageLine)
    val spanContext = text.substring((imageStart - 24).coerceAtLeast(0), (imageStart + 48).coerceAtMost(text.length))
    val bitmap = Bitmap.createBitmap(
      host.markdownView.width.coerceAtLeast(1),
      host.markdownView.height.coerceAtLeast(1),
      Bitmap.Config.ARGB_8888,
    )
    composeRule.runOnIdle { host.markdownView.draw(Canvas(bitmap)) }

    var markdownPixelsInsideDiagram = 0
    val scanLeft = imageLeft.coerceIn(0, bitmap.width)
    val scanTop = imageTop.coerceIn(0, bitmap.height)
    val scanRight = (imageLeft + bounds.width()).coerceIn(scanLeft, bitmap.width)
    val scanBottom = (imageTop + bounds.height()).coerceIn(scanTop, bitmap.height)
    for (y in scanTop until scanBottom) {
      for (x in scanLeft until scanRight) {
        val pixel = bitmap.getPixel(x, y)
        if (Color.red(pixel) > 200 && Color.green(pixel) < 100 && Color.blue(pixel) > 200) {
          markdownPixelsInsideDiagram += 1
        }
      }
    }
    bitmap.recycle()
    assertTrue("diagram drawable must have measurable area: $bounds", bounds.width() > 0 && bounds.height() > 0)
    assertEquals("SVG pixels must stay inside drawable bounds: $bounds", 0, drawablePixelsOutsideBounds)
    assertTrue("diagram should fit in the tablet note pane: host=${host.width}, image=$bounds", bounds.width() <= host.width)
    assertTrue(
      "Mermaid line must reserve the rendered drawable height " +
        "(lineHeight=$imageLineHeight, drawableHeight=${bounds.height()}, imageStart=$imageStart)",
      imageLineHeight >= bounds.height(),
    )
    assertTrue(
      "following paragraph starts before Mermaid drawable ends " +
        "(host=${host.width}x${host.height}, image=$bounds at $imageTop..$imageBottom, " +
        "afterLine=$afterLine top=$afterLineTop)",
      afterLineTop >= imageBottom,
    )
    assertTrue(
      "Markdown glyphs must not be painted over the Mermaid drawable " +
        "(host=${host.width}x${host.height}, image=$bounds at $imageLeft,$imageTop, " +
        "imageBottom=$imageBottom, imageStart=$imageStart line=$imageLine[$lineStart,$lineEnd] " +
        "lineTop=${layout.getLineTop(imageLine)} baseline=${layout.getLineBaseline(imageLine)} " +
        "lineBottom=${layout.getLineBottom(imageLine)}, afterLineTop=$afterLineTop, " +
        "context=$spanContext, overlapPixels=$markdownPixelsInsideDiagram)",
      markdownPixelsInsideDiagram == 0,
    )
  }

  @Test
  fun invalidDiagramShowsNativeWarningAndRetainsSource() {
    val source = "this is not a Mermaid diagram"
    val hostRef = AtomicReference<MermaidPreviewHost?>()
    composeRule.setContent { MermaidTestPreview("```mermaid\n$source\n```", hostRef) }

    val image = awaitImage(hostRef) { it is NoteImagePlaceholderDrawable && it.message.contains(source) }
    val warning = image.result as? NoteImagePlaceholderDrawable
    assertTrue("invalid syntax should be explicit", warning != null)
    assertTrue(requireNotNull(warning).message.contains(source))
  }

  @Test
  fun emptyDiagramShowsExplicitEmptyState() {
    val hostRef = AtomicReference<MermaidPreviewHost?>()
    composeRule.setContent { MermaidTestPreview("```mermaid\n```", hostRef) }

    val image = awaitImage(hostRef) {
      it is NoteImagePlaceholderDrawable && it.message.contains("Empty Mermaid diagram")
    }
    val warning = image.result as? NoteImagePlaceholderDrawable
    assertTrue("empty diagrams should remain visible", warning != null)
    val emptyMessage = InstrumentationRegistry.getInstrumentation().targetContext
      .getString(R.string.note_rich_mermaid_empty)
    assertTrue(requireNotNull(warning).message.contains(emptyMessage))
  }

  private fun awaitImage(
    hostRef: AtomicReference<MermaidPreviewHost?>,
    isFinal: (android.graphics.drawable.Drawable) -> Boolean,
  ): io.noties.markwon.image.AsyncDrawable {
    composeRule.waitUntil(timeoutMillis = 30_000) {
      hostRef.get()?.markdownView?.text?.let { text ->
        val spanned = text as? Spanned ?: return@let false
        mermaidSpans(spanned).any { it.drawable.result?.let(isFinal) == true }
      } == true
    }
    val spanned = requireNotNull(hostRef.get()).markdownView.text as Spanned
    return mermaidSpans(spanned).first { it.drawable.result?.let(isFinal) == true }.drawable
  }

  private fun mermaidSpans(spanned: Spanned): List<AsyncDrawableSpan> =
    spanned.getSpans(0, spanned.length, AsyncDrawableSpan::class.java)
      .filter { it.drawable.destination.startsWith("${MermaidSourceCodec.SCHEME}:") }

  private fun findMermaidHost(view: View): MermaidPreviewHost? {
    if (view is MermaidPreviewHost) return view
    if (view !is ViewGroup) return null
    for (index in 0 until view.childCount) {
      findMermaidHost(view.getChildAt(index))?.let { return it }
    }
    return null
  }

  @Composable
  private fun MermaidTestPreview(
    markdown: String,
    hostRef: AtomicReference<MermaidPreviewHost?>,
    widthFraction: Float = 1f,
    textColor: Int = Color.BLACK,
  ) {
    val context = LocalContext.current
    val coordinator = remember(context) { MermaidRenderCoordinator(context) }
    DisposableEffect(coordinator) { onDispose { coordinator.close() } }
    val markwon = remember(coordinator) {
      NoteMarkdownRenderer.createPreview(context, null, textColor, coordinator)
    }
    Column(Modifier.fillMaxSize()) {
      Text("Rendered Markdown")
      AndroidView(
        modifier = Modifier.weight(1f).fillMaxWidth(widthFraction).testTag("markdown_mermaid_host"),
        factory = { viewContext ->
          MermaidPreviewHost(viewContext, coordinator, textColor).also(hostRef::set)
        },
        update = { host -> host.setMarkdown(markwon, NotePreviewRewrite.forPreview(markdown), textColor) },
      )
    }
  }

}
