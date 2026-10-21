# Android Markdown 下划线格式实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 接通 Android 可写 Markdown 页笔记的桌面一致 Underline 命令。

**Architecture:** 扩展现有 `MarkdownInlineStyle` 为 `<u>` source wrapper；在 Markwon 4.6.2 中加入 `HtmlPlugin`，关闭全部默认 tag handlers，仅注册其 `UnderlineHandler`。Markwon editor 默认只标记 Markdown 标点，因此额外注册只处理 `UnderlineSpan` 的 `EditHandler`，把 renderer 输出映射回可编辑源文本；MainActivity 继续通过当前 EditText 变更并让原 watcher 更新草稿。

**Tech Stack:** Kotlin、Jetpack Compose、Android `EditText`、Markwon 4.6.2 HTML module、JUnit 4、Compose UI instrumentation。

**Spec:** `android/docs/spec/features/markdown-underline-formatting.md`

## Global Constraints

- Android 最低版本 API 34，唯一代理 AVD 为 `emulator-5554` Android 14 平板；它不代表小米实机验收。
- 桌面源文语法固定为 `<u>...</u>`；项目格式与 revision 保存契约不变。
- HTML 插件必须使用 `excludeDefaults(true)`，只注册 `UnderlineHandler`；不得启用 WebView 或远程内容处理。
- 每次推送更新根 `CHANGELOG.md`；分支 `codex/android-port` 推送至 `fork` 同名分支。
- 仅运行受影响 helper 测试、完整 app JVM 测试、一个 API 34 仪器流程及 `:app:assembleDebug`。

## Review Focus

- 嵌套已有 Markdown marker 时保持源文本正确；验证 `<u>` span 实际存在。
- 空选区插入并选中现有本地化占位词；反向或补充 Unicode 字符前选区仍按 UTF-16。
- HTML 集成仅增加 Underline handler，且不启用默认图片/链接/其他 tag handlers。
- 编辑命令只更新当前页 draft；保存仍 revision-checked 且邻页不变。
- 预览/只读/无项目/忙碌状态沿用现有 Format 命令门控。

---

### Task 1: SPEC 与计划

**Files:**

- Create: `android/docs/spec/features/markdown-underline-formatting.md`
- Create: `android/docs/spec/markdown-underline-formatting-plan.md`
- Modify: `android/docs/spec/features/markdown-inline-formatting.md`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

- [x] 明确 `<u>` desktop source semantics、唯一 HTML handler、安全边界与页级保存行为。
- [x] 先提交并推送仅文档变更，代码实现仍未开始。

### Task 2: 行内变换

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MarkdownInlineFormatting.kt`
- Test: `android/app/src/test/java/io/pagenote/android/MarkdownInlineFormattingTest.kt`

**Interface:**

- 新增 `MarkdownInlineStyle.UNDERLINE("<u>", "</u>")`，复用 `wrapSelection`。

- [x] **Step 1: Write failing tests**：验证选择文本产生 `<u>selected</u>`、折叠光标产生并选中 `<u>文字</u>`。
- [x] **Step 2: Verify RED**：运行 `./gradlew :app:testDebugUnitTest --tests io.pagenote.android.MarkdownInlineFormattingTest`，确认缺少 `UNDERLINE` 时编译失败。
- [x] **Step 3: Implement the minimal style**：只增加 enum 样式值，不改范围算法。
- [x] **Step 4: Verify GREEN**：重复聚焦 JVM 命令，原五项及新增下划线用例全部通过。

### Task 3: HTML preview 与菜单闭环

**Files:**

- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/java/io/pagenote/android/NoteMarkdownRenderer.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/NoteMarkdownFormatActivityTest.kt`
- Modify: `android/docs/spec/tasks.md`
- Modify: `android/docs/spec/markdown-underline-formatting-plan.md`
- Modify: `CHANGELOG.md`

- [x] **Step 1: Add the failing Reader flow**：测试无项目/预览时禁用；可写编辑器中选择文本插入 `<u>`，折叠光标插入占位并保持选中，保存当前页后确认相邻页不变，编辑器与预览均检查 `UnderlineSpan`。
- [x] **Step 2: Verify RED on API 34 AVD**：pending Underline 初始导致 enabled 断言失败；再移除 HTML renderer 验证时，编辑器 span 断言失败，证实需要 EditHandler 映射。
- [x] **Step 3: Wire the restricted renderer and command**：新增 `io.noties.markwon:html:4.6.2`；renderer 安装 `HtmlPlugin` 时调用 `excludeDefaults(true)` 并只添加 `UnderlineHandler`；editor 只注册 `UnderlineEditHandler`；替换 Underline pending command并复用现有 `applyMarkdownInlineFormatting`。
- [x] **Step 4: Verify the focused API 34 flow passes**：确认嵌套粗体源文、编辑器与预览原生 span、选区、占位、页级保存和相邻页隔离均符合 SPEC。
- [x] **Step 5: Run app JVM tests and compile**：完整 app JVM 共 256 项通过；单项 API 34 测试 1/1 通过；`:app:assembleDebug` 与 `git diff --check` 通过。
- [x] **Step 6: Update SPEC and CHANGELOG, commit and push**：更新了 task 25 与本计划；实现提交并推送 `fork/codex/android-port`。
