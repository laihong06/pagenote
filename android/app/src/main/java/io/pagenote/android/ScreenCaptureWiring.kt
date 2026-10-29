package io.pagenote.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * 系统截屏的组合接线（移植差距清单 #23）：MediaProjection 授权 launcher 与
 * 前台服务启动样板从 ReaderHome 抽出，控制其方法体积（同 AudioWiring 先例）。
 *
 * 悬浮截屏入口（#24）：悬浮窗服务运行时 [floatingEntry] 可受理悬浮球截屏
 * 请求；MediaProjection 授权本身必须由 Activity 弹出，悬浮路径最终经
 * [launchViaActivity] 回到主界面。
 */
internal class ScreenCaptureLauncher(private val context: Context) {
  private val projectionManager: MediaProjectionManager?
    get() = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager

  /** 悬浮入口（#24：悬浮窗服务运行中可用，否则回退 Activity 路径）。 */
  var floatingEntry: FloatingCaptureEntry = NoFloatingCaptureEntry

  /** 由组合内的 Activity 结果 launcher 注入；未进入组合时为 null。 */
  internal var launchActivity: ((Intent) -> Unit)? = null

  internal fun onProjectionApproved(resultCode: Int, resultData: Intent?) {
    val data = resultData ?: return
    ScreenCaptureService.start(context, resultCode, data)
  }

  /**
   * 发起一次系统截屏：悬浮入口可用则走悬浮请求路径（由悬浮球请求回主界面
   * 授权），否则启动 MediaProjection 授权 Activity。无投影服务或 launcher
   * 未接线时返回 false。
   */
  fun launch(): Boolean {
    if (floatingEntry.requestLaunch()) return true
    return launchViaActivity()
  }

  /** 直接发起 Activity 级 MediaProjection 授权（悬浮球 #24 路径的落点）。 */
  fun launchViaActivity(): Boolean {
    val intent = projectionManager?.createScreenCaptureIntent() ?: return false
    val launch = launchActivity ?: return false
    launch(intent)
    return true
  }
}

@Composable
internal fun rememberScreenCaptureLauncher(
  context: Context,
  onDenied: () -> Unit,
  onFailed: () -> Unit,
): ScreenCaptureLauncher {
  val launcher = remember(context) { ScreenCaptureLauncher(context.applicationContext) }
  val projectionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    when {
      result.resultCode == Activity.RESULT_OK ->
        launcher.onProjectionApproved(result.resultCode, result.data)
      else -> onDenied()
    }
  }
  launcher.launchActivity = { intent ->
    try {
      projectionLauncher.launch(intent)
    } catch (error: Exception) {
      onFailed()
    }
  }
  return launcher
}

/** 服务是否已在准备/抓帧（用于菜单禁用态与提示）。 */
internal fun screenCaptureBusy(): Boolean =
  ScreenCaptureService.phase.value == ScreenCapturePhase.PREPARING ||
    ScreenCaptureService.phase.value == ScreenCapturePhase.CAPTURING
