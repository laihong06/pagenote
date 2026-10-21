# Android Markdown 行内格式实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 可写 Markdown 笔记中启用桌面一致的粗体、斜体和删除线命令。

**Architecture:** 用纯 Kotlin 变换器根据 `EditText` UTF-16 选区生成局部替换及新选区；Reader 菜单只在可写编辑器可用时启用命令，并将变更写回现有原生编辑器，让现有 watcher、草稿恢复与按页 revision 保存继续生效。

**Tech Stack:** Kotlin、Jetpack Compose、Android `EditText`、Markwon 4.6.2、JUnit 4。

**Spec:** `android/docs/spec/features/markdown-inline-formatting.md`

## Global Constraints

- Android 最低版本保持 API 34；当前设备目标仅为 Xiaomi Pad 5 Pro / Snapdragon 870 / Android 14。
- 保持桌面标记语义：粗体 `**`、斜体 `*`、删除线 `~~`；不新增项目格式字段。
- 仅使用现有 Markwon Markdown 编辑/预览和 Android 原生编辑控件，不添加运行时依赖。
- 每次 push 更新根 `CHANGELOG.md`；分支为 `codex/android-port`，推送目标为 fork 的同名分支。
- 只跑受影响 JVM 测试、一项聚焦 API 34 仪器测试和 Debug 构建；不扩展设备/API 矩阵。

## Review Focus

- 选区只按 UTF-16 范围改变，emoji 等补充平面字符之前的偏移不损坏。
- 菜单失焦后仍采用编辑器当前选区，且结果选区落在包裹内容内。
- 无选区时插入并选中占位文本；预览态/只读态/忙碌态不执行。
- 变更通过 `EditText` watcher 写入 `ReaderSessionViewModel.noteDraft`，不得绕过草稿恢复或 revision 保存。
- 保存仅更新目标页，保留分页后的相邻页内容；冲突保护仍使用既有实现。

---

### Task 1: SPEC 与测试计划

**Files:**

- Create: `android/docs/spec/features/markdown-inline-formatting.md`
- Create: `android/docs/spec/markdown-inline-formatting-plan.md`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

- [x] 明确三种行内命令、编辑器可用条件、UTF-16 选区和现有保存契约；将计划及父任务登记到 Android SPEC。
- [x] 先提交并推送仅文档变更，代码实现仍未开始。

### Task 2: 纯 Kotlin 行内格式变换

**Files:**

- Create: `android/app/src/main/java/io/pagenote/android/MarkdownInlineFormatting.kt`
- Test: `android/app/src/test/java/io/pagenote/android/MarkdownInlineFormattingTest.kt`

**Interface:**

- `enum class MarkdownInlineStyle`: `BOLD`, `ITALIC`, `STRIKETHROUGH`，分别提供 `**`, `*`, `~~` 成对标记。
- `data class MarkdownInlineEdit`: 替换起止偏移、替换文本和编辑后的选择起止偏移。
- `MarkdownInlineFormatting.wrapSelection(markdown, selectionStart, selectionEnd, style, placeholder): MarkdownInlineEdit`：规范化/夹取 UTF-16 范围；折叠选区时使用占位文本。

- [x] **Step 1: Write failing tests**
  - `wrapsSelectedRangeAndLeavesSurroundingMarkdownUntouched`：仅替换选中片段，且新选择仍覆盖原内容。
  - `insertsAndSelectsPlaceholderForCollapsedSelection`：光标位置插入标记与占位文本，新选择覆盖占位文本。
  - `usesUtf16OffsetsAfterSupplementaryCharacters`：emoji 前的 UTF-16 代理对完整保留，后续文字范围精确包裹。
  - `normalizesReversedAndClampsOutOfRangeSelection`：反向选择规范化，越界偏移夹取到源文边界。
- 另覆盖三种样式各自使用桌面分隔符的 `usesDesktopDelimitersForEachInlineStyle`。
- [x] **Step 2: Verify RED**
  - Run: `cd android && ./gradlew :app:testDebugUnitTest --offline --tests io.pagenote.android.MarkdownInlineFormattingTest`
  - Expected: compilation fails because the formatter API does not yet exist.
- [x] **Step 3: Implement the minimal formatter**
  - Create the enum/result/interface above; do not mutate the source inside the formatter.
- [x] **Step 4: Verify GREEN**
  - Run the same focused Gradle command; all five formatter tests pass.

### Task 3: Reader 菜单接线与保存闭环

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Test: `android/app/src/androidTest/java/io/pagenote/android/NoteMarkdownFormatActivityTest.kt`
- Modify: `android/docs/spec/tasks.md`
- Modify: `android/docs/spec/markdown-inline-formatting-plan.md`
- Modify: `CHANGELOG.md`

- [x] **Step 1: Write the failing reader test**
  - `menuFormattingCommandsWrapSelectionAndSaveOnlyCurrentPage`：用 SAF 测试 Provider 打开两页笔记；检查预览态禁用，选中当前页文字依次执行粗体、斜体、删除线；断言源码 draft 精确为 `Lead ***~~Selected~~*** tail`，点击保存并重读，确认第 1 页变更、第 2 页原样保留，并在预览中确认三种原生格式 span。
  - 测试先断言项目打开前命令禁用、可写编辑器打开后命令启用。
- [x] **Step 2: Verify RED on the single API 34 tablet AVD**
  - Run only `NoteMarkdownFormatActivityTest#menuFormattingCommandsWrapSelectionAndSaveOnlyCurrentPage` with explicit `ANDROID_SERIAL=emulator-5554` and class/method instrumentation filter.
  - Expected: the current pending command is disabled and the test fails at its enabled assertion.
- [x] **Step 3: Wire menu actions to the active editor**
  - Keep the active `EditText` reference from `NotePanel` attach/release lifecycle; clear only the released instance.
  - Replace the three `pendingCommand` rows with guarded `actionCommand` handlers. Enable only when a writable current Markdown editor exists and `menuBusy` is false; report the existing project/read-only/busy reasons or a localized editor-required reason.
  - Apply the returned replacement to the current `Editable`, restore the selected content range, and request editor focus. Do not write `noteDraft` directly or save from the format command.
- [x] **Step 4: Verify the focused API 34 reader test passes**
  - Repeat the same one filtered instrumentation test; verify current-page save/reload and unchanged next page.
- [x] **Step 5: Verify app JVM tests and compile Debug APK**
  - Run: `cd android && ./gradlew :app:testDebugUnitTest --offline --tests io.pagenote.android.MarkdownInlineFormattingTest :app:assembleDebug --offline`
  - Expected: all formatter tests pass and `BUILD SUCCESSFUL`.
- [x] **Step 6: Check the diff and update SPEC status/log**
  - Run: `git diff --check` and `npx prettier --check android/docs/spec/tasks.md android/docs/spec/markdown-inline-formatting-plan.md`.
  - Mark task 5.7 and this plan's implemented steps complete; add user-visible behavior and exact tests to `CHANGELOG.md`.

After verification, commit source/tests/log separately from the already pushed documentation stage, then push to `fork/codex/android-port`.
