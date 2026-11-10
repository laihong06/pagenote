# Android note UI Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement task by task.

**Goal:** Rework bookshelf, reader canvas, pen toolbox and settings using the supplied reference manual while preserving current features.
**Architecture:** Adapt Compose presentation around existing callbacks, persistence and reader state. Keep Markdown S renderer and original project format intact.
**Tech Stack:** Kotlin, Jetpack Compose, Android instrumentation.
**Spec:** docs/superpowers/specs/2026-11-08-android-note-ui-redesign.md

## Global constraints

Preserve existing uncommitted changes, file authorization, note and ink saving, accessibility and test tags. Do not claim exact reference parity without runtime evidence. Do not accept third-party terms without authorization.

## Tasks

- [x] Capture actual Android baseline and inspect existing tests and window breakpoints.
- [x] Bookshelf: add responsive navigation and create menu; test search, actions and navigation in compact and expanded layouts.
- [x] Canvas and pen toolbox: adapt visual chrome and pen controls while preserving input, scroll and existing semantics; test pen selection and note interaction.
- [x] Settings: reorganize existing options and retain save/cancel/error and every setting; test access and persistence.
- [x] Run focused tests, build, capture actual before/after states, record environment, limitations and changelogs.

## 进度记录（2026-11-09）

- 书架：手机搜索和目录行分开布局、宽屏侧栏、右下角创建菜单、移除重复的大新建卡片；书架创建/保存/封面/搜索/重开代表流程通过模拟器测试。宽屏侧栏及完整的参考视觉对照仍需继续。
- 画布/笔盒：笔栏跟随主题，可收起和长按打开样式；独立交互测试通过。画布整体布局和参考视觉差距仍需继续。
- 设置：保留原选项，增加分类跳转，相关保存/取消/跳转测试通过；分组完整性与参考界面仍需继续。
- 已采集工作区 debug 的模拟器运行截图，详见 `docs/android/guide/images/ui-redesign/README.md`；同状态前后证据与横屏覆盖尚不完整。

- 追加进度：窄屏书架加入抽屉，宽屏侧栏可收起；设置分类补充阅读、夜览和模型入口。平板尺寸覆盖下完成书架侧栏、设置分类和主阅读器/笔栏代表交互；发现并修复笔身绘制越界，像素回归通过。手机组合运行 27/27。参考应用实际运行画面未采集，视觉差距、同状态前后截图和完整手写/设置回归仍待继续。
- 追加验收：在 root34 的 2560×1600 / 240 dpi 平板覆盖尺寸运行 `InkSelectionClipboardActivityTest,AnnotationWriteRecoveryTest,ReaderPaneDividerTest,PenAnnotationsUriResolutionTest,ReaderSettingsPagePrefetchTest`，12/12 通过。相同组合在 1080×2400 / 420 dpi 手机上为 10/12：笔迹剪贴板测试读取空剪贴板，分栏测试断言 52 但实际显示 51；单独重跑仍失败。平板通过不证明手机路径正常，原因需继续调查，不能将手机该组合记为通过。
- 手机失败复核：原剪贴板测试按平板的常驻菜单假设执行；手机抽屉选命令后会关闭，旧测试未重开抽屉而点击了背后的区域。测试现按手机用户操作重开菜单，保留原来的复制、粘贴、撤销和文件断言。分栏手势测试原固定期望 52，未扣除设备触摸阈值；现按实际 `touchSlop` 精确计算预期，独立单测仍验证分栏比例数学。修正测试后手机同一组合 12/12 通过，平板两项针对性重跑通过；未借此宣称全部 UI/设备验收。
- 2026-11-10：平板设置由居中弹窗改为右侧面板，手机沿用弹窗；同一设置内容、分类跳转和保存/取消复用。平板尺寸覆盖下书架、设置、笔盒、笔迹与分栏组合 22/22 通过；为手机和平板使用独立 `.baseline` 调试包和相同合成项目补齐基线截图，来源与局限见截图目录 README。参考应用四页仍无运行时截图，无法确认像素级一致。
- 后续补充笔型与颜色快捷面板和可恢复的画布专注模式，专注模式下笔记面板保持挂载并验证未保存草稿。代码审查发现并修复手机书架忙碌时仍可更换目录、平板设置遮罩不能关闭；补充保存中不可关闭及窄屏阅读器标题可见性测试。参考应用多组收藏仍未实现，见设计中的范围说明。
- 最终回归：Android 14 手机尺寸的综合组合 41/41；最后一轮布局及边界修复后相关手机组合 16/16、平板覆盖尺寸组合 23/23。模拟器尺寸及密度已恢复；上述为代表性测试，并非全部设备和状态的覆盖。
