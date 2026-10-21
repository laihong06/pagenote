# Android Note Import Implementation Plan

> **For agentic workers:** Follow the repository's SPEC workflow and test-first implementation rules. Keep each task small and check it off as completed.

**Goal:** Connect Android `File > Import notes` for desktop-compatible JSON backups and current-page Markdown/text insertion.

**Architecture:** A pure payload helper classifies/parses JSON note backups, filters valid page entries, merges them into the latest page-notes document, and computes cursor insertion for text. `MainActivity` owns the picker, overwrite confirmation, stale context check, revision-checked dirty save, one recoverable SAF write, and localized result state. Use the existing `ProjectNoteStore` mutation lock and recovery writer.

**Tech Stack:** Kotlin, kotlinx.serialization JSON, Android SAF `ActivityResultContracts.OpenDocument`, Jetpack Compose, existing project note APIs, JUnit 4, Compose instrumentation.

**Spec:** `android/docs/spec/features/note-import.md`

## Global Constraints

- Target only Xiaomi Pad 5 Pro (Snapdragon 870), Android 14/API 34; one API 34 tablet AVD is a proxy, not hardware acceptance.
- Match the desktop JSON v1 import behavior for accepted fields and page filtering; ignore metadata mismatch.
- Never touch source documents, manifests, annotations, pen strokes, or assets.
- Save dirty notes before importing; reject stale content and preserve unrelated pages.
- Run one focused JVM test class, one API 34 AVD activity test, affected app compilation, and `:app:assembleDebug`; do not run broad suites or device matrices.
- Update root `CHANGELOG.md` for each push; continue on `codex/android-port` and push to `fork`.

## Review Focus

- Empty-string page values clear only that page; absent/invalid pages remain unchanged.
- JSON application is one notes-document write guarded by the original content and existing SAF recovery behavior.
- Invalid JSON, cancellation, stale context, conflicts, and provider failure never claim success.
- Markdown/TXT inserts as literal UTF-8 text into the current draft and does not mutate the source file.

---

### Task 1: Import Payload Contract

**Files:**

- Create: `android/app/src/main/java/io/pagenote/android/NoteImportPayload.kt`
- Test: `android/app/src/test/java/io/pagenote/android/NoteImportPayloadTest.kt`

**Interface:**

- `NoteImportPayload.parseBackup(content, currentPageCount): Map<Int, String>`; throw a stable import-format exception if JSON is malformed or `notes` is not an object.
- `NoteImportPayload.merge(currentNotes, currentPageCount, importedPages): String`; apply valid entries while preserving other pages.
- `NoteImportPayload.insertText(markdown, selectionStart, selectionEnd, text): NoteTextInsertion` with replacement range and resulting cursor.
- `NoteImportPayload.isJson(displayName, mimeType): Boolean`.

- [x] Add focused tests for desktop-shaped JSON, metadata mismatch, ignored invalid/out-of-range/non-string entries, empty-value clearing, a missing notes object, merge preservation, and text insertion/selection replacement; run and observe the expected RED.
- [x] Implement the smallest pure parser/merge/insertion helper.
- [x] Run only `:app:testDebugUnitTest --tests io.pagenote.android.NoteImportPayloadTest` and confirm it passes.

### Task 2: Reader Picker and Apply Flow

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `android/app/src/androidTest/java/io/pagenote/android/NoteExportSafTestProvider.java` only if the import fixture needs readable source documents.
- Test: `android/app/src/androidTest/java/io/pagenote/android/NoteImportActivityTest.kt`

**Produces:** A working File-menu import flow for JSON backups and Markdown/plain text.

- [x] Add one API 34 activity test for the real menu handler, picker cancellation, JSON confirmation/apply/cancel, malformed JSON, Markdown insertion, stale context, and unchanged unlisted pages; confirm it fails before implementation.
- [x] Connect OpenDocument for JSON/Markdown/text; capture project/page context and read selected URI content on `Dispatchers.IO`.
- [x] Confirm JSON overwrite, revision-save dirty page, read latest notes under the mutation lock, merge, perform one expected-original recoverable SAF write, then refresh page revision/history.
- [x] Insert text at the active editor selection or switch preview to Edit and insert at page end; preserve text as literal Markdown.
- [x] Localize success, malformed input, stale context, and failure feedback.
- [x] Run only the focused `NoteImportActivityTest` on the single Android 14/API 34 tablet AVD; confirm the one-page flow and that the other page/project files are unchanged.

### Task 3: Build and Tracking

**Files:**

- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

- [x] Run the focused JVM test class (5/5), focused API 34 activity test (1/1), and `:app:assembleDebug` (success); no other API/device matrix.
- [x] Mark task 18 complete while keeping Xiaomi hardware acceptance in task 12.
- [x] Run `git diff --check`; review the diff for project non-mutation, revision protection, and desktop import compatibility.
- [x] Commit the feature with the matching changelog entry and push to `fork/codex/android-port`.
