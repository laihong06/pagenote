# Android Markdown 链接插入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 可写页笔记中启用 Insert > Link。

**Architecture:** 新增纯 Kotlin helper，按桌面源码模式生成链接模板、替换范围和标签选区；Reader 菜单复用 writable/busy gating，通过 `Editable.replace` 更新草稿。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Android `EditText`、JUnit 4、Compose UI instrumentation、Markwon。

**Spec:** `android/docs/spec/features/markdown-link-insertion.md`

## Global Constraints

- 目标设备仅为 Android 14/API 34 小米平板 5 Pro（骁龙 870）；唯一 API 34 平板 AVD 是平台代理，不是实机证据。
- 精确复用桌面源码模式 `[` + 标签 + `](https://example.com)`；无选区标签用本地化 `Link text` / `链接文字`，并选中标签。
- 不加新依赖、不自动保存、不改其它页，不新增链接弹窗、Markdown 转义或网络导航。
- 仅运行受影响 JVM 测试、一个 API 34 AVD 聚焦仪器流程和 `:app:assembleDebug`。
- 每次推送更新根 `CHANGELOG.md`，推送到个人 fork 的 `codex/android-port`。

## Review Focus

- 选区替换边界和标签选区按 Android UTF-16 offset 计算；反向/越界值需规范化和夹取。
- 空选区的占位词必须由 Android 本地化资源提供，不能硬编码到 helper。
- 原生 watcher 维护当前页草稿；保存仍走现有 revision 检查且不影响相邻页。
- 插入后预览必须出现真实链接 span，禁止插入操作打开外部网页。

---

### Task 1: 链接模板变换

**Files:**

- Create: `android/app/src/main/java/io/pagenote/android/MarkdownLinkInsertion.kt`
- Test: `android/app/src/test/java/io/pagenote/android/MarkdownLinkInsertionTest.kt`

**Interface:**

- `data class MarkdownLinkEdit(replaceStart, replaceEnd, replacement, selectionStart, selectionEnd)`。
- `MarkdownLinkInsertion.createEdit(markdown, selectionStart, selectionEnd, placeholder): MarkdownLinkEdit`。

- [x] **Step 1: Write focused failing tests**
  - 覆盖原文作为链接标签、空选区本地化 placeholder、反向/越界范围、选中标签以及周围文本不变。
- [x] **Step 2: Verify RED**
  - Run: `cd android && ./gradlew --offline :app:testDebugUnitTest --tests io.pagenote.android.MarkdownLinkInsertionTest`
  - Expected: 编译失败，因为 helper API 尚不存在。
- [x] **Step 3: Implement the minimal pure Kotlin helper**
  - 固定 URL `https://example.com`；规范化/夹取 UTF-16 范围，返回替换文本和仅覆盖标签的 selection range。
- [x] **Step 4: Verify GREEN**
  - 重跑同一聚焦 JVM 命令，helper 测试全通过。

### Task 2: Reader 命令与 Android 14 聚焦闭环

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Test: `android/app/src/androidTest/java/io/pagenote/android/NoteMarkdownFormatActivityTest.kt`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

- [x] **Step 1: Add the failing API 34 reader test**
  - 在现有格式 Activity 测试中验证 Link 无项目/预览时禁用；反向选区替换后仅标签选中；折叠选区插入本地化 placeholder 且仅其被选中；保存只改第一页，预览具有链接 span。
- [x] **Step 2: Verify RED on the single API 34 tablet AVD**
  - 仅运行新增方法，显式使用 `ANDROID_SERIAL=emulator-5554` 和 class/method instrumentation filter。
  - Expected: `link` 命令仍为 pending/disabled 或新增行为断言失败。
- [x] **Step 3: Implement guarded Insert > Link action**
  - 加入中英文 placeholder 资源；只替换 `pendingCommand("link", ...)`，复用 `canFormatMarkdown` gate，调用 helper 并经原生 `Editable.replace` 设置标签 selection。
- [x] **Step 4: Verify the focused API 34 reader flow**
  - 重跑新增方法并检查菜单门控、草稿、selection、revision 保存、链接预览及相邻页保持。
- [x] **Step 5: Focused tests, Debug APK, and docs**
  - 运行 helper JVM 测试、单项 API 34 AVD 仪器测试和 `:app:assembleDebug`。
  - 更新 Android task 22 与 `CHANGELOG.md`；记录 AVD 代理性质及 Xiaomi 真机验收仍归 task 12。
  - 执行 `git diff --check`。
- [x] **Step 6: Commit and push**
  - 提交实现与测试、CHANGELOG、任务状态；推送至 `fork/codex/android-port`。
