# Android Markdown 段落格式实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 可写页笔记中接通标题 1–3、无序列表、有序列表和引用命令。

**Architecture:** 新增纯 Kotlin Markdown 行前缀变换 helper，返回替换范围、替换文本和折叠光标；Reader 菜单复用现有 writable/busy gating，标题通过轻量级 1/2/3 级对话框选择，其余段落命令直接应用前缀。EditText watcher 继续负责 draft 更新，不由格式命令保存文件。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Android `EditText`、JUnit 4、Compose UI instrumentation、Markwon 4.6.2。

**Spec:** `android/docs/spec/features/markdown-paragraph-formatting.md`

## Global Constraints

- 当前目标仅为 Android 14/API 34 小米平板 5 Pro（骁龙 870）；API 34 平板 AVD 仅作平台代理。
- 桌面当前富文本主路径用浏览器块命令；Android 源码编辑器按桌面 `setupMarkdownTools` 的源码前缀回退路径实现。该语义与浏览器的列表 toggle 不完全相同，但保留源码、易预测且避免有损替换。
- 标题仅支持桌面现有 1–3 级。`task_list` 在 Android 源码模式沿用 `src/editing.js` 的 `- [ ] ` 逐行前缀；字号无 Markdown 表示。
- 仅运行相关 JVM 测试、一个 API 34 平板 AVD 聚焦仪器测试和 Debug APK 构建。
- 每次推送都更新根 `CHANGELOG.md`；当前分支 `codex/android-port`，远端个人 fork。

## Review Focus

- 起点/终点逐行扩展与桌面源码回退一致，包括选区结束在行首、EOF、空行和空文档。
- Kotlin UTF-16 索引与 Android EditText 选区一致，范围外内容保持原样，光标落在变换后范围末尾。
- 标题弹窗的取消/选择和可写/busy gating，不应丢失或误用当前编辑选区。
- 通过 `Editable.replace` 更新草稿，不自动保存，不影响相邻页。

---

### Task 1: 纯 Kotlin 段落变换

**Files:**

- Create: `android/app/src/main/java/io/pagenote/android/MarkdownParagraphFormatting.kt`
- Test: `android/app/src/test/java/io/pagenote/android/MarkdownParagraphFormattingTest.kt`

**Interface:**

- `MarkdownParagraphPrefix` 定义 `HEADING_1/2/3`、`UNORDERED_LIST`、`ORDERED_LIST`、`TASK_LIST`、`QUOTE`。
- `data class MarkdownParagraphEdit(replaceStart, replaceEnd, replacement, cursor)`。
- `MarkdownParagraphFormatting.prefixLines(markdown, selectionStart, selectionEnd, prefix): MarkdownParagraphEdit`。

- [x] **Step 1: Write focused failing tests**
  - 验证每种 prefix 的输出、跨行和空行、选区行边界、折叠光标、反向/越界选区、emoji UTF-16 偏移、范围外内容和新光标。
- [x] **Step 2: Verify RED**
  - Run: `cd android && ./gradlew :app:testDebugUnitTest --offline --tests io.pagenote.android.MarkdownParagraphFormattingTest`
  - Expected: 编译失败，因为 helper API 尚不存在。
- [x] **Step 3: Implement the minimal pure Kotlin helper**
  - 精确遵循桌面源码回退行扩展规则；不加入 toggle、自动空行或保存行为。
- [x] **Step 4: Verify GREEN**
  - Run the same focused JVM command; all helper tests pass.

### Task 2: Reader 命令和 Android 14 聚焦闭环

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Test: `android/app/src/androidTest/java/io/pagenote/android/NoteMarkdownFormatActivityTest.kt`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

- [x] **Step 1: Add the failing API 34 reader test**
  - 在已有格式仪器类增加段落命令测试：无可写编辑器时禁用；标题弹窗可选 1/2/3；标题、无序、有序、引用能按选区变换；检查草稿、页级保存、相邻页不变和预览内容。
- [x] **Step 2: Verify RED on the single API 34 tablet AVD**
  - 仅运行 `NoteMarkdownFormatActivityTest` 的该方法，显式指定当前 API 34 arm64 平板 AVD serial。
  - Expected: 段落命令仍 disabled/pending 或新测试未通过。
- [x] **Step 3: Implement guarded menu actions and heading level dialog**
  - 添加 helper 应用函数，调用前重新检查编辑器可写/busy；使用 `Editable.replace` 并折叠光标。
  - 标题命令只打开 1/2/3 级对话框，确认时应用，取消不改文本。
  - 接通 `heading`、`unordered_list`、`ordered_list`、`task_list`、`quote`；保留字号为禁用。
- [x] **Step 4: Verify the focused API 34 reader flow**
  - 重跑该方法；确认 watcher、保存和当前页范围符合断言。
- [x] **Step 5: Focused tests, Debug APK, and push**
  - 运行新增 helper JVM 测试、单项仪器测试、`:app:assembleDebug`。
  - 更新 task 5.9 与 `CHANGELOG.md`，记录 AVD 代理与 Xiaomi 真机仍待 task 12。Task List 的增量测试/实现由 `markdown-task-list-insertion-plan.md` 单独追踪。
  - 执行 `git diff --check` 和相关文档格式检查，提交并推送到 `fork/codex/android-port`。
