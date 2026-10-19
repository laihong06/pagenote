# Task 12 验收记录（已关闭）

> 状态声明：`../tasks.md` task 12 已按下述限定范围关闭。本文保留验收结果及未测试硬件边界。
> 只读依据：`../tasks.md` task 12、task 30；`desktop-parity-gap-audit.md`；`desktop-command-checklist.md`。
> Office 本机编辑按获批 Android 移植范围回桌面端处理；本记录不声明 Android 具有 Office 编辑能力，也不改变 task 36/37 的独立状态。

## 1. API 34 平板 AVD 批注/笔迹保存重载闭环

- 在唯一 API 34 平板 AVD（`Pixel_Tablet_API34`）确认：各创建一条 PDF 批注与手写笔迹，可保存并重载。
- 2026-10-20 `PdfAnnotationSelectionClipboardActivityTest` 2/2、`InkSelectionClipboardActivityTest` 1/1 通过；覆盖形状 dwell、批注与笔迹共存、存储回读、剪贴板、移动/缩放、删除和页级撤销/重做。
- 先前 `nativeAnnotationSelectCopyPasteMoveDeleteUndoAndRedoPreserveFreehandItems` 连续两次超时。单项诊断发现触摸前 `PenCanvasView.selectedTool` 仍为 `PEN`；并将 `[width*0.25, width*0.10]` 映射到页外 y=-77.33。测试现等待原生 Select 状态，并将选择/移动/缩放触点由 PDF 页坐标转换为画布坐标；批注类 2/2 通过，原失败用例随后连续两次通过。未改生产批注行为。

## 2. 冷启动第 31 页 ANR 根因（归 task 30）

- 旧版冷启动重开后跳转第 31 页曾发生输入分发超时，首个可见 native 栈帧为 `minikin::getNextWordBreakForCache`。
- task 30 已在 API 34 AVD 完成真正 `force-stop` 冷启动、真实 IME 页码跳转、编辑/保存及再次冷启动验证；本轮无系统 ANR 或新的输入超时。回归只覆盖隔离合成 fixture，不代表小米 Pad 5 Pro 已重测当前版本。

## 3. 代表性功能闭环与 Office 待实现声明

- API 34 平板 AVD 安装 Debug 包，并完成批注、笔迹、逐页 Markdown 保存/回读、Recent 重开和离线 OCR 代表性闭环；附件和使用说明命令已分别在 task 32/33 接通。
- Office 编辑按获批 Android 移植范围回桌面端处理（独立 parity 任务归 task 36）：`office_edit` / `save_return`
  在 `desktop-command-checklist.md` 与 `desktop-parity-gap-audit.md` 中均为明确未接线 `pendingCommand()` 占位，
  终态结论归 task 37。任何未实现命令不得归因于 Android 版本。
- 范围限定：只确认上述闭环，不抽查多页或多种文档，不测压力曲线、延迟、边缘性能、电量、长时间稳定性，
  也不要求无端点配置时验证云请求。

## 侧键橡皮擦：未验证硬件行为

- 手写笔侧键橡皮擦未单独测试；不影响用户已报告的基础书写与压感正常，也不作为常规功能验收阻塞。
- 原生 View 仪器测试中合成 generic-motion 侧键事件通过，仅验证应用事件逻辑，
  不代表实体手写笔的压力、误触、延迟及具体按键映射已验证。

## 关联

- 任务：`../tasks.md` task 12（验收主体）、task 30（ANR 根因）、task 32/33（附件/使用说明）、task 36/37（Office 待实现/收尾审计）。
- 命令清单：`desktop-command-checklist.md`；缺口审计：`desktop-parity-gap-audit.md`。
