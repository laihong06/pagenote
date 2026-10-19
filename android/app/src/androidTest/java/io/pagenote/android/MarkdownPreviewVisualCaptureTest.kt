package io.pagenote.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.ext.latex.JLatexAsyncDrawableSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class MarkdownPreviewVisualCaptureTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun capturesRichLightMarkdownPreviewAndChecksMermaidRasterBudget() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val outputDirectory = requireNotNull(context.getExternalFilesDir(CAPTURE_DIRECTORY))
    outputDirectory.mkdirs()
    outputDirectory.listFiles()?.forEach { it.delete() }

    val fixture = MarkdownPreviewCaptureFixtures.richLight
    val hostRef = AtomicReference<MermaidPreviewHost?>()
    composeRule.setContent {
      val colorScheme = lightColorScheme()
      MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.background) {
          Box(Modifier.fillMaxSize()) {
            NotePanel(
              page = 1,
              markdown = fixture.markdown,
              dirty = false,
              writable = true,
              saving = false,
              status = null,
              onMarkdownChange = {},
              onSave = {},
              onReload = {},
              animationEnabled = false,
              modifier = Modifier.fillMaxWidth(0.46f).fillMaxHeight(),
            )
          }
        }
      }
    }
    composeRule.onNodeWithText(context.getString(R.string.note_preview)).performClick()
    composeRule.waitForIdle()

    composeRule.activityRule.scenario.onActivity { activity ->
      hostRef.set(findMermaidHost(activity.window.decorView))
    }
    awaitPreviewReady(hostRef)
    val host = requireNotNull(hostRef.get())
    val file = captureAfterDiagramLayout(host, outputDirectory)
    val renderedText = host.markdownView.text as Spanned
    assertTrue(renderedText.toString().contains(fixture.expectedText))

    assertTrue("expected a PNG capture for ${fixture.name}: $file", file.isFile)
    val decoded = BitmapFactory.decodeFile(file.absolutePath)
    assertTrue("${fixture.name} must be a readable PNG", decoded != null)
    val screenshot = requireNotNull(decoded)
    val content = composeRule.activity.findViewById<View>(android.R.id.content)
    val systemBars = requireNotNull(composeRule.activity.window.decorView.rootWindowInsets)
      .getInsets(WindowInsets.Type.systemBars())
    assertEquals("${fixture.name} must capture the full preview width", content.width, screenshot.width)
    assertEquals(
      "${fixture.name} must exclude status and navigation bars",
      content.height - systemBars.top - systemBars.bottom,
      screenshot.height,
    )
    screenshot.recycle()

    assertMermaidRasterizationStaysWithinPixelBudget()
  }

  private fun awaitPreviewReady(
    hostRef: AtomicReference<MermaidPreviewHost?>,
  ) {
    var lastState = "preview host not attached"
    try {
      composeRule.waitUntil(timeoutMillis = 30_000) {
        val host = hostRef.get() ?: return@waitUntil false
        val text = host.markdownView.text as? Spanned ?: run {
          lastState = "host text is not Spanned: ${host.markdownView.text.javaClass.name}"
          return@waitUntil false
        }
        val spans = text.getSpans(0, text.length, AsyncDrawableSpan::class.java)
        val formulas = text.getSpans(0, text.length, JLatexAsyncDrawableSpan::class.java)
        val diagrams = spans.filter { it.drawable.destination.startsWith("${MermaidSourceCodec.SCHEME}:") }
        val images = spans.filter { span ->
          span !is JLatexAsyncDrawableSpan && !span.drawable.destination.startsWith("${MermaidSourceCodec.SCHEME}:")
        }
        val imagePlaceholders = images.count { it.drawable.result is NoteImagePlaceholderDrawable }
        lastState = "async=${spans.map { it.drawable.hasResult() }}, formulas=${formulas.size}/2, " +
          "images=${images.size}, imagePlaceholders=$imagePlaceholders/2, " +
          "diagrams=${diagrams.map { it.drawable.result?.javaClass?.simpleName }}"
        spans.isNotEmpty() && spans.all { it.drawable.hasResult() } &&
          formulas.size == 2 && formulas.all { (it as AsyncDrawableSpan).drawable.hasResult() } &&
          formulas.none { (it as AsyncDrawableSpan).drawable.result is FormulaFallbackDrawable } &&
          imagePlaceholders == 2 && diagrams.size == 1 &&
          diagrams.single().drawable.result?.let { it !is NoteImagePlaceholderDrawable } == true
      }
    } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
      throw AssertionError("Rich Markdown preview did not settle: $lastState", failure)
    }
    composeRule.waitForIdle()
  }

  private fun findMermaidHost(view: View): MermaidPreviewHost? {
    if (view is MermaidPreviewHost) return view
    if (view !is ViewGroup) return null
    for (index in 0 until view.childCount) {
      findMermaidHost(view.getChildAt(index))?.let { return it }
    }
    return null
  }

  private fun captureAfterDiagramLayout(
    host: MermaidPreviewHost,
    outputDirectory: File,
  ): File {
    awaitMermaidFrame(host)

    composeRule.waitUntil(timeoutMillis = 5_000) {
      val text = host.markdownView.text as? Spanned ?: return@waitUntil false
      val span = mermaidSpans(text).singleOrNull() ?: return@waitUntil false
      val layout = host.markdownView.layout ?: return@waitUntil false
      val start = text.getSpanStart(span)
      val result = span.drawable.result ?: return@waitUntil false
      if (start < 0) return@waitUntil false
      val bounds = span.drawable.bounds
      val line = layout.getLineForOffset(start)
      result.intrinsicWidth > 10 && result.intrinsicHeight > 10 && bounds.height() > 0 &&
        layout.getLineBottom(line) - layout.getLineTop(line) >= bounds.height()
    }
    val screenshotFile = captureContentView("rich-light", outputDirectory)

    val text = host.markdownView.text as Spanned
    val span = mermaidSpans(text).single()
    val layout = requireNotNull(host.markdownView.layout)
    val spanBounds = Rect(span.drawable.bounds)
    val start = text.getSpanStart(span)
    val line = layout.getLineForOffset(start)
    val left = host.markdownView.totalPaddingLeft + layout.getPrimaryHorizontal(start).toInt() -
      host.markdownView.scrollX
    val top = host.markdownView.extendedPaddingTop + layout.getLineBottom(line) - spanBounds.bottom -
      host.markdownView.scrollY
    val hostBitmap = Bitmap.createBitmap(host.markdownView.width, host.markdownView.height, Bitmap.Config.ARGB_8888)
    composeRule.runOnIdle { host.markdownView.draw(Canvas(hostBitmap)) }
    var hostPixels = 0
    for (y in top.coerceAtLeast(0) until (top + spanBounds.height()).coerceAtMost(hostBitmap.height)) {
      for (x in left.coerceAtLeast(0) until (left + spanBounds.width()).coerceAtMost(hostBitmap.width)) {
        if (Color.alpha(hostBitmap.getPixel(x, y)) > 0) hostPixels++
      }
    }
    hostBitmap.recycle()
    assertTrue(
      "Mermaid drawable must paint into its TextView span; " +
        "host=${host.markdownView.width}x${host.markdownView.height}, bounds=$spanBounds at $left,$top",
      hostPixels > 4,
    )
    return screenshotFile
  }

  private fun awaitMermaidFrame(host: MermaidPreviewHost) {
    val drawn = AtomicBoolean(false)
    val observer = host.viewTreeObserver
    val listener = ViewTreeObserver.OnDrawListener {
      val text = host.markdownView.text as? Spanned
      val span = text?.let(::mermaidSpans)?.singleOrNull()
      val result = span?.drawable?.result
      if (result != null && result !is NoteImagePlaceholderDrawable) {
        drawn.set(true)
      }
    }
    observer.addOnDrawListener(listener)
    try {
      host.invalidate()
      composeRule.waitUntil(timeoutMillis = 5_000) { drawn.get() }
    } finally {
      if (observer.isAlive) observer.removeOnDrawListener(listener)
    }
    composeRule.waitForIdle()
  }

  private fun mermaidSpans(text: Spanned): List<AsyncDrawableSpan> =
    text.getSpans(0, text.length, AsyncDrawableSpan::class.java).filter {
      it.drawable.destination.startsWith("${MermaidSourceCodec.SCHEME}:")
    }

  private fun assertMermaidRasterizationStaysWithinPixelBudget() {
    val svg = """
      <svg xmlns="http://www.w3.org/2000/svg" width="1001" height="4200" viewBox="0 0 1001 4200">
        <rect width="1001" height="4200" fill="#663399" />
      </svg>
    """.trimIndent()
    val drawable = MermaidSvgMediaDecoder(composeRule.activity) { 5000 }
      .decode(MermaidSvgMediaDecoder.CONTENT_TYPE, svg.byteInputStream()) as BitmapDrawable

    assertTrue(drawable.intrinsicWidth <= 5000)
    assertTrue(drawable.bitmap.width.toLong() * drawable.bitmap.height <= 4_194_304L)

    val tallSvg = """
      <svg xmlns="http://www.w3.org/2000/svg" width="1" height="20000" viewBox="0 0 1 20000">
        <rect width="1" height="20000" fill="#663399" />
      </svg>
    """.trimIndent()
    val tallDrawable = MermaidSvgMediaDecoder(composeRule.activity) { 5000 }
      .decode(MermaidSvgMediaDecoder.CONTENT_TYPE, tallSvg.byteInputStream()) as BitmapDrawable
    assertTrue("long Mermaid rasters must cap their longest edge", tallDrawable.bitmap.height <= 16_384)
  }

  private fun captureContentView(name: String, outputDirectory: File): File {
    val content = composeRule.activity.findViewById<View>(android.R.id.content)
    val display = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
    val location = IntArray(2)
    content.getLocationOnScreen(location)
    val systemBars = requireNotNull(composeRule.activity.window.decorView.rootWindowInsets)
      .getInsets(WindowInsets.Type.systemBars())
    val cropTop = location[1] + systemBars.top
    val cropHeight = content.height - systemBars.top - systemBars.bottom
    val bitmap = Bitmap.createBitmap(display, location[0], cropTop, content.width, cropHeight)
    val file = File(outputDirectory, "$name.png")
    file.outputStream().use { output ->
      check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
        "Android could not encode the $name preview capture"
      }
    }
    bitmap.recycle()
    if (display !== bitmap) display.recycle()
    return file
  }

  private companion object {
    const val CAPTURE_DIRECTORY = "markdown-preview-captures"
  }
}
