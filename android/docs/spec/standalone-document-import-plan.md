# Android Standalone PDF/Markdown Import Plan

> **For agentic workers:** Use the documented Android SPEC and execute this plan task by task. Steps use checkbox syntax for tracking.

**Goal:** Add a native Android `Open document` flow for standalone PDF/Markdown files that copies the source into a standard pagenote project and opens it without changing existing project data.

**Architecture:** Reuse the existing SAF `New project` dialog, `ProjectCreator`, and `ProjectOpenTarget`. Generalize the optional source from PDF-only to a validated PDF/Markdown document, then connect one shared document picker to both `Open document` and `New project`.

**Tech Stack:** Kotlin, Jetpack Compose, Android Storage Access Framework, `PdfRenderer`, kotlinx.serialization, Gradle/JUnit, API 34 tablet AVD.

**Spec:** `android/docs/spec/features/standalone-document-import.md`, plus §45 of `android/docs/spec/features/desktop-semantic-parity.md`.

## Global Constraints

- Android minimum and sole routine API target: Android 14 / API 34.
- Keep project metadata in the v1 `project.pagenote` manifest; do not store external SAF URIs in the project.
- Preserve selected source bytes exactly for Markdown; validate PDFs before and after project copy.
- Preserve existing no-source project creation and optional blank-PDF import behavior.
- Keep all Android SPEC and plan documents under `android/docs/spec/`.
- Each code push requires a matching root `CHANGELOG.md` Unreleased entry; source changes push to the existing Android feature branch targeting `beta`, not `main`.
- Do not publish a binary release as part of this feature slice.

## Review Focus

- Activity recreation or picker cancellation while holding a persistable read grant: verify the source URI, name, directory, and grant remain usable, then release them exactly once on clear/dismiss/success.
- Filename and provider MIME disagreement: accept only `.pdf`, `.md`, `.markdown` case-insensitively; do not import arbitrary `text/plain` files.
- PDF validation failures (empty, malformed, unreadable, zero pages): verify no final manifest/project remains and the dialog keeps retryable values.
- Duplicate project name or provider failure during copy: preserve the existing project and delete newly created children in reverse order.
- Markdown byte fidelity (CRLF, BOM, empty content): compare copied bytes, not decoded strings.

---

### Task 1: Generalize the project source contract

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/ProjectCreator.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/NewProjectManifestPolicy.kt`
- Test: `android/app/src/test/java/io/pagenote/android/NewProjectManifestPolicyTest.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/ProjectCreateOptionalPdfActivityTest.kt`

**Interfaces:**
- `ProjectCreator.create(parentTreeUri, name, sourceDocumentUri?, sourceDisplayName?)` accepts only supported source extensions.
- `NewProjectManifestPolicy.sourceExtension(displayName)` returns lowercase `pdf`, `md`, or `markdown`, or `null` for unsupported names.
- `NewProjectManifestPolicy.suggestedProjectName(displayName)` removes the final extension, replaces `[<>:"/\\|?*]` with `_`, trims, and falls back to `Imported document`.
- `NewProjectManifestPolicy.create(name, sourceDisplayName?)` returns the existing empty Markdown manifest without a source, or a project-relative `src/document.<extension>` link with the picked display name.

- [x] **Step 1: Add failing JVM tests** named `supportsAndNormalizesPdfAndMarkdownExtensions`, `suggestsSafeProjectNameFromSourceDisplayName`, `createsMarkdownManifestWithOriginalSourceName`, and `rejectsUnsupportedSourceExtension`.
- [x] **Step 2: Run the focused manifest tests** and confirm they fail because the source-type policy does not yet exist.
- [x] **Step 3: Add the minimal source policy and extend `ProjectCreator`** to copy Markdown bytes unchanged, stage/validate PDF as before, create `right.md` and `assets/`, write the manifest last, and retain reverse-order rollback.
- [x] **Step 4: Add SAF fixture tests** for Markdown byte-for-byte copy, empty Markdown, PDF copy/validation, duplicate names, unsupported extension, and rollback both before staging and after copied-PDF validation.
- [x] **Step 5: Run the focused JVM and project-creator Android tests** on API 34 and verify existing empty-project/PDF behavior remains intact.

### Task 2: Add the standalone Open Document flow

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `android/app/src/test/java/io/pagenote/android/DesktopCommandChecklistTest.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/ProjectCreateOptionalPdfActivityTest.kt` or a focused new `StandaloneDocumentImportActivityTest.kt`
- Modify: `android/docs/spec/features/desktop-command-checklist.md`

**Interfaces:**
- Shared source selection state feeds the existing `New project` dialog from both `open_document` and its optional source button.
- `open_document` is added to Android File menu/welcome actions and uses SAF `OpenDocument`; accepted file selection pre-fills the source and editable safe project name.

- [x] **Step 1: Add the UI/command test** named `openDocumentSelectionPrepopulatesProjectCreationWithoutReplacingCurrentReader` for the `open_document` command, selected-file handoff, and cancellation preserving the active project.
- [x] **Step 2: Run the focused test** and verify the end-to-end selector, safe-name handoff, unsupported-file recovery, cancellation, and create/open behavior.
- [x] **Step 3: Wire one shared PDF/Markdown picker** with persisted read-grant handling, case-insensitive extension validation, recoverable unsupported-file status, source clear/replace behavior, and existing dialog-state restoration.
- [x] **Step 4: Verify API 34 Activity flows** for file selection, source-name suggestion, destination selection, cancel, replace, create/open, and active-project preservation on failure; prove persisted source grants survive Activity recreation and are released on clear, dismiss, and success.

### Task 3: Verify, record, commit, and sync

**Files:**
- Modify: `android/docs/spec/tasks.md`
- Modify: `android/docs/spec/features/desktop-semantic-parity.md`
- Modify: `CHANGELOG.md`

- [x] **Step 1: Run focused JVM suites** for source policy and project validation.
- [x] **Step 2: Run focused API 34 AVD tests** for source selection/creation and compile all changed Android test sources.
- [x] **Step 3: Build `:app:assembleDebug`** and run `git diff --check`.
- [x] **Step 4: Update task 45 and semantic parity status** only to reflect verified behavior; retain all remaining gaps as open.
- [x] **Step 5: Add the user-visible behavior and actual verification to the root Unreleased changelog.**
- [x] **Step 6: Commit this feature slice and push `codex/android-port-upstream` to `fork`; confirm the beta PR head matches the pushed commit.**

Verification result: focused policy/command JVM tests pass; affected AndroidTest sources compile; API 34 tablet AVD ProjectCreateOptionalPdfActivityTest passes 10/10 and NewProjectDialogTest passes 3/3; `:app:assembleDebug` succeeds. The SAF fixture exercises actual persistable grants and the post-copy rollback path, but does not prove Xiaomi hardware or a real system picker UI.
