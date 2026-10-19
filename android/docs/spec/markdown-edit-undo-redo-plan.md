# Android Markdown 编辑撤销与重做实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 可写页笔记中接通 Edit > Undo/Redo。

**Architecture:** 新增纯 Kotlin `MarkdownEditHistory`，以当前页文本快照记录编辑；NotePanel watcher 提供前后文本，Reader 菜单更新历史栈后仍经原生 Editable 恢复，保存继续走现有页级 revision 流程。

**Tech Stack:** Kotlin、Android `EditText`/`TextWatcher`、Jetpack Compose、JUnit 4、Compose UI instrumentation。

**Spec:** `android/docs/spec/features/markdown-edit-undo-redo.md`

## Global Constraints

- 目标设备仅为 Android 14/API 34 小米平板 5 Pro（骁龙 870）；唯一 API 34 平板 AVD 是平台代理，不是实机证据。
- 编辑分组合并窗口 500ms、最多 80 个 undo 快照；保存不清栈，项目/页/编辑器内容重载清栈。
- 不改项目格式、不持久化历史、不合并笔迹历史、不自动保存、不加新依赖。
- 仅运行受影响 app JVM 测试、一个 API 34 AVD 聚焦仪器流程和 `:app:assembleDebug`。
- 每次推送更新根 `CHANGELOG.md`，推送到个人 fork 的 `codex/android-port`。

## Review Focus

- watcher 的程序化编辑和键入都需记录；执行 Undo/Redo 自身不得再次被记录为新编辑。
- 新编辑必须清 redo；间隔恰好 500ms 时继续合并，501ms 时开始新快照，边界由测试固定。
- 历史必须和项目/页绑定，切页或重载不能把旧页内容写进新页。
- Undo/Redo 只变更草稿；保存仍使用现有 revision 防覆盖且不改相邻页。

---

### Task 1: 纯 Kotlin 页级编辑历史

**Files:**

- Create: `android/app/src/main/java/io/pagenote/android/MarkdownEditHistory.kt`
- Test: `android/app/src/test/java/io/pagenote/android/MarkdownEditHistoryTest.kt`

**Interface:**

- `MarkdownEditHistory(initialMarkdown: String)`。
- `record(before: String, after: String, timestampMillis: Long)`。
- `undo(current: String): String?`, `redo(current: String): String?`, `reset(markdown: String)`。
- Read-only `canUndo` and `canRedo` properties.

- [x] **Step 1: Write failing deterministic history tests**
  - 覆盖空栈、往返、500ms 分组、redo 清空、80 条上限、reset 和文本失配。
- [x] **Step 2: Verify RED**
  - Run: `cd android && ./gradlew --offline :app:testDebugUnitTest --tests io.pagenote.android.MarkdownEditHistoryTest`
  - Expected: 编译失败，因为 helper API 尚不存在。
- [x] **Step 3: Implement the minimal history helper**
  - 使用有界双栈及单调时间戳分组；undo/redo 前校验传入文本与内部当前文本一致，不一致时清栈并拒绝。
- [x] **Step 4: Verify GREEN**
  - 重跑同一聚焦 JVM 命令，历史边界全通过。

### Task 2: Reader watcher 与菜单闭环

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/NoteMarkdownFormatActivityTest.kt`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

- [x] **Step 1: Add failing API 34 Reader flow**
  - 验证无历史时 Undo/Redo 禁用；一笔文本修改后 Undo 可用、Redo 禁用；撤销后反向可用；Redo 恢复内容；再次 Undo 后新编辑清除 redo；保存只改当前页；预览和只读态禁用。
- [x] **Step 2: Verify RED on the sole API 34 tablet AVD**
  - 使用唯一已连接的 `emulator-5554` 与 class/method instrumentation filter；预期菜单仍为 pending/disabled。最终 GREEN 验证显式设置 `ANDROID_SERIAL=emulator-5554`。
- [x] **Step 3: Wire watcher history and guarded actions**
  - 在 NotePanel watcher 捕获前后文本；页面/外部重载时 reset；Undo/Redo 替换文本期间抑制 history record，但保留 `onMarkdownChange` 草稿回调。
  - 用 Compose state 刷新菜单 enabled 状态；替换 pending 菜单命令，按可写/忙碌/历史栈门控。
- [x] **Step 4: Verify GREEN on API 34 AVD**
  - 断言文本、按钮状态、当前页保存和邻页不变；不启动外部 UI。
- [x] **Step 5: JVM, full app tests, Debug build and docs**
  - 运行 history 聚焦单测、`:app:testDebugUnitTest`、单项 AVD 仪器测试、`:app:assembleDebug` 与 `git diff --check`；更新 task 24 和变更日志。
- [x] **Step 6: Commit and push**
  - 提交实现与测试并推送至 `fork/codex/android-port`。
