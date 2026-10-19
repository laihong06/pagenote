# Android Markdown 表格插入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在 Android 可写页笔记中启用 Insert > Table。

**Architecture:** 新增纯 Kotlin helper，生成桌面源码模式固定 GFM 表格并返回替换范围与折叠光标；Reader 菜单复用 writable/busy gating，通过 `Editable.replace` 更新草稿。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Android `EditText`、JUnit 4、Compose UI instrumentation、Markwon GFM Tables。

**Spec:** `android/docs/spec/features/markdown-table-insertion.md`

## Global Constraints

- 目标设备仅为 Android 14/API 34 小米平板 5 Pro（骁龙 870）；唯一 API 34 平板 AVD 是平台代理，不是实机证据。
- 复用桌面源码模式双列表格模板。与桌面一致，选中内容被模板替换；不加选择列数或单元格编辑器。
- 不加新依赖、不自动保存、不改其它页，不开放链接或粘贴 Markdown。
- 仅运行受影响 JVM 测试、一个 API 34 AVD 聚焦仪器流程和 `:app:assembleDebug`。
- 每次推送更新根 `CHANGELOG.md`，推送到个人 fork 的 `codex/android-port`。

## Review Focus

- 表头、分隔行、默认内容和前后换行须与 `src/editing.js` 完全一致。
- 折叠光标置于模板末尾；选区替换仅影响规范化/夹取后的 UTF-16 范围。
- 操作只更新当前 EditText 草稿；不绕过 revision 保存，也不影响相邻页。
- 预览需出现真实 Markwon `TableSpan`；不做额外设备或 API 矩阵。

---

### Task 1: 表格模板选区变换

**Files:**

- Create: `android/app/src/main/java/io/pagenote/android/MarkdownTableInsertion.kt`
- Test: `android/app/src/test/java/io/pagenote/android/MarkdownTableInsertionTest.kt`

**Interface:**

- `data class MarkdownTableEdit(replaceStart, replaceEnd, replacement, cursor)`。
- `MarkdownTableInsertion.createEdit(markdown, selectionStart, selectionEnd): MarkdownTableEdit`。

- [x] **Step 1: Write focused failing tests**
  - 覆盖固定模板、选区删除、折叠光标、反向/越界及前后文本保持。
- [x] **Step 2: Verify RED**
  - Run: `cd android && ./gradlew --offline :app:testDebugUnitTest --tests io.pagenote.android.MarkdownTableInsertionTest`
  - Expected: 编译失败，因为 helper API 尚不存在。
- [x] **Step 3: Implement the minimal pure Kotlin helper**
  - 固定插入模板，夹取并规范化 UTF-16 范围，光标返回模板末尾。
- [x] **Step 4: Verify GREEN**
  - Run the same focused JVM command; all helper tests pass。

### Task 2: Reader 命令与 Android 14 聚焦闭环

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/NoteMarkdownFormatActivityTest.kt`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

- [x] **Step 1: Add the failing API 34 reader test**
  - 在现有格式 Activity 测试中验证 Table 无可写编辑器时禁用；选中内容后精确替换为表格并折叠光标；草稿 watcher、保存后仅当前页变化，预览出现 Markwon `TableSpan`。
- [x] **Step 2: Verify RED on the single API 34 tablet AVD**
  - 仅运行新增方法，显式使用 `ANDROID_SERIAL=emulator-5554` 和 class/method instrumentation filter。
  - Expected: `table` 命令仍为 pending/disabled 或新增行为断言失败。
- [x] **Step 3: Implement guarded Insert > Table action**
  - 只替换 `pendingCommand("table", ...)`，复用 `canFormatMarkdown` gate，调用 helper 并经原生 `Editable.replace` 设置折叠光标。
- [x] **Step 4: Verify the focused API 34 reader flow**
  - 重跑新增方法并检查草稿、revision 保存、GFM 表格预览与相邻页保持。
- [x] **Step 5: Focused tests, Debug APK, and docs**
  - 运行 helper JVM 测试、单项 API 34 AVD 仪器测试和 `:app:assembleDebug`。
  - 更新 task 5.12 与 `CHANGELOG.md`；记录 AVD 代理性质及 Xiaomi 真机验收仍归 task 12。
  - 执行 `git diff --check` 与相关 Markdown 格式检查。

> Finalization: Implementation review, commit, and push to the personal fork happen after the checked test/documentation tasks, following the repository workflow.
