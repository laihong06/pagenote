# Pen Store Late Annotations Resolution Implementation Plan

> **For agentic workers:** Execute this plan in the current `codex/android-port` workspace. Keep the change Android-only and use the existing API 34 tablet AVD.

**Goal:** Ensure a `ProjectPenStore` opened before `src/annotations.json` exists discovers and reuses that file if another reader feature creates it later.

**Architecture:** Resolve the canonical annotations document through the source-directory child query at each public store operation when the constructor URI is absent, then cache the discovered URI. Preserve the existing pen-specific merge and recoverable SAF writer; do not alter the desktop JSON schema.

**Tech Stack:** Kotlin, Android SAF `DocumentsContract`/`ContentResolver`, existing API 34 instrumentation provider, Gradle.

**Spec:** `android/docs/spec/features/pen-late-annotations-resolution.md`

## Global Constraints

- Minimum and target test environment for this slice: Android 14 / API 34.
- Use only the existing tablet AVD; do not add device, API, or performance matrices.
- Operate only the app, its test `DocumentsProvider`, and the system DocumentsUI folder picker used by its SAF flow; do not launch unrelated apps.
- Keep the regression and production changes inside the Android project.
- Update the root `CHANGELOG.md` before pushing; this is not a public binary release.

## Review Focus

- A canonical annotations file created after reader startup must be found before `createDocument` is called; assert one child document and both annotation types.
- An annotations-free project with no pen strokes must remain file-free; assert the source directory children.
- A failing source-directory query must not be mistaken for an absent file; assert the provider's create count remains zero.
- Existing PDF annotation items must survive the pen merge; assert the complete original item after a fresh store read.
- The existing SAF recovery writer must still handle partial writes; retain and run its focused regression class.

## Tasks

### Task 1: Reproduce late annotations discovery through SAF

**Files:**
- Modify: `android/app/src/androidTest/java/io/pagenote/android/ExistingProjectSafTestProvider.java`
- Create: `android/app/src/androidTest/java/io/pagenote/android/PenAnnotationsUriResolutionTest.kt`
- Modify: `android/docs/spec/tasks.md`

**Interfaces:**
- Test fixture installation accepts an option to omit `annotations.json`.
- The existing provider represents duplicate creation of the same display name with a distinct auto-renamed child, matching external-storage SAF behavior.
- Test sequence constructs `ProjectPenStore(resolver, sourceUri, null)` before a `ProjectAnnotationStore` writes a PDF annotation.

- [x] **Step 1: Add provider controls and `annotationsCreatedAfterOpenAreSharedByAnnotationAndPenStores`.** Assert the fixture starts without an annotations child, the annotation store creates the canonical file, the preconstructed pen store adds one stroke, the child list remains exactly `annotations.json`, and a fresh annotation store reads both records.
- [x] **Step 2: Add `missingAnnotationsFileIsNotCreatedForAnEmptyPenPage` and `failedAnnotationsDirectoryQueryDoesNotCreateAFile`.** Assert no child is created for an empty page and that query failure propagates without a `createDocument` call.
- [x] **Step 3: Run the three focused API 34 instrumentation cases before the fix.** The expected regressions were observed: late discovery produced `annotations (1).json`, and a failed listing fell through to `createDocument`; empty-page behavior remained correct.

### Task 2: Resolve the annotations document lazily in `ProjectPenStore`

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/ProjectPenStore.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/PenAnnotationsUriResolutionTest.kt`

**Interfaces:**
- Add a private `resolveAnnotationsUri(): Uri?` that queries direct children of `sourceDirectoryUri` for the exact `annotations.json` display name, builds a tree-scoped document URI, caches it, and propagates query failures as `IOException`.
- Use the resolver before `supportsWrite`, `readPage`, `readPages`, and `writePage` treat the document as missing.

- [x] **Step 1: Implement only lazy URI resolution and route those operations through it.** Keep first-stroke creation and the current merge/recovery logic unchanged.
- [x] **Step 2: Run `PenAnnotationsUriResolutionTest` on API 34 and verify GREEN (3/3).**
- [x] **Step 3: Verify the empty-page/no-file and child-query-failure cases in the same API 34 run.**

### Task 3: Verify existing behavior and record the result

**Files:**
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

- [x] **Step 1: Run `:app:testDebugUnitTest --offline`, the focused SAF instrumentation test, `:app:compileDebugAndroidTestKotlin`, and `:app:assembleDebug`.**
- [x] **Step 2: Update Android task 7 and task 12 with the AVD regression result, clearly retaining physical stylus validation as outstanding.**
- [x] **Step 3: Review the diff, commit the verified change, and push only to `fork/codex/android-port`.**
