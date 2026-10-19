# Android Note Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable Android `File > Export notes` to save a desktop-compatible full JSON backup or the current page as Markdown.

**Architecture:** A pure Kotlin payload builder maps the project Markdown note into the existing desktop v1 JSON shape and produces the full current-page Markdown output. `MainActivity` handles the format choice, revision-checked draft save, Android `CreateDocument` launchers, and SAF output write; exports never write into the project.

**Tech Stack:** Kotlin, kotlinx.serialization JSON, Android SAF `ActivityResultContracts.CreateDocument`, ContentResolver, JUnit 4, Jetpack Compose instrumentation.

**Spec:** `android/docs/spec/features/note-export.md`

## Global Constraints

- Target only Xiaomi Pad 5 Pro (Snapdragon 870), Android 14/API 34; API 34 AVD is a proxy, not hardware acceptance.
- Preserve the desktop JSON v1 field names and meanings; do not add fields or include non-note project data.
- Save editable dirty notes before export; include a recovered dirty draft in a read-only export without modifying the project.
- Keep both outputs user-selected via SAF and keep export I/O out of project source files.
- Run one focused JVM test class, one API 34 AVD activity test, and `:app:assembleDebug`; no broader device matrix.
- Update root `CHANGELOG.md` for every push; use `codex/android-port` and `fork`.

## Review Focus

- A note save conflict or SAF write failure must stop/report failure, never claim a successful export.
- Blank page notes between separators and non-ASCII Markdown must survive JSON mapping unchanged.
- The page Markdown output must use the complete page draft, not the preview-limited text.
- Canceling either picker must not write or alter project data.
- Read-only projects remain exportable, including a recovered local draft without project mutation.

---

### Task 1: Export Payload Contract

**Files:**

- Create: `android/app/src/main/java/io/pagenote/android/NoteExportPayload.kt`
- Test: `android/app/src/test/java/io/pagenote/android/NoteExportPayloadTest.kt`

**Interface:**

- `NoteExportPayload.fullBackup(projectName, sourcePageCount, notesMarkdown, currentPage, currentDraft): NoteExportFile`
- `NoteExportPayload.currentPage(projectName, page, markdown): NoteExportFile`
- `NoteExportFile` contains `mimeType`, `fileName`, and complete UTF-8 `content`.
- The JSON notes object maps every page represented by `PageNotes.split(notesMarkdown)` to a one-based string key. If a current-page override is supplied, apply it before building the JSON object.

- [x] Write focused tests for the desktop v1 fields, blank intermediate pages, non-ASCII content, current-page override, filename/MIME, and full current-page Markdown; run the class and observe the expected missing-type RED.
- [x] Implement the smallest pure payload builder using the existing `PageNotes.split` contract and kotlinx.serialization JSON.
- [x] Run the focused JVM test class and confirm all payload assertions pass.

### Task 2: Reader Export and SAF Flow

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/ReaderSessionViewModel.kt` only if a captured pending export must survive recreation.
- Create: `android/app/src/main/java/io/pagenote/android/NoteExportSnapshotStore.kt` for atomic app-private pending-payload recovery.
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `android/app/src/androidTest/AndroidManifest.xml` and the existing SAF test provider only if needed for an output-document fixture.
- Test: `android/app/src/androidTest/java/io/pagenote/android/NoteExportActivityTest.kt`

**Consumes:** `NoteExportPayload.fullBackup`, `NoteExportPayload.currentPage`, the existing `saveCurrentNote()` revision guard, and `OpenedProject`.

**Produces:** A working File-menu `Export notes` action with two SAF-backed output formats and localized result statuses.

- [x] Write one focused API 34 activity test covering both output formats, immediate dirty-note export, output MIME/name, and unchanged neighboring page data; verify it fails before the menu action is connected.
- [x] Connect the menu action to format choices and the matching `CreateDocument` contracts. Keep the selected export payload stable while the picker is open.
- [x] Save a dirty writable page before snapshot creation; abort on conflict/failure. For a read-only recovered draft, override only that page in the export snapshot.
- [x] Write selected output to the returned SAF URI on `Dispatchers.IO`; cancellation is a no-op and write failures report failure.
- [x] Run the focused activity test on one Android 14/API 34 tablet AVD and confirm both files contain the expected data, Activity recreation and fresh-ViewModel snapshot recovery, recovery-state marking, read-only draft, save conflict, write failure, and cancellation behavior. This does not simulate an OS process kill.
- [x] Provide retry-to-new-destination and discard actions for an interrupted process-restored export so a consumed picker result cannot leave the reader permanently blocked.

### Task 3: Build and Tracking

**Files:**

- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

- [x] Run `./gradlew --offline :app:testDebugUnitTest --tests io.pagenote.android.NoteExportPayloadTest :app:assembleDebug` and confirm focused tests pass and Gradle reports `BUILD SUCCESSFUL`.
- [x] Mark the export task complete with actual evidence; keep Xiaomi hardware acceptance in task 12.
- [x] Run `git diff --check`; review the final diff for exact schema parity and project non-mutation.
- [x] Commit the feature and matching changelog entry, then push to `fork/codex/android-port`.
