package io.pagenote.feature.yuketang

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URL
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class RainLoginState(
  val status: String = "signed_out",
  val qrImage: ByteArray? = null,
  val accountName: String? = null,
  val error: String? = null,
)

data class RainCourse(
  val courseId: String,
  val classroomId: String,
  val name: String,
  val teacher: String = "",
  val term: String = "",
)

data class RainLesson(
  val classroomId: String,
  val lessonId: String?,
  val presentationId: String,
  val name: String,
  val createdAt: String? = null,
)

data class RainSlide(
  val pageId: String,
  val sourceIndex: Int,
  val presentationId: String,
  val imageUrl: String,
)

data class RainSlideImage(val slide: RainSlide, val contentType: String, val bytes: ByteArray)

data class RainLiveState(
  val status: String = "stopped",
  val lessonId: String? = null,
  val error: String? = null,
  val pageId: String? = null,
  val sourceIndex: Int? = null,
  val eventNumber: Long = 0,
  val possibleGap: Boolean = false,
  val pendingPages: Int = 0,
)

class RainClassroomClient(context: Context) {
  private companion object {
    const val MAX_QR_IMAGE_BYTES = 2 * 1024 * 1024
  }

  private data class ActiveLive(
    val lessonId: String,
    val token: String,
    val identity: String,
    val cookie: String,
    val onSlide: suspend (RainSlideImage) -> Unit,
    val attempts: AtomicInteger = AtomicInteger(),
    val reconnectPending: AtomicBoolean = AtomicBoolean(),
    var connections: Int = 0,
    var lastTimelineKey: String? = null,
    var stopped: Boolean = false,
    val pendingMessages: AtomicInteger = AtomicInteger(),
    val pendingSlides: LinkedHashMap<String, RainLiveSlideEvent> = LinkedHashMap(),
  )
  private val appContext = context.applicationContext
  private val sessionStore = RainSessionStore(appContext)
  private val http = OkHttpClient.Builder()
    .callTimeout(35, TimeUnit.SECONDS)
    .connectTimeout(20, TimeUnit.SECONDS)
    .build()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val _login = MutableStateFlow(RainLoginState())
  val login: StateFlow<RainLoginState> = _login.asStateFlow()
  private val _live = MutableStateFlow(RainLiveState())
  val live: StateFlow<RainLiveState> = _live.asStateFlow()
  private val liveMutex = Mutex()
  private var loginSocket: WebSocket? = null
  private var liveSocket: WebSocket? = null
  private var loginId: String? = null
  private var loginFinished = false
  private var lessonAuthorization: String? = null
  private var activeLive: ActiveLive? = null
  private val presentations = mutableMapOf<String, List<RainSlide>>()

