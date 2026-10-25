package io.pagenote.android

/**
 * 悬浮截屏入口预留接口（移植差距清单 #24 依赖，本批次只预留不实现）。
 *
 * 批次 #24 的悬浮窗/悬浮球截屏入口实现本接口后注入 [ScreenCaptureController]：
 * - [isAvailable]：当前设备/权限下悬浮入口是否可用（如悬浮窗权限已授予）；
 * - [requestLaunch]：从悬浮入口触发一次系统截屏，返回是否已受理。
 *
 * 系统截屏命令在启动 MediaProjection 流程前先询问悬浮入口，未实现或不可用时
 * 走现有的 Activity 级 MediaProjection 授权路径，行为与批次 10 基线一致。
 */
interface FloatingCaptureEntry {
  fun isAvailable(): Boolean

  /** 预留：由悬浮入口发起截屏。返回 false 表示未受理（未实现/不可用），回退 Activity 路径。 */
  fun requestLaunch(): Boolean
}

/** 批次 10 基线：悬浮入口未实现，恒不可用，绝不触发截屏。 */
object NoFloatingCaptureEntry : FloatingCaptureEntry {
  override fun isAvailable(): Boolean = false

  override fun requestLaunch(): Boolean = false
}
