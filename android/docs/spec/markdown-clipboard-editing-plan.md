# Android Markdown 剪贴板命令实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Android 可写 Markdown 笔记接通剪切、复制和全选菜单命令。

**Architecture:** 在 MainActivity 菜单中以当前 `EditText` 的可写状态和选区状态控制命令；复制/剪切用 Android `ClipboardManager`，剪切与全选直接操作原生 `Editable`/选区，保留已有 watcher、草稿恢复和 revision 保存链路。

**Tech Stack:** Kotlin、Jetpack Compose、Android `EditText`/`ClipboardManager`、JUnit 4、AndroidX instrumentation。

**Spec:** `android/docs/spec/features/markdown-clipboard-editing.md`

## Global Constraints

- 最低版本为 Android 14/API 34；目标实体设备仅为 Xiaomi Pad 5 Pro / Snapdragon 870。
- 不新增运行时依赖，不改变项目格式或桌面行为。
- 仅运行受影响 app JVM 测试、一个 API 34 平板 AVD 聚焦仪器用例和 Debug 构建。
- 每次 push 更新根 `CHANGELOG.md`；本次只推送至 fork 的 `codex/android-port`，不发布二进制。

## Review Focus

- 空/折叠选区时剪切与复制必须禁用；聚焦仪器测试断言两个命令禁用。
- 反向选区复制/剪切必须取完整 UTF-16 范围；仪器测试用反向选区并校验剪贴板文本与结果草稿。
- 菜单操作后 watcher 更新草稿且只影响当前页；仪器测试保存并重读两页笔记。
- 复制不得修改草稿；同一测试对比复制前后内容。
- 预览态禁用编辑命令；同一测试切换预览后断言三个命令均禁用。

---

### Task 1: 接通菜单剪切/复制/全选

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Test: `android/app/src/androidTest/java/io/pagenote/android/NoteMarkdownClipboardActivityTest.kt`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

**Interface:** 仅使用现有 `markdownEditor: EditText?`、`noteSelection: TextRange?`、`noteWritable` 和 `menuBusy`；不增加跨模块 API。

- [x] **Step 1: Write the failing reader test**
  - `menuClipboardCommandsCopyCutSelectAllAndSaveOnlyCurrentPage`：基于两页 SAF fixture；项目未打开及预览状态下编辑命令禁用；打开可写页后无选区时剪切/复制禁用；反向选择 `Selected` 后复制菜单启用，剪贴板得到精确文本且草稿不变；剪切菜单移除该范围、剪贴板保持同一文本；全选后编辑器选择区为 `0..text.length`；保存重读后只第 1 页变更、第 2 页不变。
- [x] **Step 2: Verify RED on the sole API 34 tablet AVD**
  - 用显式 serial 和方法过滤器只执行该测试；预期失败于当前命令仍禁用。
- [x] **Step 3: Implement guarded native edit commands**
  - 剪切/复制仅在 `noteSelection` 非空时启用，全选仅在可写编辑器可用时启用；启用条件还要求 `!menuBusy`。
  - 为不可用编辑器和无选区的剪切/复制提供中英文明确禁用原因；剪切与复制使用各自的剪贴板标签。
  - handler 执行时重新读取并规范化 `selectionStart`/`selectionEnd`；复制写 `ClipData.newPlainText`，剪切随后删除所选 `Editable` 并收起光标，全选设置 `0..text.length`。
  - 保留原生 TextWatcher 驱动草稿；不直接设置 `noteDraft`，不自动保存。
- [x] **Step 4: Verify GREEN**
  - 重跑同一个 API 34 平板仪器用例，确认所有菜单、剪贴板、草稿和按页保存断言通过。
- [x] **Step 5: Verify focused JVM tests, build and diff**
  - 运行受影响 app JVM 测试与 `:app:assembleDebug`；运行 `git diff --check`。
  - 完成父任务 20 和本计划状态，更新 CHANGELOG 测试记录。
- [x] **Step 6: Commit and push**
  - 提交实现与测试、CHANGELOG、任务状态；推送至 `fork/codex/android-port`。
