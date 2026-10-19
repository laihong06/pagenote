# 长 Markdown 冷启动缺口（task 30 唯一剩余子项）

本文只记录 task 30（`android/docs/spec/tasks.md` task 30）尚未覆盖的缺口，不关闭 task 30，不修改任何测试与实现。

## 结论

- task 30 仍为未关闭状态（`tasks.md` 中为 `- [ ] 30`）。
- task 30 的唯一剩余子项只有一条：**在 API 34 平板 AVD 上，对隔离的 69 页项目执行应用进程冷启动、恢复/跳转第 31 页、编辑和保存，确认无系统 ANR**。
- 已有的三项合成回归全部通过，但**均未覆盖冷进程（系统进程死亡后重建）**，也**未使用原用户 PDF**，因此不能视为该剩余子项已完成。
- 小米实机曾报告的冷启动 ANR 记为**未复现的环境差异**，不阻塞模拟器验收，也不作为已定位根因的证据。

## 已有覆盖（通过，但不覆盖冷进程）

以下三项均为 `android/app/src/androidTest/java/io/pagenote/android/NoteLongMarkdownEditorTest.kt` 中的合成回归（只读引用，未修改），在 API 34 平板 AVD 通过，恢复/布局断言低于 5 秒：

1. `longUnbrokenToken_laysOutAndRemainsEditableWithoutChangingSource`：40 KB 连续 token 的首轮布局、编辑与按页保存，原文精确保留。
2. `pageJumpToLongMarkdown_remainsResponsiveAndSavesOnlyTargetPage`：31 页合成项目跳转第 31 页长笔记、编辑且只保存目标页。
3. `reopeningRecentProjectRestoresLongMarkdownPageWithoutBlocking`：69 页合成 PDF + 约 57 KB 第 31 页笔记（含 40 KB 连续 token），经真实 SAF“最近项目”重开并恢复第 31 页 checkpoint，恢复低于 5 秒，保存后只有目标笔记页变化。

上述第三项使用的是测试内生成的最小合成 PDF（`minimalPdf`）与 SAF 测试 Provider，不是原用户 PDF；三项均未执行进程 kill / 冷进程重建。

## 剩余子项的验收边界

- 对象：隔离的 69 页验收副本（不得改动桌面原件与原用户 PDF）。
- 动作：应用进程冷启动 → 恢复/跳转第 31 页 → 编辑 → 保存。
- 通过标准：无系统 ANR 对话框，正文完整可编辑，保存回读后只有当前页的测试标记变化。
- 环境：API 34 平板 AVD 可承担验收；不新增 OEM / API / 设备矩阵，不要求实体设备。
- 记录口径：小米 Pad 5 Pro 冷启动 ANR 仍记为“未复现的环境差异”；`minikin::getNextWordBreakForCache` 仍是候选因素而非已证实根因（详见 `large-markdown-page-layout.md` 证据节）。

## 非目标

- 不声称 task 30 已关闭。
- 不把合成回归的通过结论外推为“冷启动已验证”或“原用户 PDF 已验证”。
- 不修改 `NoteLongMarkdownEditorTest.kt`，不新增实现代码。

## 关联

- 任务：`android/docs/spec/tasks.md` task 30。
- 契约：`android/docs/spec/features/large-markdown-page-layout.md`（冷进程恢复另由 task 30 验收）。
- 回归（只读）：`android/app/src/androidTest/java/io/pagenote/android/NoteLongMarkdownEditorTest.kt`（三项合成用例）。
