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
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

/** One editor result snapshot reported by the contenteditable page. */
internal data class RichHtmlEditorState(
  /** DOMPurify-sanitized HTML of the current editor content (sanitize-out). */
  val html: String,
  val canUndo: Boolean,
  val canRedo: Boolean,
  val contentHeightPx: Int,
)

/**
 * Interactive contenteditable rich text editing window for canvas HTML
 * annotations (port batch 4), hosted in an offline WebView.
 *
 * Mirrors the security posture of [RichHtmlAnnotationRenderer] (assets-only
 * https://appassets.androidplatform.net origin, network loads blocked, no
 * file/content access) but keeps the WebView focusable so the user can type.
 * The page (assets/rich-annotation/editor.js) sanitizes HTML with DOMPurify in
 * BOTH directions — on load into the DOM and on every read-out — and tracks
 * undoable style actions internally; this controller only transports
 * load/command/undo/redo/retrieve requests and their results.
 *
 * All methods must be called on the main thread; callbacks are delivered on
 * the main thread.
 */
internal class RichHtmlEditorController(context: Context) : AutoCloseable {
  private data class Pending(
    val requestId: String,
    val callback: (Result<RichHtmlEditorState>) -> Unit,
  )

  private val appContext = context.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())
  private val requestLock = Any()
  private val requestCounter = AtomicInteger()
  private val assetLoader = WebViewAssetLoader.Builder()
    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(appContext))
    .build()
  private val allowedAssetPaths = setOf(
    "/assets/rich-annotation/editor.html",
    "/assets/rich-annotation/editor.js",
    "/assets/mermaid/purify.min.js",
  )

  @Volatile private var disposed = false
  @Volatile private var pageReady = false
  @Volatile private var pageLoadFailed = false
  private var active: Pending? = null
  private var pendingLoad: Triple<String, Boolean, (Result<RichHtmlEditorState>) -> Unit>? = null
  private var timeoutRunnable: Runnable? = null

  val webView: WebView

  private val editorClient = object : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse {
      val uri = request.url
      if (
        uri.scheme == "https" && uri.host == ASSET_HOST && uri.encodedQuery == null &&
        uri.encodedFragment == null && uri.encodedPath in allowedAssetPaths
      ) {
        return assetLoader.shouldInterceptRequest(uri) ?: blockedResponse()
      }
      return blockedResponse()
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean =
      request.url.toString() != EDITOR_URL

    override fun onPageFinished(view: WebView?, url: String?) {
      if (url == EDITOR_URL && !pageReady) {
        pageReady = true
        pendingLoad?.let { (html, editable, callback) ->
          pendingLoad = null
          loadHtml(html, editable, callback)
        }
      }
    }

    override fun onReceivedError(
      view: WebView?,
      request: WebResourceRequest?,
      error: android.webkit.WebResourceError?,
    ) {
      if (request?.isForMainFrame == true) failPageLoad()
    }

    override fun onReceivedHttpError(
      view: WebView?,
      request: WebResourceRequest?,
      errorResponse: WebResourceResponse?,
    ) {
      if (request?.isForMainFrame == true) failPageLoad()
    }
  }

  private val editorChromeClient = object : WebChromeClient() {
    override fun onReceivedTitle(view: WebView?, title: String?) {
      val match = TITLE_PATTERN.matchEntire(title.orEmpty()) ?: return
      val state = match.groupValues[1]
      val requestId = match.groupValues[2]
      if (state != "ready" && state != "error") return
      val pending = synchronized(requestLock) { active?.takeIf { it.requestId == requestId } } ?: return
      if (state == "error") {
        finish(pending, Result.failure(IllegalStateException("Rich text editor rejected the request")))
      } else {
        retrieveResult(pending)
      }
    }
  }

  init {
    check(Looper.myLooper() == Looper.getMainLooper()) { "Editor WebView must be created on the main thread" }
    webView = WebView(context).apply {
      setBackgroundColor(android.graphics.Color.WHITE)
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
      webViewClient = editorClient
      webChromeClient = editorChromeClient
      loadUrl(EDITOR_URL)
    }
  }

  /** Loads [html] into the editor; the page sanitizes it before it enters the DOM. */
  fun loadHtml(
    html: String,
    editable: Boolean,
    onResult: (Result<RichHtmlEditorState>) -> Unit,
  ) {
    checkReady(onResult)
    if (html.length > MAX_HTML_CHARS) {
      onResult(Result.failure(IllegalStateException("HTML annotation is too large")))
      return
    }
    if (!pageReady) {
      pendingLoad = Triple(html, editable, onResult)
      return
    }
    val requestId = nextRequestId()
    dispatch(
      "window.loadPagenoteRichEdit(${JSONObject.quote(html)},${if (editable) "true" else "false"},${JSONObject.quote(requestId)});",
      requestId,
      onResult,
    )
  }

  /** Applies one validated style action; each is a separate editor undo step. */
  fun runCommand(
    command: RichHtmlEditorCommand,
    onResult: (Result<RichHtmlEditorState>) -> Unit,
  ) {
    checkReady(onResult)
    val requestId = nextRequestId()
    dispatch(command.toEditorScript(requestId), requestId, onResult)
  }

  fun undo(onResult: (Result<RichHtmlEditorState>) -> Unit) {
    checkReady(onResult)
    val requestId = nextRequestId()
    dispatch("window.undoPagenoteRichEdit(${JSONObject.quote(requestId)});", requestId, onResult)
  }

  fun redo(onResult: (Result<RichHtmlEditorState>) -> Unit) {
    checkReady(onResult)
    val requestId = nextRequestId()
    dispatch("window.redoPagenoteRichEdit(${JSONObject.quote(requestId)});", requestId, onResult)
  }

  /**
   * Reads the editor content back; the page re-sanitizes it with DOMPurify
   * before returning (sanitize-out), completing the bidirectional contract.
   */
  fun confirmHtml(onResult: (Result<RichHtmlEditorState>) -> Unit) {
    checkReady(onResult)
    val requestId = nextRequestId()
    dispatch("window.retrievePagenoteRichEdit(${JSONObject.quote(requestId)});", requestId, onResult)
  }

  override fun close() {
    if (disposed) return
    disposed = true
    val pending = synchronized(requestLock) { active }
    pending?.let {
      finish(it, Result.failure(IllegalStateException("Rich text editor is closed")))
    }
    timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
    mainHandler.post {
      webView.stopLoading()
      webView.webChromeClient = null
      webView.webViewClient = WebViewClient()
      (webView.parent as? ViewGroup)?.removeView(webView)
      webView.destroy()
    }
  }

  private fun checkReady(onResult: (Result<RichHtmlEditorState>) -> Unit) {
    check(Looper.myLooper() == Looper.getMainLooper()) { "Editor calls must run on the main thread" }
    check(!disposed) { "Rich text editor is closed" }
    if (pageLoadFailed) {
      onResult(Result.failure(IllegalStateException("Rich text editor could not be initialized")))
    }
  }

  private fun dispatch(script: String, requestId: String, onResult: (Result<RichHtmlEditorState>) -> Unit) {
    val pending = Pending(requestId, onResult)
    synchronized(requestLock) { active = pending }
    val timeout = Runnable {
      val current = synchronized(requestLock) { active }
      if (current === pending) {
        finish(pending, Result.failure(IllegalStateException("Rich text editor timed out")))
      }
    }
    timeoutRunnable = timeout
    mainHandler.postDelayed(timeout, REQUEST_TIMEOUT_MS)
    webView.evaluateJavascript(script, null)
  }

  private fun nextRequestId(): String = requestCounter.incrementAndGet().toString()

  private fun retrieveResult(pending: Pending) {
    webView.evaluateJavascript("JSON.stringify(window.__pagenoteRichEditResult)") { encoded ->
      val value = runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull()
      val json = runCatching { value?.let(::JSONObject) }.getOrNull()
      val state = json?.takeIf { it.optString("requestId") == pending.requestId }
        ?.takeIf { it.optString("state") == "ready" }
        ?.let {
          RichHtmlEditorState(
            html = it.optString("html"),
            canUndo = it.optBoolean("canUndo"),
            canRedo = it.optBoolean("canRedo"),
            contentHeightPx = it.optInt("contentHeightPx"),
          )
        }
      if (state == null || state.html.length > MAX_HTML_CHARS) {
        finish(pending, Result.failure(IllegalStateException("Rich text editor returned an invalid result")))
      } else {
        finish(pending, Result.success(state))
      }
    }
  }

  private fun finish(pending: Pending, result: Result<RichHtmlEditorState>) {
    synchronized(requestLock) {
      if (active !== pending) return
      active = null
    }
    timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
    pending.callback(result)
  }

  private fun failPageLoad() {
    pageLoadFailed = true
    pageReady = true
    pendingLoad?.let { (_, _, callback) ->
      pendingLoad = null
      callback(Result.failure(IllegalStateException("Rich text editor failed to load")))
    }
    val pending = synchronized(requestLock) { active }
    pending?.let { finish(it, Result.failure(IllegalStateException("Rich text editor failed to load"))) }
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
    const val MAX_HTML_CHARS = RichHtmlAnnotationRenderer.MAX_HTML_CHARS
    private const val ASSET_HOST = "appassets.androidplatform.net"
    private const val EDITOR_URL = "https://$ASSET_HOST/assets/rich-annotation/editor.html"
    private const val REQUEST_TIMEOUT_MS = 15_000L
    private val TITLE_PATTERN = Regex("^pagenote-rich-edit:(ready|error):([0-9]+)$")

    /** Detaches [webView] from any stale parent before a new host attaches it. */
    fun prepareForAttach(view: View) {
      (view.parent as? ViewGroup)?.removeView(view)
    }
  }
}
