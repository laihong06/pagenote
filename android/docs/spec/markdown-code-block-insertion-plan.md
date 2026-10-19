# Android Markdown 代码块插入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 可写页笔记中启用 Insert > Code block。

**Architecture:** 新增纯 Kotlin helper，生成桌面源码模式 fenced block 和围栏内选择范围；Reader 菜单复用现有 writable/busy gating，通过 `Editable.replace` 更新草稿，不负责保存。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Android `EditText`、JUnit 4、Compose UI instrumentation、Markwon 4.6.2。

**Spec:** `android/docs/spec/features/markdown-code-block-insertion.md`

## Global Constraints

- 目标设备仅为 Android 14/API 34 小米平板 5 Pro（骁龙 870）；API 34 AVD 是平台代理，不是实机证据。
- 围栏精确复用桌面源码模式 `\n```python\n` + 正文 + `\n```\n`；折叠选区占位文本为本地化 `Code`/`代码`。
- 不加新依赖、不转义正文中的反引号、不实现语言选择/代码运行、不开放其它 Insert 项。
- 仅运行受影响 JVM 测试、一个 API 34 AVD 聚焦仪器流程和 `:app:assembleDebug`。
- 每次推送更新根 `CHANGELOG.md`，推送到个人 fork 的 `codex/android-port`。

## Review Focus

- 围栏前后换行是桌面既定语义，包含周边已有空行时也不裁剪或规范化。
- 选中范围以及代码正文选区必须以 UTF-16 单位计算；空选区插入占位词后选中占位词。
- 操作只作用于捕获的当前 EditText 选区、草稿和当前页；不绕过 revision 保存。
- 预览须用现有本地 renderer；测试不启动外部 picker 或其它 app。

---

### Task 1: Fenced code block 选区变换

**Files:**

- Create: `android/app/src/main/java/io/pagenote/android/MarkdownCodeBlockInsertion.kt`
- Test: `android/app/src/test/java/io/pagenote/android/MarkdownCodeBlockInsertionTest.kt`

**Interface:**

- `data class MarkdownCodeBlockEdit(replaceStart, replaceEnd, replacement, selectionStart, selectionEnd)`。
- `MarkdownCodeBlockInsertion.createEdit(markdown, selectionStart, selectionEnd, placeholder): MarkdownCodeBlockEdit`。

- [x] **Step 1: Write focused failing tests**
  - 覆盖 Python 围栏、正文保留与内部选区、折叠选区占位文本、多行/emoji、反向/越界选区和 surrounding text 不变。
- [x] **Step 2: Verify RED**
  - Run: `cd android && ./gradlew :app:testDebugUnitTest --offline --tests io.pagenote.android.MarkdownCodeBlockInsertionTest`
  - Expected: 编译失败，因为 helper API 尚不存在。
- [x] **Step 3: Implement the minimal pure Kotlin helper**
  - 使用固定 opening/closing，夹取并规范化 UTF-16 范围；不依赖 Android API。
- [x] **Step 4: Verify GREEN**
  - Run the same focused JVM command; all helper tests pass.

### Task 2: Reader 命令与 Android 14 聚焦闭环

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Test: `android/app/src/androidTest/java/io/pagenote/android/NoteMarkdownFormatActivityTest.kt`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

- [x] **Step 1: Add the failing API 34 reader test**
  - 在现有格式 Activity 测试中验证 Code block 无可写编辑器时禁用；选中多词后生成精确 Python 围栏并选中正文；草稿 watcher、保存后仅当前页变化，预览正文存在且 Markdown fence 标记不显示。
- [x] **Step 2: Verify RED on the single API 34 tablet AVD**
  - 仅运行新增方法，显式使用 `ANDROID_SERIAL=emulator-5554` 和 class/method instrumentation filter。
  - Expected: `code` 命令仍为 pending/disabled 或新增行为断言失败。
- [x] **Step 3: Implement guarded Insert > Code block action**
  - 添加本地化 placeholder；只替换 `pendingCommand("code", ...)`，启用时调用 helper，经 `Editable.replace` 并设置正文 selection。
- [x] **Step 4: Verify the focused API 34 reader flow**
  - 重跑新增方法并检查草稿、revision 保存、预览与相邻页保持。
- [x] **Step 5: Focused tests, Debug APK, and push**
  - 运行 helper JVM 测试、单项 API 34 AVD 仪器测试和 `:app:assembleDebug`。
  - 更新 task 5.10 与 `CHANGELOG.md`；记录 AVD 代理性质及 Xiaomi 真机验收仍归 task 12。
  - 执行 `git diff --check`、相关 Markdown 格式检查，提交并推送到个人 fork。
