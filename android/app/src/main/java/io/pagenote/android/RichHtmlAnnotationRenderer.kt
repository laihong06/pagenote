package io.pagenote.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class RenderedRichHtmlAnnotation(
  val sanitizedHtml: String,
  val bitmap: Bitmap,
  val contentHeightPx: Int,
)

internal class RichHtmlAnnotationRenderer(context: Context) : AutoCloseable {
  private data class Pending(
    val requestId: String,
    val widthCssPx: Int,
    val latch: CountDownLatch = CountDownLatch(1),
    @Volatile var result: RenderedRichHtmlAnnotation? = null,
    @Volatile var failure: Throwable? = null,
    var snapshotStarted: Boolean = false,
  )

  private data class RenderPayload(
    val requestId: String,
    val sanitizedHtml: String,
    val contentHeightPx: Int,
    val renderHeightCssPx: Int,
    val blockedExternalCount: Int,
  )

  private val appContext = context.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())
  private val renderLock = Any()
  private val requestLock = Any()
  private val requestCounter = AtomicInteger()
  private val pageLoaded = CountDownLatch(1)
  private val blockedRequests = AtomicInteger()
  private val assetLoader = WebViewAssetLoader.Builder()
    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(appContext))
    .build()
  private val allowedAssetPaths = setOf(
    "/assets/rich-annotation/renderer.html",
    "/assets/rich-annotation/renderer.js",
    "/assets/mermaid/purify.min.js",
  )

  @Volatile private var disposed = false
  @Volatile private var pageLoadFailed = false
  @Volatile private var active: Pending? = null

  val webView: WebView

  val blockedExternalRequestCount: Int
    get() = blockedRequests.get()

  fun attachTo(host: FrameLayout) {
    check(Looper.myLooper() == Looper.getMainLooper()) { "WebView must be attached on the main thread" }
    host.clipChildren = false
    host.clipToPadding = false
    (webView.parent as? ViewGroup)?.removeView(webView)
    host.addView(webView, FrameLayout.LayoutParams(1, 1))
  }

  fun render(html: String, widthCssPx: Int, heightCssPx: Int?): RenderedRichHtmlAnnotation {
    require(html.length <= MAX_HTML_CHARS) { "HTML annotation is too large" }
    require(widthCssPx in 1..MAX_SIDE_PX) { "HTML annotation width is outside the rendering limit" }
    require(heightCssPx == null || heightCssPx in 1..MAX_SIDE_PX) {
      "HTML annotation height is outside the rendering limit"
    }
    check(Looper.myLooper() != Looper.getMainLooper()) { "HTML rendering must not block the main thread" }
    check(!disposed) { "HTML renderer is closed" }

    return synchronized(renderLock) {
      check(!disposed && !pageLoadFailed) { "HTML renderer could not be initialized" }
      check(pageLoaded.await(PAGE_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        "HTML renderer did not finish loading its local assets"
      }
      val request = Pending(requestCounter.incrementAndGet().toString(), widthCssPx)
      synchronized(requestLock) { active = request }
      mainHandler.post {
        if (disposed || synchronized(requestLock) { active !== request }) {
          finish(request, IllegalStateException("HTML renderer is no longer available"))
        } else {
          webView.evaluateJavascript(
            "window.renderPagenoteRichHtml(" +
              "${JSONObject.quote(html)},$widthCssPx,${heightCssPx ?: "null"},${JSONObject.quote(request.requestId)});",
            null,
          )
        }
      }
      if (!request.latch.await(RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        synchronized(requestLock) { if (active === request) active = null }
        throw IllegalStateException("HTML annotation rendering timed out")
      }
      request.failure?.let { throw IllegalStateException("HTML annotation rendering failed", it) }
      request.result ?: throw IllegalStateException("HTML annotation rendering failed")
    }
  }

  override fun close() {
    if (disposed) return
    disposed = true
    val pending = synchronized(requestLock) { active }
    pending?.let { finish(it, IllegalStateException("HTML renderer is closed")) }
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
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse {
      val uri = request.url
      if (
        uri.scheme == "https" && uri.host == ASSET_HOST && uri.encodedQuery == null &&
        uri.encodedFragment == null && uri.encodedPath in allowedAssetPaths
      ) {
        return assetLoader.shouldInterceptRequest(uri) ?: blockedResponse()
      }
      blockedRequests.incrementAndGet()
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
      if (request?.isForMainFrame == true) failInitialization()
    }

    override fun onReceivedHttpError(
      view: WebView?,
      request: WebResourceRequest?,
      errorResponse: WebResourceResponse?,
    ) {
      if (request?.isForMainFrame == true) failInitialization()
    }
  }

  private val rendererChromeClient = object : WebChromeClient() {
    override fun onReceivedTitle(view: WebView?, title: String?) {
      val match = TITLE_PATTERN.matchEntire(title.orEmpty()) ?: return
      val state = match.groupValues[1]
      val requestId = match.groupValues[2]
      val request = synchronized(requestLock) {
        active?.takeIf { it.requestId == requestId && !it.snapshotStarted }?.also {
          if (state == "ready") it.snapshotStarted = true
        }
      } ?: return
      if (state != "ready") {
        finish(request, IllegalStateException("HTML renderer rejected the annotation"))
      } else {
        retrievePayload(request)
      }
    }
  }

  init {
    check(Looper.myLooper() == Looper.getMainLooper()) { "WebView must be created on the main thread" }
    webView = WebView(context).apply {
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      isFocusable = false
      isFocusableInTouchMode = false
      isClickable = false
      isLongClickable = false
      setBackgroundColor(android.graphics.Color.TRANSPARENT)
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

  private fun retrievePayload(request: Pending) {
    webView.evaluateJavascript("JSON.stringify(window.__pagenoteRichHtmlResult)") { encoded ->
      val value = runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull()
      val json = runCatching { value?.let(::JSONObject) }.getOrNull()
      val payload = json?.takeIf { it.optString("requestId") == request.requestId }
        ?.takeIf { it.optString("state") == "ready" }
        ?.let {
          RenderPayload(
            requestId = it.optString("requestId"),
            sanitizedHtml = it.optString("sanitizedHtml"),
            contentHeightPx = it.optInt("contentHeightPx"),
            renderHeightCssPx = it.optInt("renderHeightCssPx"),
            blockedExternalCount = it.optInt("blockedExternalCount"),
          )
        }
      if (payload == null || payload.sanitizedHtml.length > MAX_HTML_CHARS) {
        finish(request, IllegalStateException("HTML renderer returned an invalid result"))
        return@evaluateJavascript
      }
      blockedRequests.addAndGet(payload.blockedExternalCount)
      snapshot(request, payload)
    }
  }

  private fun snapshot(request: Pending, payload: RenderPayload) {
    val density = webView.resources.displayMetrics.density
    val widthPx = ceil(request.widthCssPx * density).roundToInt().coerceAtLeast(1)
    val heightPx = ceil(payload.renderHeightCssPx * density).roundToInt().coerceAtLeast(1)
    if (widthPx > MAX_SIDE_PX || heightPx > MAX_SIDE_PX || widthPx.toLong() * heightPx > MAX_PAGE_PIXELS) {
      finish(request, IllegalStateException("HTML annotation exceeds the bitmap rendering limit"))
      return
    }
    webView.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
    webView.measure(
      View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
    )
    webView.layout(0, 0, widthPx, heightPx)
    webView.postVisualStateCallback(
      request.requestId.toLong(),
      object : WebView.VisualStateCallback() {
        override fun onComplete(requestId: Long) {
          if (disposed || synchronized(requestLock) { active !== request }) {
            finish(request, IllegalStateException("HTML rendering request became stale"))
            return
          }
          val bitmap = runCatching {
            Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also { output ->
              webView.draw(Canvas(output))
            }
          }.getOrElse {
            finish(request, it)
            return
          }
          finish(
            request,
            RenderedRichHtmlAnnotation(payload.sanitizedHtml, bitmap, payload.contentHeightPx),
          )
        }
      },
    )
  }

  private fun finish(request: Pending, result: RenderedRichHtmlAnnotation) {
    synchronized(requestLock) {
      if (active !== request) {
        if (!result.bitmap.isRecycled) result.bitmap.recycle()
        return
      }
      active = null
      request.result = result
      request.latch.countDown()
    }
  }

  private fun finish(request: Pending, failure: Throwable) {
    synchronized(requestLock) {
      if (active !== request) return
      active = null
      request.failure = failure
      request.latch.countDown()
    }
  }

  private fun failInitialization() {
    pageLoadFailed = true
    pageLoaded.countDown()
    synchronized(requestLock) { active }?.let {
      finish(it, IllegalStateException("HTML renderer failed to load"))
    }
  }

  private fun blockedResponse() = WebResourceResponse(
    "text/plain",
    "utf-8",
    403,
    "Blocked",
    emptyMap(),
    ByteArrayInputStream(ByteArray(0)),
  )

  companion object {
    const val MAX_HTML_CHARS = 100_000
    const val MAX_SIDE_PX = 16_384
    const val MAX_PAGE_PIXELS = 4_194_304L
    private const val ASSET_HOST = "appassets.androidplatform.net"
    private const val RENDERER_URL = "https://$ASSET_HOST/assets/rich-annotation/renderer.html"
    private const val PAGE_LOAD_TIMEOUT_SECONDS = 15L
    private const val RENDER_TIMEOUT_SECONDS = 40L
    private val TITLE_PATTERN = Regex("^pagenote-rich-html:(ready|error):([0-9]+)$")
  }
}
