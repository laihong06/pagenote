# Android Markdown Clipboard Image Paste Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Paste a supported clipboard image into the current writable Android Markdown note as a local project asset and relative image reference.

**Architecture:** A pure Kotlin clipboard policy helper selects the first supported content URI and derives a safe fallback filename from MIME. `MainActivity` captures the current note insertion context and delegates to the existing `ProjectImageImporter`; the editor changes only after import succeeds and the captured context still matches.

**Tech Stack:** Kotlin, Android `ClipboardManager`/`ClipData`, SAF `DocumentsContract`, `ContentResolver`, `BitmapFactory`, JUnit 4, Jetpack Compose instrumentation.

**Spec:** `android/docs/spec/features/markdown-clipboard-image-paste.md`

## Global Constraints

- Target only Android 14/API 34 Xiaomi Pad 5 Pro (Snapdragon 870); API 34 AVD is a proxy, not hardware acceptance.
- Accept PNG/JPG/JPEG/GIF/WebP only; enforce the existing 200 MiB and 24-megapixel limits.
- Read clipboard data only on explicit command activation; persist no clipboard URI and add no network access or project-format fields.
- Do not change the note before a complete successful SAF import; clean up partial or stale-context assets where possible.
- Run only the focused JVM test, one API 34 AVD instrumentation method, and `:app:assembleDebug`.
- Update root `CHANGELOG.md` for every push; use `codex/android-port` and `fork`.

## Review Focus

- Clipboard has only text/HTML, an unsupported image type, a `file://` URI, or no URI: command reports no supported image and leaves draft/assets unchanged.
- Clipboard has an opaque `content:` URI or missing authority: skip it without querying its MIME type.
- Clipboard URI exposes MIME but no display name: importer uses a MIME-derived safe filename without weakening bitmap/MIME validation.
- Clipboard/provider access is revoked or SAF writes fail: no Markdown reference is inserted; partial assets are deleted or reported by generated name.
- Editor/project/page/draft changes while the URI is copied: insertion is rejected and the new asset is removed when possible.
- Clipboard is never inspected merely because the reader recomposes or opens.

---

### Task 1: Clipboard Image Selection Policy

**Files:**

- Create: `android/app/src/main/java/io/pagenote/android/ClipboardImageSelection.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/ProjectImageImporter.kt`
- Test: `android/app/src/test/java/io/pagenote/android/ClipboardImageSelectionTest.kt`

**Interface:**

- `data class ClipboardImageCandidate(val uri: String?, val mimeType: String?)`.
- `ProjectImageImporter.extensionForMimeType(mimeType): String?` exposes the existing normalized MIME map as the single support source.
- `ClipboardImageSelection.isContentUri(uri): Boolean` accepts only hierarchical `content://` URIs with a nonempty authority; use it before any resolver query.
- `ClipboardImageSelection.firstSupported(candidates): ClipboardImageCandidate?` selects the first content URI whose normalized MIME maps through `ProjectImageImporter.extensionForMimeType`.
- `ClipboardImageSelection.fallbackDisplayName(mimeType): String?` returns `clipboard-image.<validated extension>` or null for unsupported MIME.

- [x] **Step 1: Write focused failing JVM tests**
  - Verify first supported candidate wins, MIME parameters/case normalize, and unsupported/file/no-URI items are skipped.
  - Verify `image/jpeg` and `image/jpg` use `.jpg`, and unsupported MIME yields null.
- [x] **Step 2: Verify RED**
  - Run: `cd android && ./gradlew --offline :app:testDebugUnitTest --tests io.pagenote.android.ClipboardImageSelectionTest`
  - Expected: compilation fails because the helper does not exist.
- [x] **Step 3: Implement the minimal pure Kotlin helper**
  - Keep URI filtering deterministic; reuse the importer's MIME map for support checks and fallback names. Do not perform clipboard, resolver, or SAF I/O in this helper.
- [x] **Step 4: Verify GREEN**
  - Repeat the focused JVM command; all selection-policy tests pass.

### Task 2: MainActivity Clipboard-to-Asset Flow

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/ProjectImageImporter.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `android/app/src/androidTest/java/io/pagenote/android/NoteAssetSafTestProvider.java`
- Test: `android/app/src/androidTest/java/io/pagenote/android/NoteMarkdownImageInsertionActivityTest.kt`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

**Consumes:** `ClipboardImageSelection.firstSupported` and `fallbackDisplayName` from Task 1; existing `PendingMarkdownImageInsertion`, `ProjectImageImporter`, and `MarkdownImageInsertion` contracts.

**Produces:** `ProjectImageImporter.import(sourceUri, fallbackDisplayName, altTextOverride)` applies MIME-derived fallback metadata only for clipboard insertion; the existing picker path continues to call `import(sourceUri)`.

- [x] **Step 1: Write one failing API 34 reader/SAF integration test**
  - Install the existing two-page SAF fixture and open the writable note editor.
  - Put text-only content in the real system clipboard; tapping `Paste image` must leave draft/assets unchanged and show the localized no-image status.
  - Put a supported image `content://` URI without a provider display name in clipboard `ClipData`; tapping the command must import with the MIME-derived filename, insert at the selected text, render offline, save, and reopen page 1 while page 2 remains unchanged.
- [x] **Step 2: Verify RED on the single API 34 tablet AVD**
  - Use `ANDROID_SERIAL=emulator-5554` and an explicit test-method filter.
  - Expected: the test cannot find or use the new `Paste image` action before implementation.
- [x] **Step 3: Implement guarded clipboard handling**
  - Read `ClipboardManager.primaryClip` only inside the action handler; reject non-hierarchical/no-authority URIs before resolver access and stop lazily at the first supported item.
  - Reuse the existing captured editor context and image import completion path; use a validated MIME-derived fallback name only when the source display name is missing, and a localized generic alt label for clipboard content.
  - Localize the command and no-supported-image status in English and Simplified Chinese; keep the note draft unsaved until the existing Save action.
- [x] **Step 4: Verify the focused API 34 flow**
  - Repeat only the filtered instrumentation method; expected: no-image no-op and successful local import/preview/save/reopen all pass.
- [x] **Step 5: Verify focused JVM tests and compile**
  - Run the helper JVM test and `:app:assembleDebug` offline.
  - Expected: all focused tests pass and Gradle reports `BUILD SUCCESSFUL`.
- [x] **Step 6: Update tracking, check, review, commit, and push**
  - Mark task 5.13 complete with the single API 34 proxy flow; retain Xiaomi hardware validation in task 12.
  - Run `git diff --check` and Prettier on the new SPEC, plan, and task list.
  - Run one independent code review; resolve Critical/Important findings, record deferred minors.
  - Commit the feature with the behavior entry in `CHANGELOG.md`; push to `fork/codex/android-port`.
