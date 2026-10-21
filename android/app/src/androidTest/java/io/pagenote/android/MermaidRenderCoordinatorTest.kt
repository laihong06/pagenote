package io.pagenote.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class MermaidRenderCoordinatorTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  private lateinit var coordinator: MermaidRenderCoordinator
  private lateinit var host: FrameLayout

  @Before
  fun attachRenderer() {
    composeRule.runOnUiThread {
      val activity = composeRule.activity
      host = FrameLayout(activity)
      activity.findViewById<ViewGroup>(android.R.id.content).addView(
        host,
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1),
      )
      coordinator = MermaidRenderCoordinator(activity)
      coordinator.attachTo(host)
    }
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
  }

  @After
  fun disposeRenderer() {
    composeRule.runOnUiThread {
      coordinator.close()
      (host.parent as? ViewGroup)?.removeView(host)
    }
  }

  @Test
  fun validDiagramProducesSanitizedSvgAndNativePixels() {
    val source = "graph TD\nA[Start] --> B[Finish]"
    val result = coordinator.render(source)

    assertTrue("expected SVG result, got $result", result is MermaidRenderCoordinator.Result.Ready)
    val svg = (result as MermaidRenderCoordinator.Result.Ready).svg
    assertTrue(svg.contains("<svg"))
    assertTrue(svg.contains("Start"))
    assertTrue(svg.contains("Finish"))
    assertFalse(svg.contains("<script", ignoreCase = true))
    assertFalse(svg.contains("onclick", ignoreCase = true))

    val drawable = MermaidSvgMediaDecoder(composeRule.activity) { 600 }
      .decode(MermaidSvgMediaDecoder.CONTENT_TYPE, ByteArrayInputStream(svg.toByteArray(StandardCharsets.UTF_8)))
    assertTrue(drawable.intrinsicWidth in 1..600)
    assertTrue(drawable.intrinsicHeight > 0)
    val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, bitmap.width, bitmap.height)
    drawable.draw(Canvas(bitmap))
    assertTrue("SVG should draw opaque pixels", bitmapPixels(bitmap, Color.TRANSPARENT) > 20)
  }

  @Test
  fun emptyAndInvalidSourceReturnExplicitStates() {
    assertEquals(MermaidRenderCoordinator.Result.Empty, coordinator.render(""))
    assertEquals(MermaidRenderCoordinator.Result.Failed, coordinator.render("not a Mermaid diagram"))
  }

  @Test
  fun quotedMarkupStaysDiagramTextAndCannotInjectIntoRenderer() {
    val source = "graph TD\nA[\"</script><img src=x onerror=alert(1)>\"] --> B[Safe]"

    val result = coordinator.render(source)

    assertTrue("expected diagram SVG, got $result", result is MermaidRenderCoordinator.Result.Ready)
    val svg = (result as MermaidRenderCoordinator.Result.Ready).svg
    assertFalse(svg.contains("<script", ignoreCase = true))
    assertFalse(svg.contains("onerror", ignoreCase = true))
    assertTrue(svg.contains("Safe"))
  }

  @Test
  fun concurrentSourceUpdatesAreSerializedAndKeepTheirOwnResults() {
    val pool = Executors.newFixedThreadPool(2)
    try {
      val first = pool.submit<MermaidRenderCoordinator.Result> {
        coordinator.render("graph TD\nA[First update] --> B[One]")
      }
      val second = pool.submit<MermaidRenderCoordinator.Result> {
        coordinator.render("graph TD\nA[Second update] --> B[Two]")
      }
      val firstSvg = (first.get(35, TimeUnit.SECONDS) as MermaidRenderCoordinator.Result.Ready).svg
      val secondSvg = (second.get(35, TimeUnit.SECONDS) as MermaidRenderCoordinator.Result.Ready).svg
      val firstRequestedSource = firstSvg.contains("First") && !firstSvg.contains("Second")
      val secondRequestedSource = secondSvg.contains("Second") && !secondSvg.contains("First")
      assertTrue("first render did not match its source: ${firstSvg.take(600)}", firstRequestedSource)
      assertTrue("second render did not match its source: ${secondSvg.take(600)}", secondRequestedSource)
    } finally {
      pool.shutdownNow()
    }
  }

  @Test
  fun rendererBlocksNetworkAndLocalFileCapabilities() {
    val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    server.soTimeout = 1_500
    val requestCount = AtomicInteger()
    val accepted = CountDownLatch(1)
    val serverThread = thread(name = "mermaid-network-guard") {
      try {
        server.accept().use {
          requestCount.incrementAndGet()
          accepted.countDown()
        }
      } catch (_: SocketTimeoutException) {
        Unit
      } catch (_: java.io.IOException) {
        Unit
      }
    }
    try {
      assertTrue(coordinator.render("graph TD\nA[Local] --> B[Only]") is MermaidRenderCoordinator.Result.Ready)
      composeRule.runOnUiThread {
        val url = "http://127.0.0.1:${server.localPort}/probe"
        coordinator.webView.evaluateJavascript("fetch(${org.json.JSONObject.quote(url)}).catch(() => {});", null)
      }
      assertFalse("renderer attempted a localhost request", accepted.await(2, TimeUnit.SECONDS))
      assertEquals(0, requestCount.get())
      composeRule.runOnUiThread {
        assertFalse(coordinator.webView.settings.allowFileAccess)
        assertFalse(coordinator.webView.settings.allowContentAccess)
        assertTrue(coordinator.webView.settings.blockNetworkLoads)
      }
    } finally {
      server.close()
      serverThread.join(2_000)
    }
  }

  @Test
  fun overLimitSourceFailsBeforeEnteringWebView() {
    val result = coordinator.render("x".repeat(MermaidSourceCodec.MAX_SOURCE_CHARS + 1))

    assertEquals(MermaidRenderCoordinator.Result.Failed, result)
  }

  private fun bitmapPixels(bitmap: Bitmap, skipColor: Int): Int {
    var count = 0
    for (y in 0 until bitmap.height) {
      for (x in 0 until bitmap.width) if (bitmap.getPixel(x, y) != skipColor) count++
    }
    return count
  }
}
