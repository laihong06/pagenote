# Android Markdown 剪贴板粘贴实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从系统剪贴板把安全 HTML 或纯文本插入 Android 当前页 Markdown 笔记。Task 1 records the completed plain-text baseline; Task 2 extends it with rich HTML conversion.

**Architecture:** Reader 菜单复用现有可写 Markdown 编辑器门控；显式点击时读取第一项，优先用 Jsoup 在本地将 `text/html` 转换为 Markdown，必要时回退纯文本；用现有 `NoteImportPayload.insertText` 计算 UTF-16 选区替换范围，直接更新原生 `Editable`，继续由现有 watcher 维护草稿。

**Tech Stack:** Kotlin、Jetpack Compose、Android `ClipboardManager`/`EditText`、Jsoup、现有 `NoteImportPayload`、AndroidX instrumentation。

**Spec:** `android/docs/spec/features/markdown-clipboard-paste.md`

## Global Constraints

- 最低版本保持 Android 14/API 34；实体目标仍为 Xiaomi Pad 5 Pro / Snapdragon 870。
- 仅在用户显式点击后处理首个 clip item；不读取图片 URI、不执行 HTML、不获取网络资源，不改变项目格式。
- 仅运行受影响 app JVM 测试、一个 API 34 平板 AVD 聚焦仪器用例和 Debug 构建。
- 每次 push 更新根 `CHANGELOG.md`；只推 fork 的 `codex/android-port`，不发布二进制。

## Review Focus

- 显式点击前不得访问剪贴板；仪器测试点击命令前设置空剪贴板且草稿不变。
- 非空剪贴板文本必须原样替换选区；测试包含 Markdown 标记并检查精确草稿。
- 折叠光标时必须插入而非追加/替换，并将光标放在插入文本之后；测试再次粘贴并检查 selection。
- 空剪贴板不改草稿并展示本地化提示；聚焦测试断言状态和草稿。
- 保存仍按页；两页 fixture 保存后下一页内容不变。

---

### Task 1: 接通 Paste as Markdown

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/NoteMarkdownClipboardActivityTest.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

**Interface:** 复用 `NoteImportPayload.insertText(markdown, selectionStart, selectionEnd, text)` 和现有 `canFormatMarkdown`；不新增跨模块 API。

- [x] **Step 1: Write the failing reader test**
  - 新增 `pasteMarkdownUsesClipboardReplacesSelectionAndInsertsAtCaret`：两页 SAF fixture；无项目时命令禁用；可写编辑器下空剪贴板点击后提示且 draft 不变；反向选区 `replace me` 被 `**fresh**` 替换，光标移至插入末尾；再次粘贴 `!` 于折叠光标处插入；保存重读后只第一页变化，第二页不变。
- [x] **Step 2: Verify RED on the sole API 34 tablet AVD**
  - 显式指定 `emulator-5554` 和单方法过滤器；预期当前 `pendingCommand` 仍禁用时失败。
- [x] **Step 3: Implement the guarded paste handler**
  - 只有命令被显式点击时读 `primaryClip` 第一项 `text`；空值/空串设置本地化状态并返回。
  - 用 `NoteImportPayload.insertText` 计算插入范围，聚焦编辑器后更新 `Editable` 并设置返回的折叠光标；不直接写 draft、不自动保存。
  - 只替换 `pendingCommand("paste_markdown", ...)`，复用可写编辑器 gate 和相应 disabled reason。
- [x] **Step 4: Verify GREEN**
  - 重跑相同 API 34 AVD 单项仪器用例，确认提示、选区替换、光标插入和按页保存闭环。
- [x] **Step 5: Verify JVM tests, build and diff**
  - 运行 `:app:testDebugUnitTest`, `:app:assembleDebug` 和 `git diff --check`；更新父 task 21 与 CHANGELOG。
- [x] **Step 6: Commit and push**
  - 提交实现与测试、CHANGELOG、任务状态；推送至 `fork/codex/android-port`。

### Task 2: 安全富 HTML 转 Markdown

**Files:**

- Add: `android/app/src/main/java/io/pagenote/android/HtmlToMarkdown.kt`
- Test: `android/app/src/test/java/io/pagenote/android/HtmlToMarkdownTest.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/NoteMarkdownClipboardActivityTest.kt`
- Modify: `android/app/build.gradle.kts`

**Interface:** `HtmlToMarkdown.convert(html): String?` 只返回本地解析结果，不访问网络；剪贴板处理器先转换 HTML，再回退当前 `ClipData.Item.text`，最后仍调用 `NoteImportPayload.insertText`。

- [ ] **Step 1: Write failing converter and reader tests**：覆盖常见语义标签、嵌套列表/表格、畸形 HTML、安全协议、script/style/事件属性、图片 alt-only 与 HTML 无输出回退；仪器用例构造同时包含 HTML/plain text 的 `ClipData` 并断言 HTML 优先、精确插入与按页保存。
- [ ] **Step 2: Verify RED**：运行指定 API 34 平板 AVD 的单项转换/Activity 测试，确认新增断言因纯文本基线不支持 HTML 而失败。
- [ ] **Step 3: Implement local conversion**：使用固定白名单和递归 DOM 转 Markdown；不启用加载器或网络 API；HTML 无有效正文时回退纯文本。
- [ ] **Step 4: Verify GREEN**：重跑同一 AVD 测试，覆盖选择替换、插入点、草稿撤销和保存后相邻页不变。
- [ ] **Step 5: Verify build and diff**：运行聚焦 JVM 测试、`:app:compileDebugAndroidTestKotlin`、`:app:assembleDebug` 与 `git diff --check`；更新 task 46 和 CHANGELOG。
- [ ] **Step 6: Commit and push**：独立提交此功能切片及 CHANGELOG，推送当前 fork feature branch。