  fun startLogin() {
    loginSocket?.cancel()
    val id = UUID.randomUUID().toString()
    loginId = id
    loginFinished = false
    _login.value = RainLoginState(status = "connecting")
    loginSocket = http.newWebSocket(
      Request.Builder().url("wss://www.yuketang.cn/wsapp/").build(),
      object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          webSocket.send(JSONObject().put("op", "requestlogin").put("role", "web")
            .put("version", 1.4).put("type", "qrcode").put("from", "web").toString())
          scope.launch {
            delay(60_000)
            if (loginId == id && !loginFinished) {
              _login.value = RainLoginState(status = "expired", error = "二维码已过期，请重新生成")
              loginSocket?.close(1000, "expired")
            }
          }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
          val message = runCatching { JSONObject(text) }.getOrNull() ?: return
          val ticket = message.optString("ticket").takeIf(String::isNotBlank)
          if (ticket != null && loginId == id && !loginFinished) {
            _login.value = RainLoginState(status = "loading_qr")
            scope.launch {
              try {
                val image = fetchLoginQrImage(ticket)
                if (loginId == id && !loginFinished)
                  _login.value = RainLoginState(status = "waiting_scan", qrImage = image)
              } catch (error: Exception) {
                if (loginId == id && !loginFinished)
                  _login.value = RainLoginState(status = "failed", error = error.message ?: "雨课堂二维码图片暂时无法加载")
              }
            }
            return
          }
          if (message.has("UserID") && message.has("Auth") && loginId == id) {
            loginFinished = true
            scope.launch { finishLogin(message) }
          }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
          if (loginId == id && !loginFinished)
            _login.value = RainLoginState(status = "failed", error = t.message ?: "无法连接雨课堂")
        }
      },
    )
  }

  private fun ticketUrl(ticket: String): String {
    val uri = try {
      URI(ticket)
    } catch (_: Exception) {
      throw IOException("雨课堂二维码地址无效")
    }
    if (uri.isAbsolute) {
      if (!uri.scheme.equals("https", ignoreCase = true)) throw IOException("雨课堂二维码地址无效")
      return uri.toASCIIString()
    }
    if (uri.rawAuthority != null) throw IOException("雨课堂二维码地址无效")
    return URL(URL("https://www.yuketang.cn/"), uri.toString()).toExternalForm()
  }

  private fun fetchLoginQrImage(ticket: String): ByteArray {
    val ticketUrl = ticketUrl(ticket)
    val url = URL(ticketUrl)
    val host = url.host.lowercase()
    val isYuketang = host == "yuketang.cn" || host.endsWith(".yuketang.cn")
    val isWechatQr = host == "mp.weixin.qq.com" && url.path == "/cgi-bin/showqrcode"
    if (url.protocol != "https" || url.userInfo != null || (url.port != -1 && url.port != 443) || (!isYuketang && !isWechatQr))
      throw IOException("雨课堂二维码地址无效")

    val qrHttp = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
    val request = Request.Builder().url(url).header("Accept", "image/png,image/jpeg,image/webp,image/gif")
      .header("User-Agent", "pagenote/android").build()
    qrHttp.newCall(request).execute().use { response ->
      if (!response.isSuccessful) throw IOException("雨课堂二维码图片暂时无法加载")
      val body = response.body ?: throw IOException("雨课堂二维码图片为空")
      val responseType = body.contentType()?.let { "${it.type}/${it.subtype}".lowercase() }
      val contentType = if (responseType == "image/jpg") "image/jpeg" else responseType
      if (contentType !in setOf("image/png", "image/jpeg", "image/webp", "image/gif"))
        throw IOException("雨课堂返回的二维码图片格式无法识别")
      if (body.contentLength() > MAX_QR_IMAGE_BYTES) throw IOException("雨课堂二维码图片超过大小限制")
      val bytes = body.byteStream().use { it.readNBytes(MAX_QR_IMAGE_BYTES + 1) }
      if (bytes.isEmpty() || bytes.size > MAX_QR_IMAGE_BYTES) throw IOException("雨课堂二维码图片超过大小限制")
      return bytes
    }
  }

  private suspend fun finishLogin(auth: JSONObject) {
    try {
      val body = JSONObject().put("UserID", auth.get("UserID")).put("Auth", auth.get("Auth"))
      val response = postRaw("https://www.yuketang.cn/pc/web_login", body, null)
      val cookies = response.use {
        it.headers("Set-Cookie").mapNotNull { cookie -> cookie.substringBefore(';').takeIf(String::isNotBlank) }
      }
      if (cookies.isEmpty()) throw IOException("雨课堂没有返回登录会话")
      val cookie = cookies.joinToString("; ")
      val payload = requestJson("https://www.yuketang.cn/api/v3/user/basic-info", cookie)
      val data = unwrap(payload)
      val user = data.optJSONObject("user") ?: data
      val name = user.optString("name", user.optString("nickname", "雨课堂用户"))
      sessionStore.save(cookie, name)
      _login.value = RainLoginState(status = "authenticated", accountName = name)
    } catch (error: Exception) {
      _login.value = RainLoginState(status = "failed", error = error.message ?: "雨课堂登录失败")
    } finally {
      loginSocket?.close(1000, "complete")
      loginSocket = null
    }
  }

  suspend fun refreshLogin(): RainLoginState {
    val session = sessionStore.read() ?: return RainLoginState()
    return try {
      val payload = requestJson("https://www.yuketang.cn/api/v3/user/basic-info", session.first)
      val data = unwrap(payload)
      val user = data.optJSONObject("user") ?: data
      val name = user.optString("name", user.optString("nickname", session.second))
      _login.value = RainLoginState(status = "authenticated", accountName = name)
      _login.value
    } catch (error: Exception) {
      if (error.message?.contains("401") == true || error.message?.contains("403") == true) {
        sessionStore.clear()
        _login.value = RainLoginState()
      }
      throw error
    }
  }

  fun cancelLogin() {
    loginId = null
    loginSocket?.cancel()
    loginSocket = null
    if (_login.value.status != "authenticated") _login.value = RainLoginState()
  }

  suspend fun logout() {
    stopLive()
    liveMutex.withLock {
      if (activeLive?.pendingSlides?.isNotEmpty() == true) {
        error("还有未写入的课堂页面，请重试后再退出登录")
      }
      sessionStore.clear()
      presentations.clear()
      _login.value = RainLoginState()
    }
  }

  suspend fun listCourses(): List<RainCourse> {
    val cookie = sessionStore.read()?.first ?: error("请先登录雨课堂")
    val data = unwrap(requestJson("https://www.yuketang.cn/v2/api/web/courses/list?identity=2", cookie))
    val list = data.optJSONArray("list") ?: data.optJSONArray("data") ?: error("无法识别雨课堂课程列表")
    return buildList {
      for (index in 0 until list.length()) {
        val item = list.optJSONObject(index) ?: continue
        val course = item.optJSONObject("course") ?: JSONObject()
        val teacher = item.optJSONObject("teacher") ?: JSONObject()
        val classroomId = item.stringId("classroom_id", "classroomId") ?: continue
        val courseId = item.stringId("course_id", "courseId") ?: course.stringId("id") ?: item.stringId("id") ?: continue
        add(RainCourse(courseId, classroomId,
          item.optString("name", course.optString("name", "未命名课程")),
          teacher.optString("name", item.optString("teacher_name", "")),
          item.optString("term_name", item.optString("term", ""))))
      }
    }
  }

  suspend fun listLessons(classroomId: String): List<RainLesson> {
    val cookie = sessionStore.read()?.first ?: error("请先登录雨课堂")
    val url = "https://www.yuketang.cn/v2/api/web/logs/learn/${java.net.URLEncoder.encode(classroomId, "UTF-8")}?actype=-1&page=0&offset=100&sort=-1"
    val data = unwrap(requestJson(url, cookie))
    val list = data.optJSONArray("activities") ?: error("无法识别雨课堂历史课件")
    return buildList {
      for (index in 0 until list.length()) {
        val item = list.optJSONObject(index) ?: continue
        val presentation = item.stringId("presentation_id", "presentationId", "courseware_id") ?: continue
        add(RainLesson(classroomId, item.stringId("lesson_id", "lessonId"), presentation,
          item.optString("title", item.optString("name", "未命名课件")),
          item.optString("create_time", item.optString("created_at", "")).ifBlank { null }))
      }
    }
  }

  suspend fun listCurrentLessons(): List<RainCurrentLesson> {
    val cookie = sessionStore.read()?.first ?: error("请先登录雨课堂")
    val payload = requestJson("https://www.yuketang.cn/api/v3/classroom/on-lesson", cookie)
    return normalizeCurrentLessons(payload)
  }

  suspend fun downloadPresentation(lesson: RainLesson): List<RainSlideImage> {
    val pages = mutableListOf<RainSlideImage>()
    forEachPresentationPage(lesson) { pages.add(it) }
    return pages
  }

  suspend fun forEachPresentationPage(lesson: RainLesson, onPage: suspend (RainSlideImage) -> Unit) {
    val cookie = sessionStore.read()?.first ?: error("请先登录雨课堂")
    val slides = fetchSlides(lesson.presentationId, cookie, null)
    for (slide in slides) {
      onPage(downloadSlide(slide))
    }
  }

  suspend fun startLive(lesson: RainLesson, onSlide: suspend (RainSlideImage) -> Unit) {
    if (lesson.lessonId.isNullOrBlank()) error("此课件没有可连接的课堂编号")
    activeLive?.let { previous ->
      stopLive()
      liveMutex.withLock {
        if (previous.pendingSlides.isNotEmpty()) {
          error("还有未写入的课堂页面，请重试后再开始新课堂")
        }
      }
    }
    val session = sessionStore.read() ?: error("请先登录雨课堂")
    _live.value = RainLiveState(status = "joining", lessonId = lesson.lessonId)
    val response = postRaw("https://www.yuketang.cn/api/v3/lesson/checkin", JSONObject()
      .put("source", 5).put("lessonId", lesson.lessonId), session.first,
      "https://www.yuketang.cn/lesson/fullscreen/v3/${lesson.lessonId}?source=5")
    lessonAuthorization = response.header("Set-Auth")
    val data = response.use { unwrap(JSONObject(it.body?.string().orEmpty())) }
    val token = data.optString("lessonToken").takeIf(String::isNotBlank) ?: error("进入雨课堂失败")
    val identity = data.stringId("identityId") ?: error("课堂身份无效")
    _live.value = RainLiveState(status = "connecting", lessonId = lesson.lessonId)
    val active = ActiveLive(lesson.lessonId, token, identity, session.first, onSlide)
    activeLive = active
    connectLive(active)
  }

  private fun connectLive(active: ActiveLive) {
    if (activeLive !== active || active.stopped) return
    liveSocket = http.newWebSocket(Request.Builder().url("wss://www.yuketang.cn/wsapp/").build(), object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        if (active.stopped) {
          webSocket.close(1000, "user stopped")
          return
        }
        active.attempts.set(0)
        active.reconnectPending.set(false)
        active.connections += 1
        webSocket.send(JSONObject().put("op", "hello").put("userid", active.identity)
          .put("role", "student").put("auth", active.token).put("lessonid", active.lessonId).toString())
        _live.value = _live.value.copy(status = "active", lessonId = active.lessonId, error = null)
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        val normalized = normalizeLiveMessage(message)
        if (normalized.finished) {
          _live.value = _live.value.copy(status = "finished")
          stopLive()
          return
        }
        if (normalized.slides.isEmpty()) return
        active.pendingMessages.incrementAndGet()
        scope.launch {
          liveMutex.withLock {
            runCatching {
              val replay = if (normalized.timeline) {
                val result = selectTimelineReplay(
                  normalized.slides,
                  active.lastTimelineKey,
                  initialConnection = active.connections == 1,
                )
                if (result.possibleGap) {
                  _live.value = _live.value.copy(
                    possibleGap = true,
                    error = "重连时间线未包含上次位置，可能存在缺页",
                  )
                }
                result.slides
              } else normalized.slides
              for (event in replay) {
                val key = "${event.presentationId}:${event.sourceIndex}"
                active.pendingSlides.putIfAbsent(key, event)
              }
              processPendingLiveSlides(active)
            }.onFailure { error ->
              _live.value = _live.value.copy(
                status = if (active.stopped) "stopped" else "error",
                error = error.message ?: "课件页面处理失败",
                pendingPages = active.pendingSlides.size,
              )
            }
            active.pendingMessages.decrementAndGet()
            finishStoppedLive(active)
          }
        }
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        scheduleLiveReconnect(active, t.message ?: "课堂连接失败")
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        scheduleLiveReconnect(active, reason.ifBlank { "课堂连接已断开" })
      }
    })
  }

  private fun scheduleLiveReconnect(active: ActiveLive, message: String) {
    if (active.stopped || activeLive !== active || !active.reconnectPending.compareAndSet(false, true)) return
    val attempt = active.attempts.incrementAndGet()
    _live.value = _live.value.copy(status = "reconnecting", error = message)
    val waitMs = minOf(30_000L, 1_000L * (1L shl minOf(attempt, 5)))
    scope.launch {
      delay(waitMs)
      if (activeLive === active) connectLive(active)
      else active.reconnectPending.set(false)
    }
  }

  fun stopLive() {
    val active = activeLive
    active?.stopped = true
    liveSocket?.close(1000, "user stopped")
    liveSocket = null
    if (active == null) {
      _live.value = RainLiveState(status = "stopped")
      return
    }
    _live.value = _live.value.copy(status = "stopped", pendingPages = active.pendingSlides.size)
    finishStoppedLive(active)
  }

  suspend fun retryLive() = liveMutex.withLock {
    val active = activeLive ?: throw IOException("当前没有正在采集的课堂")
    _live.value = _live.value.copy(status = if (active.stopped) "stopped" else "active", error = null)
    try {
      processPendingLiveSlides(active)
      finishStoppedLive(active)
    } catch (error: Exception) {
      _live.value = _live.value.copy(
        status = if (active.stopped) "stopped" else "error",
        error = error.message ?: "课件页面处理失败",
        pendingPages = active.pendingSlides.size,
      )
      throw error
    }
  }

  private fun finishStoppedLive(active: ActiveLive) {
    if (!active.stopped) return
    _live.value = _live.value.copy(
      status = "stopped",
      pendingPages = active.pendingSlides.size,
    )
    if (active.pendingSlides.isEmpty() && active.pendingMessages.get() == 0 && activeLive === active) {
      activeLive = null
    }
  }

  private suspend fun processPendingLiveSlides(active: ActiveLive) {
    for ((key, event) in active.pendingSlides.toMap()) {
      val slides = presentations[event.presentationId] ?: fetchSlides(
        event.presentationId,
        active.cookie,
        lessonAuthorization,
      ).also { presentations[event.presentationId] = it }
      val slide = slides.firstOrNull { it.sourceIndex == event.sourceIndex }
        ?: throw IOException("课件中找不到第 ${event.sourceIndex + 1} 页")
      val image = downloadSlide(slide)
      active.onSlide(image)
      active.pendingSlides.remove(key)
      active.lastTimelineKey = "${event.presentationId}:${event.sourceIndex}"
      _live.value = _live.value.copy(
        status = if (active.stopped) "stopped" else "active",
        error = null,
        pageId = slide.pageId,
        sourceIndex = event.sourceIndex,
        eventNumber = _live.value.eventNumber + 1,
        pendingPages = active.pendingSlides.size,
      )
    }
  }

  private suspend fun fetchSlides(presentationId: String, cookie: String, authorization: String?): List<RainSlide> {
    val encodedId = java.net.URLEncoder.encode(presentationId, "UTF-8")
    val url = "https://www.yuketang.cn/api/v3/lesson/presentation/fetch?presentation_id=$encodedId"
    val response = requestJsonResponse(url, cookie, authorization = authorization)
    lessonAuthorization = response.second.header("Set-Auth") ?: authorization
    val data = unwrap(response.first)
    val array = data.optJSONArray("slides") ?: error("无法识别雨课堂课件页面")
    return buildList {
      for (order in 0 until array.length()) {
        val item = array.optJSONObject(order) ?: continue
        val pageId = item.stringId("id", "pageId", "page_id") ?: continue
        val cover = item.opt("cover")
        val image = when (cover) {
          is String -> cover
          is JSONObject -> cover.optString("url", cover.optString("src", cover.optString("image_url")))
          else -> item.optString("imageUrl", item.optString("image_url", ""))
        }
        if (image.isBlank()) continue
        add(RainSlide(pageId, item.intValue("index") ?: order, presentationId, URL(URL("https://www.yuketang.cn"), image).toString()))
      }
    }.also { if (it.isEmpty()) error("课件没有可用页面") }
  }

  private suspend fun downloadSlide(slide: RainSlide): RainSlideImage {
    val request = Request.Builder().url(requireRainAsset(slide.imageUrl)).build()
    return http.newCall(request).execute().use { response ->
      if (!response.isSuccessful) throw IOException("课件页面下载失败（HTTP ${response.code}）")
      val type = response.header("Content-Type").orEmpty().substringBefore(';').lowercase()
      if (type !in setOf("image/jpeg", "image/png")) throw IOException("课件页面格式不支持")
      val bytes = response.body?.bytes() ?: throw IOException("课件页面为空")
      if (bytes.isEmpty() || bytes.size > 24 * 1024 * 1024) throw IOException("课件页面为空或超过 24 MB")
      RainSlideImage(slide, type, bytes)
    }
  }

  private fun requireRainAsset(value: String): String {
    val url = URL(value)
    require(url.protocol == "https" && (url.host == "yuketang.cn" || url.host.endsWith(".yuketang.cn"))) {
      "课件图片来自不受支持的资源域名"
    }
    return url.toString()
  }

  private fun requestJson(url: String, cookie: String): JSONObject = requestJsonResponse(url, cookie).first

  private fun requestJsonResponse(url: String, cookie: String, authorization: String? = null): Pair<JSONObject, Response> {
    val request = Request.Builder().url(url).header("Accept", "application/json")
      .header("Referer", "https://www.yuketang.cn/web?next=/v2/web/index&type=3")
      .header("Cookie", cookie).apply { if (!authorization.isNullOrBlank()) header("Authorization", authorization) }.build()
    val response = http.newCall(request).execute()
    if (!response.isSuccessful) {
      val code = response.code
      response.close()
      throw IOException("雨课堂请求失败（HTTP $code）")
    }
    return response.use { Pair(JSONObject(it.body?.string().orEmpty()), it) }
  }

  private fun postRaw(url: String, body: JSONObject, cookie: String?, referer: String? = null): Response {
    val request = Request.Builder().url(url).post(body.toString().toRequestBodyJson())
      .header("Accept", "application/json").apply {
        if (!cookie.isNullOrBlank()) header("Cookie", cookie)
        header("Referer", referer ?: "https://www.yuketang.cn/web?next=/v2/web/index&type=3")
      }.build()
    val response = http.newCall(request).execute()
    if (!response.isSuccessful) {
      val code = response.code
      response.close()
      throw IOException("雨课堂请求失败（HTTP $code）")
    }
    return response
  }

  private fun unwrap(payload: JSONObject): JSONObject {
    val code = payload.optInt("code", 0)
    if (code != 0) throw IOException("雨课堂请求失败：${payload.optString("msg", "code $code")}")
    return payload.optJSONObject("data") ?: payload
  }

  private fun JSONObject.stringId(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    if (!has(key) || isNull(key)) null else opt(key)?.toString()?.takeIf { it != "" && it != "null" }
  }

  private fun JSONObject.intValue(key: String): Int? = if (!has(key) || isNull(key)) null else {
    val value = opt(key)
    when (value) { is Number -> value.toInt(); is String -> value.toIntOrNull(); else -> null }
  }
}

private fun String.toRequestBodyJson() =
  toRequestBody("application/json; charset=utf-8".toMediaType())

private class RainSessionStore(context: Context) {
  private val preferences = context.getSharedPreferences("rain-session", Context.MODE_PRIVATE)
  private val alias = "pagenote.rain.session.v1"

  fun save(cookie: String, name: String) {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey())
    val encrypted = cipher.doFinal(JSONObject().put("cookie", cookie).put("name", name).toString().toByteArray())
    val bytes = cipher.iv + encrypted
    preferences.edit().putString("session", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()
    check(preferences.contains("session")) { "无法保存雨课堂登录状态" }
  }

  fun read(): Pair<String, String>? {
    val encoded = preferences.getString("session", null) ?: return null
    return try {
      val bytes = Base64.decode(encoded, Base64.NO_WRAP)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
      val value = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size))))
      value.getString("cookie") to value.optString("name", "雨课堂用户")
    } catch (_: Exception) {
      clear()
      null
    }
  }

  fun clear() { preferences.edit().remove("session").commit() }

  private fun secretKey(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(alias, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(KeyGenParameterSpec.Builder(alias,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setRandomizedEncryptionRequired(true).build())
    return generator.generateKey()
  }
}
