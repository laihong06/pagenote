# Android Markdown 公式插入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在 Android 可写页笔记中启用 Insert > Formula。

**Architecture:** 新增纯 Kotlin helper，生成桌面源码模式 `$$` 块公式和正文选区；Reader 菜单复用现有 writable/busy gating，通过 `Editable.replace` 更新草稿，不负责保存。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Android `EditText`、JUnit 4、Compose UI instrumentation、Markwon LaTeX renderer。

**Spec:** `android/docs/spec/features/markdown-formula-insertion.md`

## Global Constraints

- 目标设备仅为 Android 14/API 34 小米平板 5 Pro（骁龙 870）；唯一 API 34 平板 AVD 是平台代理，不是实机证据。
- 模板精确复用桌面源码模式 `\n\n$$\n` + 正文 + `\n$$\n\n`；空选区公式正文固定为 `E=mc^2`。
- 不加新依赖、不校验/改写 TeX、不实现行内公式，不开放表格、链接或粘贴 Markdown。
- 仅运行受影响 JVM 测试、一个 API 34 AVD 聚焦仪器流程和 `:app:assembleDebug`。
- 每次推送更新根 `CHANGELOG.md`，推送到个人 fork 的 `codex/android-port`。

## Review Focus

- 双换行和公式分隔符精确沿用 `src/editing.js`，不裁剪或规范化周围内容。
- 选区及结果正文 selection 均以 UTF-16 单位计算，包含 emoji 与反向/越界输入。
- 操作只更新当前 EditText 草稿；不绕过 revision 保存，也不影响相邻页。
- 预览须复用现有离线 LaTeX renderer；测试不启动外部 picker 或其它 app。

---

### Task 1: 块公式选区变换

**Files:**

- Create: `android/app/src/main/java/io/pagenote/android/MarkdownFormulaInsertion.kt`
- Test: `android/app/src/test/java/io/pagenote/android/MarkdownFormulaInsertionTest.kt`

**Interface:**

- `data class MarkdownFormulaEdit(replaceStart, replaceEnd, replacement, selectionStart, selectionEnd)`。
- `MarkdownFormulaInsertion.createEdit(markdown, selectionStart, selectionEnd): MarkdownFormulaEdit`。

- [x] **Step 1: Write focused failing tests**
  - 覆盖桌面块公式模板、正文选区、`E=mc^2` 折叠选区、emoji/多行、反向/越界范围及周围文本保持。
- [x] **Step 2: Verify RED**
  - Run: `cd android && ./gradlew :app:testDebugUnitTest --offline --tests io.pagenote.android.MarkdownFormulaInsertionTest`
  - Expected: 编译失败，因为 helper API 尚不存在。
- [x] **Step 3: Implement the minimal pure Kotlin helper**
  - 使用固定 opening/closing，夹取并规范化 UTF-16 范围；不依赖 Android API。
- [x] **Step 4: Verify GREEN**
  - Run the same focused JVM command; all helper tests pass。

### Task 2: Reader 命令与 Android 14 聚焦闭环

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/NoteMarkdownFormatActivityTest.kt`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

- [x] **Step 1: Add the failing API 34 reader test**
  - 在现有格式 Activity 测试中验证 Formula 无可写编辑器时禁用；选中公式正文后生成精确 `$$` 块并继续选中正文；草稿 watcher、保存后仅当前页变化，预览显示公式且不显示块标记。
- [x] **Step 2: Verify RED on the single API 34 tablet AVD**
  - 仅运行新增方法，显式使用 `ANDROID_SERIAL=emulator-5554` 和 class/method instrumentation filter。
  - Expected: `formula` 命令仍为 pending/disabled 或新增行为断言失败。
- [x] **Step 3: Implement guarded Insert > Formula action**
  - 只替换 `pendingCommand("formula", ...)`，复用 `canFormatMarkdown` gate，调用 helper 并经原生 `Editable.replace` 设置正文 selection。
- [x] **Step 4: Verify the focused API 34 reader flow**
  - 重跑新增方法并检查草稿、revision 保存、块公式预览与相邻页保持。
- [x] **Step 5: Focused tests, Debug APK, and docs**
  - 运行 helper JVM 测试、单项 API 34 AVD 仪器测试和 `:app:assembleDebug`。
  - 更新 task 5.11 与 `CHANGELOG.md`；记录 AVD 代理性质及 Xiaomi 真机验收仍归 task 12。
  - 执行 `git diff --check` 与相关 Markdown 格式检查。按仓库流程，独立审查后提交并推送到个人 fork。
