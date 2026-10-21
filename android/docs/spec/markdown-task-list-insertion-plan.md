# Android Markdown 任务列表插入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 可写页笔记中启用 Task List 段落命令。

**Architecture:** 复用 `MarkdownParagraphFormatting`，为既有 enum 加入桌面源码模式 `- [ ] ` 前缀；Reader 菜单复用现有 writable/busy gating 与 `Editable` 更新路径。

**Tech Stack:** Kotlin、Android `EditText`、JUnit 4、Compose UI instrumentation、Markwon TaskList。

**Spec:** `android/docs/spec/features/markdown-task-list-insertion.md`

## Global Constraints

- 目标设备仅为 Android 14/API 34 小米平板 5 Pro（骁龙 870）；唯一 API 34 平板 AVD 是平台代理，不是实机证据。
- 精确沿用桌面源码回退前缀 `- [ ] `；任务项 checkbox 仅用于预览，不点击写回。
- 不加依赖、不自动保存、不改其它页；字号继续禁用。
- 仅运行受影响 JVM 测试、一个 API 34 AVD 聚焦仪器用例和 `:app:assembleDebug`。
- 每次推送更新根 `CHANGELOG.md`，推送到个人 fork 的 `codex/android-port`。

## Review Focus

- 任务列表前缀必须是精确的 `- [ ] `，不能用“待办事项”占位文本。
- 多行/空行使用既有行扩展语义，不能在范围外添加或删除字符。
- 预览 checkbox 不得写回；变更仅通过 Markdown 编辑与显式页级保存。

---

### Task 1: 接通任务列表段落前缀

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MarkdownParagraphFormatting.kt`
- Test: `android/app/src/test/java/io/pagenote/android/MarkdownParagraphFormattingTest.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/NoteMarkdownFormatActivityTest.kt`
- Modify: `android/docs/spec/features/markdown-paragraph-formatting.md`
- Modify: `android/docs/spec/markdown-paragraph-formatting-plan.md`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

- [x] **Step 1: Write failing JVM and Reader assertions**
  - JVM cases add task prefix for selected single/multiple lines including blank lines.
  - Reader flow changes task-list command from disabled to enabled; verifies resulting note, saves only current page, and finds a not-done `TaskListSpan` in preview.
- [x] **Step 2: Verify RED**
  - Run the focused `MarkdownParagraphFormattingTest` and the existing `paragraphCommandsPrefixSelectedLinesAndSaveOnlyCurrentPage` method on `emulator-5554` API 34.
  - Expected: JVM cannot find the enum case; Reader command remains disabled.
- [x] **Step 3: Implement the exact source-mode prefix**
  - Add `TASK_LIST("- [ ] ")`; replace only `pendingCommand("task_list", ...)` with the existing paragraph action/helper and gate.
- [x] **Step 4: Verify GREEN**
  - Re-run the focused JVM test and single API 34 Reader flow; confirm current page save and GFM preview span.
- [x] **Step 5: Run focused regression and Debug build**
  - Run `:app:testDebugUnitTest`, focused AVD instrumentation, `:app:assembleDebug`, and `git diff --check`; update the parent task list and changelog.
- [x] **Step 6: Commit and push**
  - Commit implementation and tests, then push to `fork/codex/android-port`.
