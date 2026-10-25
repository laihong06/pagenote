package io.pagenote.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 一次系统截屏的交付物。 */
data class ScreenCaptureFrame(val bitmap: Bitmap, val capturedAtMillis: Long)

enum class ScreenCapturePhase { IDLE, PREPARING, CAPTURING, FAILED }

/**
 * 系统截屏前台服务（移植差距清单 #23）：
 *
 * MediaProjection 令牌经 Intent extra 传入；服务先进入 `mediaProjection` 类型前台
 * （Android 14+ 要求先前台再取投影），再建 VirtualDisplay + ImageReader，抓到第一帧
 * 后按 [ScreenCapturePolicy.scaleToFrameLimit] 缩放为 ARGB_8888 位图交付
 * [frames]，随后释放投影并自停。OCR / 取色由调用侧消费位图（OCR 复用 feature-ocr）。
 *
 * 悬浮截屏入口（#24）只预留 [FloatingCaptureEntry] 接口，不影响本服务。
 */
class ScreenCaptureService : Service() {

  private var projection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private var handlerThread: HandlerThread? = null
  private var delivered = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
    val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
    if (resultCode == Int.MIN_VALUE || resultData == null) {
      phase.value = ScreenCapturePhase.FAILED
      stopSelf()
      return START_NOT_STICKY
    }
    startInForeground()
    phase.value = ScreenCapturePhase.PREPARING
    val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val activeProjection = try {
      manager.getMediaProjection(resultCode, resultData)
    } catch (error: Exception) {
      Log.w(TAG, "media projection rejected", error)
      phase.value = ScreenCapturePhase.FAILED
      stopSelf()
      return START_NOT_STICKY
    }
    projection = activeProjection
    activeProjection.registerCallback(object : MediaProjection.Callback() {
      override fun onStop() {
        releaseCapture()
      }
    }, Handler(mainLooper))

    val metrics = resources.displayMetrics
    val size = ScreenCapturePolicy.scaleToFrameLimit(metrics.widthPixels, metrics.heightPixels)
    handlerThread = HandlerThread("screen-capture").also { it.start() }
    val reader = ImageReader.newInstance(size.width, size.height, PixelFormat.RGBA_8888, 2)
    reader.setOnImageAvailableListener({ readerProxy ->
      if (delivered) return@setOnImageAvailableListener
      val image = readerProxy.acquireLatestImage() ?: return@setOnImageAvailableListener
      delivered = true
      phase.value = ScreenCapturePhase.CAPTURING
      try {
        val bitmap = image.toBitmap()
        _frames.value = ScreenCaptureFrame(bitmap, System.currentTimeMillis())
      } catch (error: Exception) {
        Log.w(TAG, "frame conversion failed", error)
        phase.value = ScreenCapturePhase.FAILED
      } finally {
        image.close()
        releaseCapture()
        stopSelf()
      }
    }, Handler(handlerThread!!.looper))
    imageReader = reader
    virtualDisplay = activeProjection.createVirtualDisplay(
      "pagenote-screen-capture",
      size.width,
      size.height,
      metrics.densityDpi,
      DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
      reader.surface,
      null,
      null,
    )
    return START_NOT_STICKY
  }

  private fun Image.toBitmap(): Bitmap {
    val plane = planes[0]
    val buffer = plane.buffer
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width
    val bitmapWidth = width + rowPadding / pixelStride
    val full = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
    buffer.rewind()
    full.copyPixelsFromBuffer(buffer)
    return if (bitmapWidth == width) {
      full
    } else {
      val cropped = Bitmap.createBitmap(full, 0, 0, width, height)
      full.recycle()
      cropped
    }
  }

  private fun releaseCapture() = synchronized(this) {
    virtualDisplay?.release()
    virtualDisplay = null
    imageReader?.close()
    imageReader = null
    projection?.stop()
    projection = null
    handlerThread?.quitSafely()
    handlerThread = null
  }

  private fun startInForeground() {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, getString(R.string.screen_capture_channel_name), NotificationManager.IMPORTANCE_LOW),
      )
    }
    val notification: Notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_menu_camera)
      .setContentTitle(getString(R.string.screen_capture_notification_title))
      .setContentText(getString(R.string.screen_capture_notification_text))
      .setOngoing(true)
      .build()
    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
  }

  override fun onDestroy() {
    releaseCapture()
    super.onDestroy()
  }

  companion object {
    private const val TAG = "ScreenCaptureService"
    private const val CHANNEL_ID = "screen_capture"
    private const val NOTIFICATION_ID = 2101
    const val EXTRA_RESULT_CODE = "result_code"
    const val EXTRA_RESULT_DATA = "result_data"

    /** 最近的截屏帧（服务一次性交付；消费方读后可清空）。 */
    private val _frames = MutableStateFlow<ScreenCaptureFrame?>(null)
    val frames: StateFlow<ScreenCaptureFrame?> = _frames

    val phase = MutableStateFlow(ScreenCapturePhase.IDLE)

    fun consumeFrame(): ScreenCaptureFrame? {
      val current = _frames.value ?: return null
      _frames.value = null
      return current
    }

    fun start(context: Context, resultCode: Int, resultData: Intent) {
      phase.value = ScreenCapturePhase.PREPARING
      val intent = Intent(context, ScreenCaptureService::class.java).apply {
        putExtra(EXTRA_RESULT_CODE, resultCode)
        putExtra(EXTRA_RESULT_DATA, resultData)
      }
      androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    fun reset() {
      phase.value = ScreenCapturePhase.IDLE
      _frames.value = null
    }
  }
}
