package io.pagenote.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 悬浮窗前台服务（移植差距清单 #24）：
 *
 * `specialUse` 类型前台服务保活；经 WindowManager 挂 `TYPE_APPLICATION_OVERLAY`
 * 窗口（需 SYSTEM_ALERT_WINDOW），承载悬浮球与展开面板——本服务即悬浮窗
 * 管理器：窗口挂载/移除、拖拽/缩放的边界钳制全部走 [FloatingWindowPolicy]，
 * 服务只把结果应用到 LayoutParams。面板四个页签：
 * - 笔记小窗：编辑 [FloatingWindowHub] 注入的当前页草稿，「保存」经 hub 回传
 *   主界面（页码绑定，见 [FloatingNoteWindowPolicy]）；
 * - 剪贴板：读取系统剪贴板预览并可存为常用语；
 * - 常用语：[FloatingPhraseStore] 列表，点按复制、长按删除、可从剪贴板添加；
 * - 计时器：[FloatingTimerEngine] 倒计时，归零发通知。
 *
 * 「截屏」按钮经 [FloatingWindowHub.requestCapture] 请求主界面发起
 * MediaProjection 授权（批次 10 预留接口 [FloatingCaptureEntry] 的 #24 闭环）。
 */
class FloatingWindowService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val handler = Handler(Looper.getMainLooper())
  private lateinit var themedContext: Context
  private var windowManager: WindowManager? = null
  private var phraseStore: FloatingPhraseStore? = null
  private var clipboard: ClipboardManager? = null

  private var ballView: View? = null
  private var ballParams: WindowManager.LayoutParams? = null
  private var panelView: ViewGroup? = null
  private var panelParams: WindowManager.LayoutParams? = null

  private var noteEditor: EditText? = null
  private var updatingNoteFromHub = false
  private var clipPreview: TextView? = null
  private var phraseListHost: LinearLayout? = null
  private var phraseStatus: TextView? = null
  private var timerDisplay: TextView? = null
  private var timerStartButton: Button? = null
  private var currentPhrases: List<String> = emptyList()

  private val timerEngine = FloatingTimerEngine()
  private var lastPresetMinutes = FloatingTimerPolicy.DEFAULT_PRESET_MINUTES
  private var timerCompletionNotified = false
  private var panelWidthDp = FloatingWindowPolicy.DEFAULT_PANEL_WIDTH_DP
  private var panelHeightDp = FloatingWindowPolicy.DEFAULT_PANEL_HEIGHT_DP
  private var panelVisible = false
  private var lastBallX: Int? = null
  private var lastBallY: Int? = null

  private val density: Float
    get() = resources.displayMetrics.density

  private fun dp(value: Int): Int = (value * density).roundToInt()

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    themedContext = ContextThemeWrapper(this, android.R.style.Theme_Material_Light)
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    phraseStore = floatingPhraseStore(applicationContext)
    clipboard = getSystemService(ClipboardManager::class.java)
    FloatingWindowHub.setActive(true)
    startInForeground()
    observePhrases()
    showBall()
    observeHubDraft()
    scheduleTick()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

  override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
    serviceScope.cancel()
    removeBall()
    removePanel()
    FloatingWindowHub.setActive(false)
    stopForeground(STOP_FOREGROUND_REMOVE)
    super.onDestroy()
  }

  // --- 前台通知 ---------------------------------------------------------------

  private fun startInForeground() {
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        getString(R.string.floating_window_channel_name),
        NotificationManager.IMPORTANCE_LOW,
      ),
    )
    val contentIntent = PendingIntent.getActivity(
      this,
      0,
      packageManager.getLaunchIntentForPackage(packageName),
      PendingIntent.FLAG_IMMUTABLE,
    )
    val notification: Notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_menu_edit)
      .setContentTitle(getString(R.string.floating_window_notification_title))
      .setContentText(getString(R.string.floating_window_notification_text))
      .setContentIntent(contentIntent)
      .setOngoing(true)
      .build()
    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
  }

  private fun notifyTimerFinished(minutes: Int) {
    val manager = getSystemService(NotificationManager::class.java)
    manager.notify(
      TIMER_NOTIFICATION_ID,
      androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle(getString(R.string.floating_timer_done_title))
        .setContentText(getString(R.string.floating_timer_done_text, minutes))
        .setAutoCancel(true)
        .build(),
    )
  }

  // --- 悬浮窗管理器：窗口挂载/移除/更新 ----------------------------------------

  private fun overlayFlags(): Int =
    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
      WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

  private fun showBall() {
    if (ballView != null) return
    removePanel()
    panelVisible = false
    val size = dp(FloatingWindowPolicy.BALL_SIZE_DP)
    val metrics = resources.displayMetrics
    val (x, y) = FloatingWindowPolicy.clampBallPosition(
      x = lastBallX ?: metrics.widthPixels - size - dp(16),
      y = lastBallY ?: dp(120),
      widthPx = size,
      heightPx = size,
      screenWidthPx = metrics.widthPixels,
      screenHeightPx = metrics.heightPixels,
    )
    val params = WindowManager.LayoutParams(
      size,
      size,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      overlayFlags() or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      this.x = x
      this.y = y
    }
    lastBallX = x
    lastBallY = y
    val ball = buildBallView()
    ball.setOnTouchListener(BallTouchListener(params))
    ball.setOnLongClickListener {
      handler.post { stopSelf() }
      true
    }
    ballParams = params
    ballView = ball
    runCatching { windowManager?.addView(ball, params) }
      .onFailure { error -> android.util.Log.w(TAG, "ball addView failed", error) }
  }

  private fun removeBall() {
    val ball = ballView ?: return
    ballParams?.let { params ->
      lastBallX = params.x
      lastBallY = params.y
      runCatching { windowManager?.removeView(ball) }
    }
    ballView = null
    ballParams = null
  }

  private fun expandPanel() {
    if (panelVisible) return
    val anchorX = ballParams?.x ?: dp(24)
    val anchorY = ballParams?.y ?: dp(24)
    removeBall()
    val metrics = resources.displayMetrics
    val widthPx = dp(panelWidthDp)
    val heightPx = dp(panelHeightDp)
    val (x, y) = FloatingWindowPolicy.clampPanelPosition(
      x = anchorX,
      y = anchorY,
      widthPx = widthPx,
      heightPx = heightPx,
      screenWidthPx = metrics.widthPixels,
      screenHeightPx = metrics.heightPixels,
    )
    val params = WindowManager.LayoutParams(
      widthPx,
      heightPx,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      overlayFlags(),
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      this.x = x
      this.y = y
    }
    val panel = buildPanelView()
    panelParams = params
    panelView = panel
    panelVisible = true
    runCatching { windowManager?.addView(panel, params) }
      .onFailure { error ->
        android.util.Log.w(TAG, "panel addView failed", error)
        panelView = null
        panelParams = null
        panelVisible = false
        showBall()
      }
  }

  private fun collapseToBall() {
    removePanel()
    showBall()
  }

  private fun removePanel() {
    val panel = panelView ?: return
    runCatching { windowManager?.removeView(panel) }
    panelView = null
    panelParams = null
    panelVisible = false
  }

  private fun updatePanelGeometry(widthPx: Int, heightPx: Int, x: Int, y: Int) {
    val params = panelParams ?: return
    val view = panelView ?: return
    val metrics = resources.displayMetrics
    params.width = widthPx
    params.height = heightPx
    val (cx, cy) = FloatingWindowPolicy.clampPanelPosition(
      x,
      y,
      widthPx,
      heightPx,
      metrics.widthPixels,
      metrics.heightPixels,
    )
    params.x = cx
    params.y = cy
    runCatching { windowManager?.updateViewLayout(view, params) }
  }

  // --- 悬浮球 -----------------------------------------------------------------

  private fun buildBallView(): View {
    val ballBackground = GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(0xEE245E59.toInt())
    }
    return TextView(themedContext).apply {
      text = getString(R.string.floating_ball_label)
      setTextColor(Color.WHITE)
      textSize = 18f
      typeface = Typeface.DEFAULT_BOLD
      gravity = Gravity.CENTER
      background = ballBackground
    }
  }

  /** 悬浮球触摸：拖拽（策略钳制）；位移在容差内视为点按 → 展开面板。 */
  private inner class BallTouchListener(private val params: WindowManager.LayoutParams) :
    View.OnTouchListener {
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var totalDx = 0f
    private var totalDy = 0f

    override fun onTouch(view: View, event: MotionEvent): Boolean {
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          downRawX = event.rawX
          downRawY = event.rawY
          startX = params.x
          startY = params.y
          totalDx = 0f
          totalDy = 0f
        }
        MotionEvent.ACTION_MOVE -> {
          totalDx = event.rawX - downRawX
          totalDy = event.rawY - downRawY
          val metrics = resources.displayMetrics
          val (x, y) = FloatingWindowPolicy.clampBallPosition(
            startX + totalDx.roundToInt(),
            startY + totalDy.roundToInt(),
            params.width,
            params.height,
            metrics.widthPixels,
            metrics.heightPixels,
          )
          params.x = x
          params.y = y
          lastBallX = x
          lastBallY = y
          runCatching { windowManager?.updateViewLayout(view, params) }
        }
        MotionEvent.ACTION_UP -> {
          view.performClick()
          if (FloatingWindowPolicy.isTapWithinSlop(totalDx, totalDy, dp(8).toFloat())) {
            // 触摸分发中不能结构性换窗，投递到下一帧。
            handler.post { expandPanel() }
          }
        }
      }
      return true
    }
  }

  // --- 展开面板 ---------------------------------------------------------------

  private fun panelBackground(): GradientDrawable = GradientDrawable().apply {
    setColor(0xF51A2421.toInt())
    cornerRadius = dp(12).toFloat()
  }

  private fun panelButton(label: String): Button =
    Button(themedContext).apply { text = label; textSize = 13f; isAllCaps = false }

  private fun panelLabel(text: String, size: Float = 14f, bold: Boolean = false): TextView =
    TextView(themedContext).apply {
      this.text = text
      setTextColor(Color.WHITE)
      textSize = size
      if (bold) typeface = Typeface.DEFAULT_BOLD
    }

  private fun buildPanelView(): ViewGroup {
    val root = LinearLayout(themedContext).apply {
      orientation = LinearLayout.VERTICAL
      background = panelBackground()
      setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    // 标题栏：标题文本是拖拽把手（不拦截右侧按钮），截屏经 hub 回传主界面授权。
    val header = LinearLayout(themedContext).apply { orientation = LinearLayout.HORIZONTAL }
    val dragHandle = panelLabel(getString(R.string.floating_panel_title), bold = true).apply {
      setOnTouchListener(PanelDragTouchListener())
    }
    header.addView(
      dragHandle,
      LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
    )
    header.addView(
      panelButton(getString(R.string.floating_capture_action)).apply {
        setOnClickListener { FloatingWindowHub.requestCapture() }
      },
    )
    header.addView(
      panelButton(getString(R.string.floating_collapse_action)).apply {
        setOnClickListener { handler.post { collapseToBall() } }
      },
    )
    header.addView(
      panelButton(getString(R.string.floating_close_action)).apply {
        setOnClickListener { handler.post { stopSelf() } }
      },
    )
    root.addView(header)

    // 页签行 + 四个内容页。
    val tabs = LinearLayout(themedContext).apply { orientation = LinearLayout.HORIZONTAL }
    val notePage = buildNotePage()
    val clipPage = buildClipPage()
    val phrasePage = buildPhrasePage()
    val timerPage = buildTimerPage()
    val pages = listOf(notePage, clipPage, phrasePage, timerPage)
    listOf(
      R.string.floating_tab_note,
      R.string.floating_tab_clipboard,
      R.string.floating_tab_phrases,
      R.string.floating_tab_timer,
    ).forEachIndexed { index, labelRes ->
      tabs.addView(
        panelButton(getString(labelRes)).apply {
          setOnClickListener { displayPage(pages, index) }
        },
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
      )
    }
    root.addView(tabs)

    val content = FrameLayout(themedContext)
    pages.forEach { page ->
      content.addView(
        page,
        FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
        ),
      )
    }
    root.addView(
      content,
      LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        0,
        1f,
      ).apply { topMargin = dp(4) },
    )

    // 缩放把手（右下角拖动改变面板宽高）。
    val resizeHandle = TextView(themedContext).apply {
      text = getString(R.string.floating_resize_handle)
      setTextColor(Color.WHITE)
      textSize = 12f
      gravity = Gravity.END or Gravity.CENTER_VERTICAL
      setPadding(0, dp(6), dp(4), dp(2))
      setOnTouchListener(PanelResizeTouchListener())
    }
    root.addView(
      resizeHandle,
      LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)),
    )

    displayPage(pages, PAGE_NOTE)
    return root
  }

  /** 页签切换：只显示选中的页并按需刷新动态内容。 */
  private fun displayPage(pages: List<View>, index: Int) {
    pages.forEachIndexed { position, page ->
      page.visibility = if (position == index) View.VISIBLE else View.GONE
    }
    when (index) {
      PAGE_CLIPBOARD -> refreshClipboardPreview()
      PAGE_PHRASES -> refreshPhrases()
      PAGE_TIMER -> refreshTimerDisplay()
    }
  }

  // --- 悬浮笔记小窗 -----------------------------------------------------------

  private fun buildNotePage(): View {
    val page = LinearLayout(themedContext).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(4), dp(4), dp(4), dp(4))
    }
    val editor = EditText(themedContext).apply {
      hint = getString(R.string.floating_note_hint)
      minLines = 4
      gravity = Gravity.TOP or Gravity.START
      setTextSize(14f)
      setTextColor(Color.BLACK)
      updatingNoteFromHub = true
      setText(FloatingWindowHub.noteDraft.value?.text.orEmpty())
      updatingNoteFromHub = false
      addTextChangedListener(
        object : android.text.TextWatcher {
          override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
          override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
          override fun afterTextChanged(s: android.text.Editable?) {
            if (updatingNoteFromHub) return
            FloatingWindowHub.updateNoteText(s?.toString().orEmpty())
          }
        },
      )
    }
    noteEditor = editor
    page.addView(
      editor,
      LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
    )
    page.addView(
      panelButton(getString(R.string.floating_note_save)).apply {
        setOnClickListener { FloatingWindowHub.requestNoteSave() }
      },
      LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    )
    return page
  }

  /** 主界面 attach 的新草稿（重新打开悬浮窗）推入悬浮窗编辑器。 */
  private fun observeHubDraft() {
    FloatingWindowHub.noteDraft
      .onEach { draft ->
        val editor = noteEditor ?: return@onEach
        val current = editor.text?.toString().orEmpty()
        val incoming = draft?.text.orEmpty()
        if (current != incoming) {
          updatingNoteFromHub = true
          editor.setText(incoming)
          updatingNoteFromHub = false
        }
      }
      .launchIn(serviceScope)
  }

  // --- 剪贴板 -----------------------------------------------------------------

  private fun clipboardText(): String? {
    val clip = clipboard?.primaryClip ?: return null
    val builder = StringBuilder()
    for (index in 0 until clip.itemCount) {
      val text = clip.getItemAt(index).coerceToText(themedContext)?.toString() ?: continue
      if (text.isNotBlank()) {
        if (builder.isNotEmpty()) builder.append('\n')
        builder.append(text)
      }
    }
    return builder.toString().takeIf { it.isNotBlank() }
  }

  private fun buildClipPage(): View {
    val page = LinearLayout(themedContext).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(4), dp(4), dp(4), dp(4))
    }
    val preview = TextView(themedContext).apply {
      hint = getString(R.string.floating_clipboard_empty)
      setTextColor(Color.WHITE)
      setTextSize(14f)
      setTextIsSelectable(true)
    }
    clipPreview = preview
    val scroll = ScrollView(themedContext)
    scroll.addView(preview)
    page.addView(
      scroll,
      LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
    )
    page.addView(
      panelButton(getString(R.string.floating_clipboard_save_phrase)).apply {
        setOnClickListener { saveClipboardAsPhrase() }
      },
      LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    )
    page.addView(
      panelButton(getString(R.string.floating_refresh)).apply {
        setOnClickListener { refreshClipboardPreview() }
      },
      LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    )
    return page
  }

  private fun refreshClipboardPreview() {
    clipPreview?.text = clipboardText()
  }

  private fun saveClipboardAsPhrase() {
    val text = clipboardText()
    if (text == null) {
      phraseStatus?.text = getString(R.string.floating_clipboard_empty)
      return
    }
    serviceScope.launch {
      phraseStore?.add(text)
    }
    phraseStatus?.text = getString(R.string.floating_phrase_saved)
  }

  // --- 常用语 -----------------------------------------------------------------

  private fun buildPhrasePage(): View {
    val page = LinearLayout(themedContext).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(4), dp(4), dp(4), dp(4))
    }
    val status = TextView(themedContext).apply {
      setTextColor(Color.LTGRAY)
      setTextSize(12f)
    }
    phraseStatus = status
    page.addView(status)
    val host = LinearLayout(themedContext).apply { orientation = LinearLayout.VERTICAL }
    phraseListHost = host
    val scroll = ScrollView(themedContext)
    scroll.addView(host)
    page.addView(
      scroll,
      LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
    )
    page.addView(
      panelButton(getString(R.string.floating_phrase_add_from_clipboard)).apply {
        setOnClickListener {
          val text = clipboardText()
          if (text == null) {
            status.text = getString(R.string.floating_clipboard_empty)
          } else {
            serviceScope.launch {
              phraseStore?.add(text)
            }
            status.text = getString(R.string.floating_phrase_saved)
            refreshPhrases()
          }
        }
      },
      LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    )
    return page
  }

  private fun refreshPhrases() {
    renderPhrases()
  }

  /** 常用语 Flow 在服务创建时只收集一次；切回页签只重绘缓存，避免重复订阅。 */
  private fun observePhrases() {
    phraseStore?.phrases
      ?.onEach { phrases ->
        currentPhrases = phrases
        renderPhrases()
      }
      ?.launchIn(serviceScope)
  }

  private fun renderPhrases() {
    val host = phraseListHost ?: return
    host.removeAllViews()
    if (currentPhrases.isEmpty()) {
      host.addView(panelLabel(getString(R.string.floating_phrase_empty), size = 13f))
    }
    currentPhrases.forEachIndexed { index, phrase ->
      host.addView(
        panelLabel(FloatingPhrasePolicy.preview(phrase)).apply {
          setPadding(dp(6), dp(8), dp(6), dp(8))
          setOnClickListener {
            clipboard?.setPrimaryClip(ClipData.newPlainText("phrase", phrase))
            phraseStatus?.text = getString(R.string.floating_phrase_copied)
          }
          setOnLongClickListener {
            serviceScope.launch {
              phraseStore?.remove(index)
            }
            true
          }
        },
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
      )
    }
  }

  // --- 计时器 -----------------------------------------------------------------

  private fun buildTimerPage(): View {
    val page = LinearLayout(themedContext).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setPadding(dp(4), dp(4), dp(4), dp(4))
    }
    val display = TextView(themedContext).apply {
      text = FloatingTimerPolicy.format(0L)
      setTextColor(Color.WHITE)
      textSize = 40f
      typeface = Typeface.MONOSPACE
      gravity = Gravity.CENTER
    }
    timerDisplay = display
    page.addView(display)

    val presets = LinearLayout(themedContext).apply { orientation = LinearLayout.HORIZONTAL }
    FloatingTimerPolicy.MINUTE_PRESETS.forEach { minutes ->
      presets.addView(
        panelButton(getString(R.string.floating_timer_preset, minutes)).apply {
          setOnClickListener { selectTimerPreset(minutes) }
        },
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
      )
    }
    page.addView(presets)

    val controls = LinearLayout(themedContext).apply { orientation = LinearLayout.HORIZONTAL }
    val startButton = panelButton(getString(R.string.floating_timer_start)).apply {
      setOnClickListener { onTimerControlClicked() }
    }
    timerStartButton = startButton
    controls.addView(startButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    controls.addView(
      panelButton(getString(R.string.floating_timer_reset)).apply {
        setOnClickListener { resetTimer() }
      },
      LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
    )
    page.addView(controls)
    return page
  }

  /** 档位选择：仅在空闲时按档位开始；计时时忽略（先重置）。 */
  private fun selectTimerPreset(minutes: Int) {
    val state = timerEngine.state
    if (state.running || state.remainingMillis > 0L) return
    startTimer(minutes)
    refreshTimerDisplay()
  }

  private fun onTimerControlClicked() {
    val state = timerEngine.state
    when {
      state.finished -> {
        startTimer(lastPresetMinutes)
      }
      state.running -> timerEngine.pause()
      state.remainingMillis > 0L -> timerEngine.resume()
      else -> {
        startTimer(FloatingTimerPolicy.DEFAULT_PRESET_MINUTES)
      }
    }
    refreshTimerDisplay()
  }

  private fun resetTimer() {
    timerEngine.reset()
    timerCompletionNotified = false
    refreshTimerDisplay()
  }

  private fun startTimer(minutes: Int) {
    timerCompletionNotified = false
    lastPresetMinutes = minutes
    timerEngine.start(FloatingTimerPolicy.durationMillis(minutes))
  }

  private fun refreshTimerDisplay() {
    val state = timerEngine.state
    timerDisplay?.text = FloatingTimerPolicy.format(state.remainingMillis)
    timerStartButton?.text = getString(
      when {
        state.running -> R.string.floating_timer_pause
        state.remainingMillis > 0L -> R.string.floating_timer_resume
        else -> R.string.floating_timer_start
      },
    )
  }

  private val tickRunnable = object : Runnable {
    override fun run() {
      val state = timerEngine.tick()
      if (state.finished && !timerCompletionNotified) {
        timerCompletionNotified = true
        notifyTimerFinished(lastPresetMinutes)
      }
      if (panelVisible) refreshTimerDisplay()
      handler.postDelayed(this, FloatingTimerPolicy.TICK_INTERVAL_MS)
    }
  }

  private fun scheduleTick() {
    handler.postDelayed(tickRunnable, FloatingTimerPolicy.TICK_INTERVAL_MS)
  }

  // --- 面板手势 ---------------------------------------------------------------

  /** 拖拽把手：移动面板窗口（策略钳制在屏幕内）。 */
  private inner class PanelDragTouchListener : View.OnTouchListener {
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0

    override fun onTouch(view: View, event: MotionEvent): Boolean {
      val params = panelParams ?: return false
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          downRawX = event.rawX
          downRawY = event.rawY
          startX = params.x
          startY = params.y
        }
        MotionEvent.ACTION_MOVE -> updatePanelGeometry(
          params.width,
          params.height,
          startX + (event.rawX - downRawX).roundToInt(),
          startY + (event.rawY - downRawY).roundToInt(),
        )
      }
      return true
    }
  }

  /** 缩放把手：拖动右下角改变面板宽高（dp 钳制，位置保持完整在屏）。 */
  private inner class PanelResizeTouchListener : View.OnTouchListener {
    private var downRawX = 0f
    private var downRawY = 0f
    private var startWidthDp = 0
    private var startHeightDp = 0
    private var startX = 0
    private var startY = 0

    override fun onTouch(view: View, event: MotionEvent): Boolean {
      val params = panelParams ?: return false
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          downRawX = event.rawX
          downRawY = event.rawY
          startWidthDp = panelWidthDp
          startHeightDp = panelHeightDp
          startX = params.x
          startY = params.y
        }
        MotionEvent.ACTION_MOVE -> {
          val dWidthDp = ((event.rawX - downRawX) / density).roundToInt()
          val dHeightDp = ((event.rawY - downRawY) / density).roundToInt()
          val (widthDp, heightDp) = FloatingWindowPolicy.resizedPanel(
            startWidthDp,
            startHeightDp,
            dWidthDp,
            dHeightDp,
          )
          panelWidthDp = widthDp
          panelHeightDp = heightDp
          updatePanelGeometry(dp(widthDp), dp(heightDp), startX, startY)
        }
      }
      return true
    }
  }

  companion object {
    private const val TAG = "FloatingWindowService"
    private const val CHANNEL_ID = "floating_window"
    private const val NOTIFICATION_ID = 2102
    private const val TIMER_NOTIFICATION_ID = 2112
    private const val PAGE_NOTE = 0
    private const val PAGE_CLIPBOARD = 1
    private const val PAGE_PHRASES = 2
    private const val PAGE_TIMER = 3

    /** 阅读界面入口（SYSTEM_ALERT_WINDOW 权限由调用侧先检查）。 */
    fun start(context: Context) {
      val intent = Intent(context, FloatingWindowService::class.java)
      androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, FloatingWindowService::class.java))
    }
  }
}
