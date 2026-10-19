# Task 12 剩余验收收敛（未关闭）

> 状态声明：本文仅收敛 `../tasks.md` task 12 的剩余验收条目，**不声称 task 12 已关闭**。
> 只读依据：`../tasks.md` task 12、task 30；`desktop-parity-gap-audit.md`；`desktop-command-checklist.md`。
> 本文件为验收口径文档，不修改任何实现与既有 SPEC 结论。

## 1. API 34 平板 AVD 批注/笔迹保存重载闭环

- 在唯一 API 34 平板 AVD（`Pixel_Tablet_API34`）确认：各创建一条 PDF 批注与手写笔迹，可保存并重载。
- 本轮相关通过项：`feature-pen` 7/7、`PdfPageViewportNavigationTest` 1/1、`ReaderSettingsPagePrefetchTest` 1/1。
- 已知 flake（不阻塞其他切片，待按 SPEC 单独调查）：
  - `PdfAnnotationSelectionClipboardActivityTest.nativeAnnotationSelectCopyPasteMoveDeleteUndoAndRedoPreserveFreehandItems`
    在 `Pixel_Tablet_API34` 连续两次复现 `waitUntil(10s)` 超时（第 193 行）。
  - 同文件 `endpointDwellPersistsNativeShapeAndUsesAnnotationUndoHistory` 通过。

## 2. 冷启动第 31 页 ANR 根因（归 task 30）

- 现象：冷启动重开项目后跳转第 31 页，曾弹出系统“pagenote 没有响应”；选择等待后恢复。
- 已知信息：仅限 pagenote 的日志显示主线程输入超时，首个可见 native 栈帧为
  `minikin::getNextWordBreakForCache`；根因未确认。
- 归属：根因调查归 task 30（`features/large-markdown-page-layout.md`），不计入本文件第 1/3 条的常规功能闭环门槛。
- task 30 的 API 34 合成回归（`NoteLongMarkdownEditorTest`、最近项目重开恢复、69 页合成恢复等）未复现小米冷启动 ANR，
  仅作为环境差异记录。

## 3. 代表性功能闭环与 Office 待实现声明

- API 34 平板 AVD 构建/安装与代表性功能闭环；附件和使用说明命令已分别在 task 32/33 接通。
- Office 编辑仍是 Android 待实现项（归 task 36）：`office_edit` / `save_return`
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
