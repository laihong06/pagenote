# Android New Project with Optional PDF Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Android users create a v1 pagenote project with an optional copied PDF source, including blank-page PDFs.

**Architecture:** Extend the existing Compose creation dialog with a second SAF picker while preserving the current folder/name state. `ProjectCreator` copies and validates a selected PDF under the new project `src/`, writes the matching v1 manifest last, and rolls back created documents on failure; the existing project-open flow remains the single path to open the result.

**Tech Stack:** Kotlin, Jetpack Compose, Android SAF `OpenDocument`/`OpenDocumentTree`, `DocumentsContract`, Android `PdfRenderer`, JUnit 4, Compose instrumentation.

**Spec:** `android/docs/spec/features/project-create-optional-pdf.md`

## Global Constraints

- Target Xiaomi Pad 5 Pro (Snapdragon 870), Android 14/API 34 only; one API 34 tablet AVD is a proxy, not hardware acceptance.
- Preserve `.pagenote` project format version 1 and all existing Markdown-only creation behavior.
- Copy PDF bytes into project-owned `src/document.pdf`; never persist the selected external URI in the manifest.
- Use the existing SAF tree access model and create/open workflow; do not introduce filesystem-path assumptions.
- Run focused tests, one API 34 AVD flow, and `:app:assembleDebug`; do not expand device/API matrices.
- Update root `CHANGELOG.md` on every push; continue on `codex/android-port` and push to `fork`.

## Review Focus

- A blank but valid one-page PDF must not be mistaken for an empty/invalid source; test it with `PdfRenderer.pageCount == 1`.
- Canceling the PDF picker while replacing a selection must retain the prior selection and directory; test state in the Compose dialog/Activity flow.
- Provider names must never become filesystem paths; use the fixed project-relative `src/document.pdf` and preserve display name only as manifest metadata.
- A failed copy/PDF validation must remove every created child in reverse order or surface the residual project name; assert no partial project remains in the SAF fixture.
- PDF and Markdown projects must keep their own manifest `left` path while retaining `right.md` and `assets/`; assert both shapes and direct-open source type.

---

### Task 1: Picker and Dialog State

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/NewProjectDialogPolicy.kt`
- Create: `android/app/src/main/java/io/pagenote/android/NewProjectManifestPolicy.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Test: `android/app/src/test/java/io/pagenote/android/NewProjectDialogPolicyTest.kt`
- Test: `android/app/src/test/java/io/pagenote/android/NewProjectManifestPolicyTest.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/NewProjectDialogTest.kt`

**Interface:**

- Extend `NewProjectDialogPolicy.canDismiss(isCreating: Boolean, isChoosingDirectory: Boolean, isChoosingPdf: Boolean): Boolean` so neither picker nor creation can dismiss the dialog.
- Extend `NewProjectDialog` with `pdfDisplayName: String?`, `onChoosePdf`, and `onClearPdf`; preserve existing directory selection API.
- Add `NewProjectManifestPolicy.create(name: String, pdfDisplayName: String? = null): ProjectManifest`; no PDF retains `src/left.md`, while a selected PDF sets `src/document.pdf` and `leftName`.

- [x] Write failing policy/Compose assertions for the optional PDF button, selected filename/clear state, and dismissal blocking while either picker is active.
- [x] Write failing `NewProjectManifestPolicyTest` assertions for Markdown defaults and PDF source/metadata mapping.
- [x] Run the focused policy/manifest JVM tests and `:app:compileDebugAndroidTestKotlin`; confirm the new assertions fail before implementation.
- [x] Add the localized optional-PDF control, saved URI/selection state, read-only persistable URI grant lifecycle, and an `OpenDocument` launcher restricted to `application/pdf`.
- [x] Run the focused JVM tests and instrumentation compilation; verify cancel/reselect preserves project name and folder. API 34 AVD: 3 Compose tests passed; focused JVM tests passed.

### Task 2: Project Creator PDF Import

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/ProjectCreator.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/ProjectCreateOptionalPdfActivityTest.kt`
- Test fixture: `android/app/src/androidTest/java/io/pagenote/android/ProjectCreateSafTestProvider.java`
- Modify: `android/app/src/androidTest/AndroidManifest.xml`

**Interface:**

- Extend `ProjectCreator.create(parentTreeUri: Uri, name: String, sourcePdfUri: Uri? = null, sourceDisplayName: String? = null): ProjectOpenTarget`.
- PDF mode writes `src/document.pdf`, `src/right.md`, `src/assets/`, then a v1 manifest whose `left` is `src/document.pdf` and `leftName` is the external display name. Markdown mode keeps the current structure.

- [x] Add one API 34 end-to-end activity test that supplies a generated valid blank one-page PDF and asserts copied bytes, manifest links, page count, and direct-open state; recreate the Activity with the selection pending, include the no-PDF Markdown branch and malformed-PDF cleanup assertion.
- [x] Run the focused activity test on the API 34 tablet AVD; correct the SAF test fixture and test-only input interaction based on the observed failures.
- [x] Implement SAF copy, source and copied-file `PdfRenderer` validation (`pageCount > 0`), manifest encoding, and reverse-order cleanup while retaining existing same-name protection.
- [x] Re-run the API 34 activity test; assert the imported PDF is project-local and the original source URI is not written to the manifest. All 3 activity tests passed.

### Task 3: Final Verification and Tracking

**Files:**

- Modify: `android/docs/spec/tasks.md`
- Modify: `android/docs/spec/project-create-optional-pdf-plan.md`
- Modify: `CHANGELOG.md`

- [x] Run focused `NewProjectDialogPolicyTest`, `NewProjectManifestPolicyTest`, the new-project dialog and activity tests on the API 34 AVD, and `:app:assembleDebug`.
- [x] Run `git diff --check`; final device verification used only the API 34 AVD and no unrelated test suites.
- [x] Record exact results, mark task 19 complete, and keep hardware acceptance in task 12.
- [x] Commit the feature with its changelog entry and push to `fork/codex/android-port`.

Verification note: an early connected-test invocation saw a concurrently running API 35/16 KB AVD and ran the 3 dialog tests there before the target mismatch was noticed. That AVD was shut down; all final focused instrumentation tests ran only on API 34. No other app was opened.
