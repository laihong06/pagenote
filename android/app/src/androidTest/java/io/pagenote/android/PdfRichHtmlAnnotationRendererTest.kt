package io.pagenote.android

import android.widget.FrameLayout
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PdfRichHtmlAnnotationRendererTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun sanitizesRichHtmlRendersBitmapAndBlocksExternalResources() {
    val rendererRef = AtomicReference<RichHtmlAnnotationRenderer>()
    val hostRef = AtomicReference<FrameLayout>()
    composeRule.runOnUiThread {
      val host = FrameLayout(composeRule.activity).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
      }
      composeRule.activity.addContentView(host, ViewGroup.LayoutParams(1, 1))
      hostRef.set(host)
      rendererRef.set(RichHtmlAnnotationRenderer(composeRule.activity).also { it.attachTo(host) })
    }
    val renderer = rendererRef.get()
    try {
      val rendered = runBlocking {
        withContext(Dispatchers.IO) {
          renderer.render(
            html = "<h2 onclick=\"run()\">Rich <b>note</b></h2>" +
              "<script>document.title='owned'</script>" +
              "<a href=\"javascript:run()\">unsafe</a>" +
              "<img src=\"https://example.invalid/pixel.png\">",
            widthCssPx = 420,
            heightCssPx = null,
          )
        }
      }

      assertTrue(rendered.sanitizedHtml.contains("<b>note</b>"))
      assertFalse(rendered.sanitizedHtml.contains("script", ignoreCase = true))
      assertFalse(rendered.sanitizedHtml.contains("onclick", ignoreCase = true))
      assertFalse(rendered.sanitizedHtml.contains("javascript:", ignoreCase = true))
      assertTrue(rendered.contentHeightPx > 0)
      assertNotNull(rendered.bitmap)
      assertTrue((0 until rendered.bitmap.height).any { y ->
        (0 until rendered.bitmap.width).any { x -> (rendered.bitmap.getPixel(x, y) ushr 24) != 0 }
      })
      assertTrue("remote resources must be blocked", renderer.blockedExternalRequestCount > 0)
      assertTrue(rendered.bitmap.width.toLong() * rendered.bitmap.height <= RichHtmlAnnotationRenderer.MAX_PAGE_PIXELS)
      assertTrue(
        runCatching {
          renderer.render("x".repeat(RichHtmlAnnotationRenderer.MAX_HTML_CHARS + 1), 500, null)
        }.exceptionOrNull() is IllegalArgumentException,
      )
    } finally {
      composeRule.runOnUiThread {
        renderer.close()
        (hostRef.get().parent as? ViewGroup)?.removeView(hostRef.get())
      }
    }
  }
}
