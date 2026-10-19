# 长 Markdown 冷启动缺口（已验收）

本文记录 task 30 曾经未覆盖的冷进程缺口及其关闭证据；不表示小米 Pad 5 Pro 当前版本已重测。

## 结论

- task 30 已在 `android/docs/spec/tasks.md` 标为完成。
- 冷进程缺口通过 API 34 平板 AVD 的真实 force-stop、冷启动、Recent 重开、IME 页码跳转、编辑保存及第二次冷启动闭环补齐。
- 本次验收使用独立合成 fixture，不覆盖原用户 PDF；小米 Pad 5 Pro 当前修复版仍未重测。

## 已有覆盖（通过，但不覆盖冷进程）

以下三项均为 `android/app/src/androidTest/java/io/pagenote/android/NoteLongMarkdownEditorTest.kt` 中的合成回归（只读引用，未修改），在 API 34 平板 AVD 通过，恢复/布局断言低于 5 秒：

1. `longUnbrokenToken_laysOutAndRemainsEditableWithoutChangingSource`：40 KB 连续 token 的首轮布局、编辑与按页保存，原文精确保留。
2. `pageJumpToLongMarkdown_remainsResponsiveAndSavesOnlyTargetPage`：31 页合成项目跳转第 31 页长笔记、编辑且只保存目标页。
3. `reopeningRecentProjectRestoresLongMarkdownPageWithoutBlocking`：69 页合成 PDF + 约 57 KB 第 31 页笔记（含 40 KB 连续 token），经真实 SAF“最近项目”重开并恢复第 31 页 checkpoint，恢复低于 5 秒，保存后只有目标笔记页变化。

上述第三项使用的是测试内生成的最小合成 PDF（`minimalPdf`）与 SAF 测试 Provider，不是原用户 PDF；三项均未执行进程 kill / 冷进程重建。

## 已完成子项的验收边界

- 对象：隔离的 69 页验收副本（不得改动桌面原件与原用户 PDF）。
- 动作：应用进程冷启动 → 恢复/跳转第 31 页 → 编辑 → 保存。
- 通过标准：无系统 ANR 对话框，正文完整可编辑，保存回读后只有当前页的测试标记变化。
- 环境：API 34 平板 AVD 可承担验收；不新增 OEM / API / 设备矩阵，不要求实体设备。
- 验收记录与保存回读见 `long-markdown-anr-instrumentation.md` 和 `large-markdown-page-layout.md`。
- 记录口径：小米 Pad 5 Pro 冷启动 ANR 仍记为“当前修复版未在该真机重测”；加入断点后 API 34 AVD 同一路径不再复现，静态排版是已观察到的触发路径，但各因素因果贡献未单独量化。

## 非目标

- 不把 AVD 通过外推为小米 Pad 5 Pro 当前修复版已验证。
- 不把合成 fixture 结果外推为原用户 PDF 已验证。
- 不修改 `NoteLongMarkdownEditorTest.kt`，不新增实现代码。

## 关联

- 任务：`android/docs/spec/tasks.md` task 30（已关闭）。
- 契约与验收证据：`android/docs/spec/features/large-markdown-page-layout.md`、`long-markdown-anr-instrumentation.md`。
- 回归：`android/app/src/androidTest/java/io/pagenote/android/NoteLongMarkdownEditorTest.kt`（4 项 API 34 AVD 用例）。
