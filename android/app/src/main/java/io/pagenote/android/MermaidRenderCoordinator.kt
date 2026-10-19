package io.pagenote.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class MermaidRenderCoordinator(context: Context) : AutoCloseable {
  sealed interface Result {
    data class Ready(val svg: String) : Result
    data object Empty : Result
    data object Failed : Result
  }

  private data class Pending(
    val requestId: String,
    val latch: CountDownLatch = CountDownLatch(1),
    @Volatile var result: Result? = null,
    var retrievingSvg: Boolean = false,
  )

  private val appContext = context.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())
  private val renderLock = Any()
  private val requestCounter = AtomicInteger()
  private val pageLoaded = CountDownLatch(1)
  private val requestLock = Any()
  private val assetLoader = WebViewAssetLoader.Builder()
    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(appContext))
    .build()
  private val allowedAssetPaths = setOf(
    "/assets/mermaid/renderer.html",
    "/assets/mermaid/renderer.js",
    "/assets/mermaid/mermaid.min.js",
    "/assets/mermaid/purify.min.js",
  )

  @Volatile private var disposed = false
  @Volatile private var pageLoadFailed = false
  @Volatile private var active: Pending? = null
  @Volatile var renderWidthPx: Int = 0
    private set

  val webView: WebView

  fun attachTo(host: FrameLayout) {
    check(Looper.myLooper() == Looper.getMainLooper()) { "WebView must be attached on the main thread" }
    (webView.parent as? ViewGroup)?.removeView(webView)
    host.addView(
      webView,
      FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1),
    )
    renderWidthPx = host.width
    host.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
      renderWidthPx = right - left
      if (renderWidthPx <= 0) renderWidthPx = oldRight - oldLeft
    }
  }

  fun render(source: String): Result {
    if (source.isEmpty()) return Result.Empty
    if (source.length > MermaidSourceCodec.MAX_SOURCE_CHARS) return Result.Failed
    if (Looper.myLooper() == Looper.getMainLooper() || disposed) return Result.Failed

    return synchronized(renderLock) {
      if (disposed || pageLoadFailed || !pageLoaded.await(PAGE_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        return@synchronized Result.Failed
      }
      val request = Pending(requestCounter.incrementAndGet().toString())
      synchronized(requestLock) { active = request }
      mainHandler.post {
        if (disposed || synchronized(requestLock) { active !== request }) {
          finish(request, Result.Failed)
        } else {
          val sourceArgument = JSONObject.quote(source)
          val idArgument = JSONObject.quote(request.requestId)
          webView.evaluateJavascript(
            "window.renderPagenoteMermaid($sourceArgument,$idArgument);",
            null,
          )
        }
      }
      if (!request.latch.await(RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        synchronized(requestLock) { if (active === request) active = null }
        Result.Failed
      } else {
        request.result ?: Result.Failed
      }
    }
  }

  override fun close() {
    if (disposed) return
    disposed = true
    val pending = synchronized(requestLock) {
      active
    }
    pending?.let { finish(it, Result.Failed) }
    pageLoaded.countDown()
    mainHandler.post {
      webView.stopLoading()
      webView.webChromeClient = null
      webView.webViewClient = WebViewClient()
      (webView.parent as? ViewGroup)?.removeView(webView)
      webView.destroy()
    }
  }

  private val rendererClient = object : WebViewClient() {
    override fun shouldInterceptRequest(
      view: WebView?,
      request: WebResourceRequest,
    ): WebResourceResponse {
      val uri = request.url
      if (
        uri.scheme == "https" &&
        uri.host == ASSET_HOST &&
        uri.encodedQuery == null &&
        uri.encodedFragment == null &&
        uri.encodedPath in allowedAssetPaths
      ) {
        return assetLoader.shouldInterceptRequest(uri) ?: blockedResponse()
      }
      return blockedResponse()
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean =
      request.url.toString() != RENDERER_URL

    override fun onPageFinished(view: WebView?, url: String?) {
      if (url == RENDERER_URL) pageLoaded.countDown()
    }

    override fun onReceivedError(
      view: WebView?,
      request: WebResourceRequest?,
      error: android.webkit.WebResourceError?,
    ) {
      if (request?.isForMainFrame == true) {
        pageLoadFailed = true
        pageLoaded.countDown()
        active?.let { finish(it, Result.Failed) }
      }
    }

    override fun onReceivedHttpError(
      view: WebView?,
      request: WebResourceRequest?,
      errorResponse: WebResourceResponse?,
    ) {
      if (request?.isForMainFrame == true) {
        pageLoadFailed = true
        pageLoaded.countDown()
        active?.let { finish(it, Result.Failed) }
      }
    }
  }

  private val rendererChromeClient = object : WebChromeClient() {
    override fun onReceivedTitle(view: WebView?, title: String?) {
      val match = TITLE_PATTERN.matchEntire(title.orEmpty()) ?: return
      val state = match.groupValues[1]
      val requestId = match.groupValues[2]
      val request = synchronized(requestLock) {
        active?.takeIf { it.requestId == requestId && !it.retrievingSvg }?.also {
          if (state == "ready") it.retrievingSvg = true
        }
      } ?: return

      when (state) {
        "ready" -> retrieveSvg(request)
        "empty" -> finish(request, Result.Empty)
        "error" -> finish(request, Result.Failed)
      }
    }
  }

  init {
    check(Looper.myLooper() == Looper.getMainLooper()) { "WebView must be created on the main thread" }
    webView = WebView(context).apply {
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      isFocusable = false
      isFocusableInTouchMode = false
      visibility = View.INVISIBLE
      settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        blockNetworkLoads = true
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
      }
      webViewClient = rendererClient
      webChromeClient = rendererChromeClient
      loadUrl(RENDERER_URL)
    }
  }

  private fun retrieveSvg(request: Pending) {
    webView.evaluateJavascript("JSON.stringify(window.__pagenoteMermaidResult)") { encoded ->
      val decoded = runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull()
      val result = runCatching { decoded?.let(::JSONObject) }.getOrNull()
      if (result == null || result.optString("requestId") != request.requestId) {
        finish(request, Result.Failed)
        return@evaluateJavascript
      }
      val svg = result.optString("svg")
      val validSize = svg.toByteArray(StandardCharsets.UTF_8).size <= MAX_SVG_BYTES
      finish(request, if (result.optString("state") == "ready" && svg.isNotBlank() && validSize) {
        Result.Ready(svg)
      } else {
        Result.Failed
      })
    }
  }

  private fun finish(request: Pending, result: Result) {
    synchronized(requestLock) {
      if (active !== request) return
      active = null
      request.result = result
      request.latch.countDown()
    }
  }

  private fun blockedResponse(): WebResourceResponse {
    return WebResourceResponse(
      "text/plain",
      "utf-8",
      403,
      "Blocked",
      emptyMap(),
      ByteArrayInputStream(ByteArray(0)),
    )
  }

  companion object {
    private const val ASSET_HOST = "appassets.androidplatform.net"
    private const val RENDERER_URL = "https://$ASSET_HOST/assets/mermaid/renderer.html"
    private const val PAGE_LOAD_TIMEOUT_SECONDS = 15L
    private const val RENDER_TIMEOUT_SECONDS = 30L
    private const val MAX_SVG_BYTES = 256 * 1024
    private val TITLE_PATTERN = Regex("pagenote-mermaid:(ready|error|empty):([0-9]+)")
  }
}
