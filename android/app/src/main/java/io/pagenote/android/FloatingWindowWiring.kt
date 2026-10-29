package io.pagenote.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * 悬浮窗的组合接线（移植差距清单 #24）：SYSTEM_ALERT_WINDOW 权限判定、
 * 系统设置页跳转与服务启动样板从 ReaderHome 抽出（同 ScreenCaptureWiring
 * 先例）。悬浮窗服务运行时，hub 的保存/截屏请求由 ReaderHome 收集。
 */
internal class FloatingWindowLauncher(private val context: Context) {

  /** 悬浮窗权限是否已授予（SYSTEM_ALERT_WINDOW → canDrawOverlays）。 */
  fun isOverlayPermissionGranted(): Boolean = Settings.canDrawOverlays(context)

  /** 未授权时的系统设置页 Intent（引导用户授予“显示在其他应用上层”）。 */
  fun permissionSettingsIntent(): Intent = Intent(
    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
    Uri.parse("package:${context.packageName}"),
  ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  /**
   * 启动悬浮窗服务：注入当前页草稿（[FloatingWindowHub.attach]）后拉起
   * 前台服务。调用侧先确保 [isOverlayPermissionGranted]。
   */
  fun launch(
    currentPage: Int,
    noteDraft: String,
    projectKey: String,
    noteRevision: String,
  ) {
    FloatingWindowHub.attach(currentPage, noteDraft, projectKey, noteRevision)
    FloatingWindowService.start(context)
  }

  /** 停止悬浮窗服务（菜单里悬浮窗运行中 → 停止）。 */
  fun stop() {
    FloatingWindowService.stop(context)
  }

  /** 悬浮窗服务是否运行中（菜单文案/状态切换）。 */
  fun isActive(): Boolean = FloatingWindowHub.phase.value == FloatingWindowHub.Phase.ACTIVE
}

@Composable
internal fun rememberFloatingWindowLauncher(context: Context): FloatingWindowLauncher =
  remember(context) { FloatingWindowLauncher(context) }
