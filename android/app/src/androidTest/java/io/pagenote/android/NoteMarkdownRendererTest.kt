package io.pagenote.android

import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.ext.latex.JLatexAsyncDrawableSpan
import io.noties.markwon.ext.tasklist.TaskListSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class NoteMarkdownRendererTest {
  @Test
  fun gfmBasicsRenderStrikethroughAndTaskStates() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val markdown = "~~retired~~\n\n- [ ] Open\n- [x] Started\n- [X] Done"
    val renderer = NoteMarkdownRenderer.createPreview(context, null, Color.BLACK)
    val rendered = renderer.render(renderer.parse(markdown)) as Spanned
    val spans = rendered.getSpans(0, rendered.length, Any::class.java)
    val taskSpans = rendered.getSpans(0, rendered.length, TaskListSpan::class.java)

    assertTrue("GFM deletion must render as a native strike span", spans.any {
      it is android.text.style.StrikethroughSpan
    })
    assertEquals("all task items must render as checkbox spans", 3, taskSpans.size)
    assertEquals(
      "unchecked, lowercase x, and uppercase X states must be preserved",
      listOf(false, true, true),
      taskSpans.sortedBy(rendered::getSpanStart).map(TaskListSpan::isDone),
    )

    val editorMarkdown = "~~editable strike~~\n\n- [ ] todo"
    val editorSource = SpannableStringBuilder(editorMarkdown)
    NoteMarkdownRenderer.createEditor(context, null).process(editorSource)
    assertEquals("source editing must preserve GFM markers", editorMarkdown, editorSource.toString())
  }

  @Test
  fun previewCreatesLatexDrawableAndPrismSyntaxSpans() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val markdown = NotePreviewRewrite.forPreview(
      "Inline \$x^2\$ and block:\n\n\$\$\ny = x + 1\n\$\$\n\n```python3\nprint('hello')\n```",
    )

    val renderer = NoteMarkdownRenderer.createPreview(context, null, Color.BLACK)
    val rendered = renderer.render(renderer.parse(markdown))
    val spanned: Spanned = rendered
    val spans = spanned.getSpans(0, spanned.length, Any::class.java)

    assertTrue("LaTeX source must become an async drawable span", spans.any {
      it.javaClass.name.contains("JLatexAsyncDrawableSpan")
    })
    assertTrue("known code language must create syntax color spans", spans.any {
      it is ForegroundColorSpan
    })
  }

  @Test
  fun inlineAndBlockFormulasDrawVisiblePixels() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val markdown = NotePreviewRewrite.forPreview(
      "Inline \$x^2\$ and block:\n\n\$\$\ny = x + 1\n\$\$",
    )
    val renderer = NoteMarkdownRenderer.createPreview(context, null, Color.BLACK)
    val rendered = renderer.render(renderer.parse(markdown)) as Spanned
    val formulas = rendered.getSpans(0, rendered.length, JLatexAsyncDrawableSpan::class.java)

    assertEquals("both inline and block formulas must render", 2, formulas.size)
    formulas.forEach { span ->
      val drawable = (span as AsyncDrawableSpan).drawable
      awaitLatexDrawable(drawable)

      assertTrue("LaTeX rendering must complete", drawable.hasResult())
      assertFalse("valid formula must not use error fallback", drawable.result is FormulaFallbackDrawable)
      assertTrue(
        "valid formula must resolve to JLatexMathDrawable, got ${drawable.result.javaClass.name}",
        drawable.result.javaClass.name.startsWith("ru.noties.jlatexmath."),
      )
      assertDrawableHasVisiblePixels(drawable.result)
    }
  }

  @Test
  fun invalidFormulaUsesRendererFallbackAndPreservesFollowingText() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val source = "\\notARealCommand{x}"
    val markdown = "Before formula:\n\n\$\$\n$source\n\$\$\n\nAfter formula."
    val renderer = NoteMarkdownRenderer.createPreview(context, null, Color.BLACK)
    val rendered = renderer.render(renderer.parse(markdown)) as Spanned
    val formula = rendered.getSpans(0, rendered.length, JLatexAsyncDrawableSpan::class.java).single()
    val drawable = (formula as AsyncDrawableSpan).drawable

    awaitLatexDrawable(drawable)

    assertTrue("invalid TeX must complete through the renderer error handler", drawable.hasResult())
    val fallback = drawable.result as? FormulaFallbackDrawable
    assertTrue(
      "renderer error handler must return FormulaFallbackDrawable, got ${drawable.result.javaClass.name}",
      fallback != null,
    )
    assertTrue("fallback must retain the original formula source", requireNotNull(fallback).source.contains(source))
    assertDrawableHasVisiblePixels(fallback)
    assertTrue("content after the formula must remain rendered", rendered.toString().contains("After formula."))
  }

  @Test
  fun unknownCodeLanguageKeepsItsSourceText() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val markdown = "```unknown-language\nsource stays visible\n```"

    val renderer = NoteMarkdownRenderer.createPreview(context, null, Color.BLACK)
    val rendered = renderer.render(renderer.parse(markdown))

    assertTrue(rendered.contains("source stays visible"))
  }

  @Test
  fun syntaxThemeContrastFollowsSurfaceTextColor() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val markdown = "```python\nprint('hello')\n```"
    val lightSurfaceColors = codeSpanColors(context, markdown, Color.BLACK)
    val darkSurfaceColors = codeSpanColors(context, markdown, Color.WHITE)

    assertTrue(lightSurfaceColors.isNotEmpty())
    assertTrue(darkSurfaceColors.isNotEmpty())
    assertTrue(
      "light surfaces need the darker syntax palette",
      lightSurfaceColors.map(Color::luminance).average() < darkSurfaceColors.map(Color::luminance).average(),
    )
  }

  @Test
  fun formulaFallbackShowsAndPreservesTheUnrenderedSource() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val source = "\\notARealCommand{x}"
    val fallback = FormulaFallbackDrawable(context, source, Color.BLACK)

    assertEquals(source, fallback.source)
    assertTrue(fallback.intrinsicWidth > 0)
    assertTrue(fallback.intrinsicHeight > 0)
  }

  @Test
  fun missingProjectImageUsesVisiblePlaceholderInsteadOfTransparentDrawable() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val renderer = NoteMarkdownRenderer.createPreview(context, null, Color.BLACK)
    val source = "deadbeef.png"
    val rendered = renderer.render(renderer.parse("![missing](pagenote-asset://$source)")) as Spanned
    val image = rendered.getSpans(0, rendered.length, AsyncDrawableSpan::class.java).single().drawable

    loadAndAwaitImage(renderer, image)

    assertTrue("image handler must return an explicit placeholder", image.hasResult())
    assertVisiblePlaceholder(
      image.result,
      context.getString(R.string.note_rich_image_missing, source),
    )
  }

  @Test
  fun undecodableInlineImageUsesVisiblePlaceholder() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val source = "data:image/png;base64,AAAA"
    val renderer = NoteMarkdownRenderer.createPreview(context, null, Color.BLACK)
    val rendered = renderer.render(renderer.parse("![broken]($source)")) as Spanned
    val image = rendered.getSpans(0, rendered.length, AsyncDrawableSpan::class.java).single().drawable

    loadAndAwaitImage(renderer, image)

    assertTrue("image decoding should complete", image.hasResult())
    assertVisiblePlaceholder(
      image.result,
      context.getString(R.string.note_rich_image_decode_failed, "image/png"),
    )
  }

  @Test
  fun unreadableImageStreamUsesVisiblePlaceholder() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val decoder = NoteImageMediaDecoder(context, Color.BLACK)
    val unreadable = object : InputStream() {
      override fun read(): Int = throw IOException("private provider detail")

      override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        throw IOException("private provider detail")
    }

    val drawable = decoder.decode("image/png", unreadable)

    assertVisiblePlaceholder(
      drawable,
      context.getString(R.string.note_rich_image_decode_failed, "image/png"),
    )
  }

  @Test
  fun remoteHttpAndHttpsImagesUsePlaceholdersWithoutConnecting() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    server.soTimeout = 1_500
    val requestObserved = CountDownLatch(1)
    val requestCount = AtomicInteger()
    val serverThread = thread(name = "offline-image-probe") {
      try {
        server.accept().use {
          requestCount.incrementAndGet()
          requestObserved.countDown()
        }
      } catch (_: SocketTimeoutException) {
        // No request is the expected offline behavior.
      } catch (_: IOException) {
        // Closing the server after the assertion is expected when no request arrived.
      }
    }
    try {
      val renderer = NoteMarkdownRenderer.createPreview(context, null, Color.BLACK)
      val urls = listOf(
        "http://127.0.0.1:${server.localPort}/image.png?token=private",
        "https://127.0.0.1:${server.localPort}/image.png?token=private",
      )
      val images = urls.map { url ->
        val rendered = renderer.render(renderer.parse("![remote]($url)")) as Spanned
        rendered.getSpans(0, rendered.length, AsyncDrawableSpan::class.java).single().drawable.also {
          loadAndAwaitImage(renderer, it)
        }
      }

      images.zip(urls).forEach { (image, url) ->
        assertTrue("remote image handling should complete synchronously", image.hasResult())
        val placeholder = assertVisiblePlaceholder(
          image.result,
          context.getString(R.string.note_rich_image_remote, "127.0.0.1/image.png"),
        )
        assertFalse("query parameters must not be exposed", placeholder.message.contains("private"))
      }
      assertFalse(
        "HTTP and HTTPS image rendering must not initiate connections",
        requestObserved.await(750, TimeUnit.MILLISECONDS),
      )
      assertEquals("neither scheme may open a socket", 0, requestCount.get())
    } finally {
      server.close()
      serverThread.join(2_000)
    }
  }

  @Test
  fun unsupportedImageSchemesDoNotExposeLocalPathsOrQueryData() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val renderer = NoteMarkdownRenderer.createPreview(context, null, Color.BLACK)
    val sources = listOf(
      "file:///private/course/lesson.png?token=secret#page=2",
      "data:image/png;base64,PRIVATE-PAYLOAD-DO-NOT-SHOW",
    )

    sources.forEach { source ->
      val rendered = renderer.render(renderer.parse("![unsafe]($source)")) as Spanned
      val image = rendered.getSpans(0, rendered.length, AsyncDrawableSpan::class.java).single().drawable

      loadAndAwaitImage(renderer, image)

      assertTrue("unsupported schemes must finish with a visible warning", image.hasResult())
      val placeholder = image.result as? NoteImagePlaceholderDrawable
      assertTrue("unsupported schemes must use a warning placeholder", placeholder != null)
      val message = requireNotNull(placeholder).message
      assertFalse(message.contains("/private/course"))
      assertFalse(message.contains("token=secret"))
      assertFalse(message.contains("page=2"))
      assertFalse(message.contains("PRIVATE-PAYLOAD"))
    }
  }

  private fun codeSpanColors(context: android.content.Context, markdown: String, textColor: Int): List<Int> {
    val renderer = NoteMarkdownRenderer.createPreview(context, null, textColor)
    val rendered = renderer.render(renderer.parse(markdown)) as Spanned
    return rendered.getSpans(0, rendered.length, ForegroundColorSpan::class.java)
      .map { it.foregroundColor }
  }

  private fun assertVisiblePlaceholder(
    drawable: Drawable,
    expectedMessage: String,
  ): NoteImagePlaceholderDrawable {
    val placeholder = drawable as? NoteImagePlaceholderDrawable
    assertTrue("image failure must return a warning placeholder", placeholder != null)
    val image = requireNotNull(placeholder)
    assertEquals(expectedMessage, image.message)
    assertTrue(image.intrinsicWidth > 0)
    assertTrue(image.intrinsicHeight > 0)

    val bitmap = Bitmap.createBitmap(image.intrinsicWidth, image.intrinsicHeight, Bitmap.Config.ARGB_8888)
    image.setBounds(0, 0, bitmap.width, bitmap.height)
    image.draw(Canvas(bitmap))
    val background = Color.rgb(255, 246, 220)
    val border = Color.rgb(225, 188, 111)
    var glyphPixels = 0
    for (y in 0 until bitmap.height) {
      for (x in 0 until bitmap.width) {
        val color = bitmap.getPixel(x, y)
        if (color != Color.TRANSPARENT && color != background && color != border) glyphPixels++
      }
    }
    assertTrue("warning text must be drawn, not just reserve space", glyphPixels > 4)
    return image
  }

  private fun assertDrawableHasVisiblePixels(drawable: Drawable) {
    val width = drawable.intrinsicWidth
    val height = drawable.intrinsicHeight
    assertTrue("rendered formula must report a positive width", width > 0)
    assertTrue("rendered formula must report a positive height", height > 0)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(Canvas(bitmap))

    var visiblePixels = 0
    for (y in 0 until height) {
      for (x in 0 until width) {
        if (Color.alpha(bitmap.getPixel(x, y)) > 0) visiblePixels++
      }
    }
    assertTrue("formula drawable must draw actual pixels", visiblePixels > 4)
  }

  private fun loadAndAwaitImage(
    renderer: io.noties.markwon.Markwon,
    image: io.noties.markwon.image.AsyncDrawable,
  ) {
    image.setCallback2(object : Drawable.Callback {
      override fun invalidateDrawable(who: Drawable) = Unit
      override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = Unit
      override fun unscheduleDrawable(who: Drawable, what: Runnable) = Unit
    })
    renderer.configuration().asyncDrawableLoader().load(image)

    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val deadline = android.os.SystemClock.uptimeMillis() + 2_000
    while (!image.hasResult() && android.os.SystemClock.uptimeMillis() < deadline) {
      instrumentation.waitForIdleSync()
      Thread.sleep(10)
    }
  }

  private fun awaitLatexDrawable(drawable: AsyncDrawable) {
    drawable.setCallback2(object : Drawable.Callback {
      override fun invalidateDrawable(who: Drawable) = Unit
      override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = Unit
      override fun unscheduleDrawable(who: Drawable, what: Runnable) = Unit
    })
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val deadline = android.os.SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(10)
    while (!drawable.hasResult() && android.os.SystemClock.uptimeMillis() < deadline) {
      instrumentation.waitForIdleSync()
      Thread.sleep(10)
    }
  }
}
