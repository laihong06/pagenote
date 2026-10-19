# Android Markdown 项目图片插入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 可写页笔记中通过系统文档选择器插入本地项目图片。

**Architecture:** 用纯 Kotlin helper 生成安全 Markdown 引用及选区替换结果；用单一 `ProjectImageImporter` 将选择的 `content://` 图片暂存、解码验证并通过 SAF 写入项目 `src/assets/`。Reader 菜单捕获项目/页/草稿/选区上下文，只有同一上下文仍有效且 SAF 写入成功后才提交引用到原生编辑器。

**Tech Stack:** Kotlin、Jetpack Compose、Android `OpenDocument` Activity Result、SAF `DocumentsContract`、`BitmapFactory`、JUnit 4、Markwon 4.6.2。

**Spec:** `android/docs/spec/features/markdown-image-insertion.md`

## Global Constraints

- 当前目标仅为 Android 14/API 34 小米平板 5 Pro（骁龙 870）；API 34 AVD 只作为平台代理。
- 只接收桌面附件上传支持且 Android 现有 bitmap renderer 可解码的 PNG/JPG/JPEG/GIF/WebP，最大 200 MiB、2400 万像素。
- 写入 `src/assets/<uuid>.<ext>`，笔记只保存相对 `assets/` 引用；不保存来源 URI，不添加网络请求、依赖或项目格式字段。
- 失败不得写入笔记引用；删除部分 SAF 资源失败时向用户报告残留名。
- 仅运行受影响 JVM 测试、一个 API 34 AVD 聚焦仪器测试和 Debug APK 构建。
- 每次推送都更新根 `CHANGELOG.md`；分支为 `codex/android-port`，推送到个人 fork。

## Review Focus

- Picker 返回 null、空流、未知 MIME、错误/损坏图片或超文件/像素上限输入时不生成笔记引用或项目文件。
- SAF 资源创建后写入失败时清理新文档；清理失败必须保留并报告安全文件名。
- Project `assets` 目录缺失、只读或 Provider 拒绝创建时失败关闭，不改草稿。
- 页面、项目或草稿在 picker/异步复制期间变化时，不得把引用插入错误项目或页；未引用资源尽量清理。
- 选区替换必须只改捕获文本范围，保留周围 Markdown，并把光标置于插入引用后。

---

### Task 1: 图片引用与选区变换

**Files:**

- Create: `android/app/src/main/java/io/pagenote/android/MarkdownImageInsertion.kt`
- Test: `android/app/src/test/java/io/pagenote/android/MarkdownImageInsertionTest.kt`

**Interface:**

- `data class MarkdownImageInsertionEdit(replaceStart, replaceEnd, replacement, cursor)`。
- `MarkdownImageInsertion.createReference(altText, assetName): String`：只接受项目资产名 `[a-f0-9-]+\.(png|jpe?g|gif|webp)`，清理控制字符并转义 Markdown alt 文本。
- `MarkdownImageInsertion.createEdit(markdown, selectionStart, selectionEnd, reference): MarkdownImageInsertionEdit`：规范化/夹取 UTF-16 范围，替换该范围为 `\n<reference>\n`，返回替换文本末尾光标。

- [x] **Step 1: Write focused failing tests**
  - 四种图片扩展名均生成 `![safe alt](assets/<name>)`；拒绝路径穿越/非法资源名。
  - alt 文本中的换行、控制字符及 `[`/`]` 不得破坏 Markdown 链接结构。
  - 光标插入与非空选区替换都只改选中范围，并返回准确的新光标位置。
  - 反向/越界选区被规范化/夹取，emoji 等补充平面字符保持原样。
- [x] **Step 2: Verify RED**
  - Run: `cd android && ./gradlew :app:testDebugUnitTest --offline --tests io.pagenote.android.MarkdownImageInsertionTest`
  - Expected: compilation fails because the helper API does not yet exist.
- [x] **Step 3: Implement the minimal pure Kotlin helper**
  - Do not perform Android I/O or mutate the Markdown input.
- [x] **Step 4: Verify GREEN**
  - Run the same focused JVM command; all helper tests pass.

### Task 2: SAF 图片导入与 Reader 菜单闭环

**Files:**

- Create: `android/app/src/main/java/io/pagenote/android/ProjectImageImporter.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `android/app/src/androidTest/java/io/pagenote/android/ExistingProjectSafTestProvider.java`
- Test: `android/app/src/androidTest/java/io/pagenote/android/NoteMarkdownImageInsertionActivityTest.kt`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

- [x] **Step 1: Write the failing API 34 reader/SAF test**
  - Use the existing two-page SAF project fixture and inject results into the picker callback through the Activity test hook; do not launch system UI.
  - Assert the image menu command is disabled without an editable project, and enabled for the writable page editor.
  - In the same test method, cover cancelled picker (no change), injected partial SAF write failure (no Markdown reference and no undeleted partial asset), then a valid PNG result (asset under `src/assets`, relative reference inserted at captured selection, reference decodes in Markwon preview, saving/re-reading changes page 1 only).
- [x] **Step 2: Verify RED on the single API 34 tablet AVD**
  - Run only `NoteMarkdownImageInsertionActivityTest` with `ANDROID_SERIAL=emulator-5554` and an explicit method filter.
  - Expected: `Insert > Image` remains disabled/pending before implementation.
- [x] **Step 3: Implement `ProjectImageImporter`**
  - Read the picker URI through `ContentResolver` into a private cache temporary file, enforce the 200 MiB and 24 MP caps, validate supported MIME/extension and actual bitmap decoding before project mutation.
  - Find or create `src/assets`, create a UUID-named SAF document without overwrite, stream the staged bytes, and return the filename/reference metadata only after the stream closes successfully.
  - On failure after document creation, delete that document; if deletion fails, throw a safe import exception carrying only its generated asset name. Always delete the private temp file.
- [x] **Step 4: Wire the guarded Reader action**
  - Register `ActivityResultContracts.OpenDocument`, launch with `image/*`, and capture project id/source URI, page, editor instance, Markdown snapshot, and selection before launch.
  - Revalidate the captured context before and after I/O. On changed context, do not insert; attempt to delete the just-imported asset.
  - Apply `MarkdownImageInsertion.createEdit` through the original editor `Editable`, allowing its watcher to update the draft. Do not save notes from the import action.
  - Include import work in project/page action locks, use localized failure/status text, and replace only the pending `image` command.
- [x] **Step 5: Verify the focused API 34 reader flow**
  - Repeat only the filtered test. Verify the picker cancellation, rollback, selected image render, save/re-read and neighboring page preservation assertions.
- [x] **Step 6: Verify focused JVM tests and compile Debug APK**
  - Run: `cd android && ./gradlew :app:testDebugUnitTest --offline --tests io.pagenote.android.MarkdownImageInsertionTest :app:assembleDebug --offline`
  - Expected: focused tests pass and Gradle reports `BUILD SUCCESSFUL`.
- [x] **Step 7: Update task/log, check and push**
  - Mark task 5.8 complete and record exactly one API 34 AVD integration run plus focused JVM/build results; Xiaomi Pad 5 Pro hardware acceptance remains task 12.
  - Run `git diff --check` and `npx prettier --check android/docs/spec/tasks.md android/docs/spec/markdown-image-insertion-plan.md`.
  - Commit implementation with `CHANGELOG.md` and push to `fork/codex/android-port`.
