# Android Reader Interaction Acceptance Plan

> **For agentic workers:** Use the Android SPEC and execute each slice independently. Steps use checkbox syntax for tracking.

**Goal:** Revalidate and close the six Android reader interactions requested in task 50, fixing real behavior gaps rather than treating prior implementation or component tests as acceptance.

**Architecture:** Keep the existing native Compose reader, `PdfPageViewport`, `PenCanvasView`, DataStore settings, `ReaderPagePrefetcher`, and native PDF annotation history. Extend the current page-change, coordinate, cache, and shape-conversion paths; do not add another project format or cloud service.

**Tech Stack:** Kotlin, Jetpack Compose, Android Views, DataStore, coroutines, JUnit, Android instrumentation, API 34 tablet AVD.

**Spec:** `android/docs/spec/features/reader-interaction-enhancements.md` and its four linked feature contracts.

## Global Constraints

- Keep feature behavior uniform on Android 14 and later; API 34 is the minimum and sole routine instrumentation target.
- Keep portable project coordinates and schemas unchanged (`annotations.json`, pagenote v1, page-scoped note revisions).
- Keep automatic preparation local, sequential, cache-aware, and free of model/network calls.
- Test first for every behavior change; after a complete slice compile affected Android test sources and assemble `:app:assembleDebug`.
- Add a matching root `CHANGELOG.md` Unreleased entry to each feature commit, commit each completed slice, and push it to `fork/codex/android-port-upstream`. Do not publish a release or alter desktop packages.

## Review Focus

- Touch swipe competing with pinch, pen input, OCR/text selection, annotation drag, and dirty-note save; the Activity page index must change only through the common page-change path.
- Ink points outside the fitted page when the option changes, and coordinate mapping after viewport zoom/pan; preserve old coordinates and pressure on save/reload.
- Queue cancellation during non-cooperative preparation or a project/source revision change; stale results must never be consumed for a different identity.
- Dwell timer cancellation on lift, tool change, page change, detach, and movement after preview; failed native-annotation persistence must retain the original freehand stroke.
- Settings default, boundary values, process recreation, and labels/accessibility for the toolbar controls.

---

### Task 1: PDF swipe and header navigation

**Files:** `MainActivity.kt`, `PdfPageSwipePolicy.kt`, `ReaderPageJumpActivityTest.kt`, and focused tests as needed.

- [x] Run `PdfPageSwipePolicyTest` and the API 34 `ReaderPageJumpActivityTest` navigation cases; verify vertical up/down swipes and adjacent header buttons against the same real three-page PDF reader session.
- [x] Review threshold/diagonal rejection, document boundaries, and dirty-note conflict/pending-save guards across the policy, common page-change path, and Activity tests; no navigation gap requiring a code change was exposed.
- [x] No implementation fix was needed after the focused regressions passed.
- [x] Run focused JVM/Activity tests, `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug`, and `git diff --check`.
- [x] Record this audit in SPEC/task evidence and push the documentation and verification slices.

### Task 2: Zoomed ink and optional outside-page canvas

**Files:** `feature-pen/PenCanvasGeometry.kt`, `PenCanvasView.kt`, `ReaderSettingsDialog.kt`, settings stores, and focused tests.

- [x] Run zoom/pan geometry and View stylus tests plus DataStore round-trip tests on the API 34 AVD.
- [x] Add a focused Reader Settings interaction regression for the outside-page switch; it verifies default-off, toggle, save, and restored state.
- [x] Confirm existing geometry/View tests cover render/input round-trip after zoom, default clipping, and visible-margin coordinates when enabled; no additional runtime change was exposed.
- [x] Run the focused `feature-pen` and app JVM tests, affected instrumentation, `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug`, and `git diff --check`.
- [x] Update SPEC/task evidence and changelog; commit and push this slice.

### Task 3: Automatic forward-page preparation

**Files:** `PagePrefetchCoordinator.kt`, `PageTutorSourceLoader.kt`, `ReaderSettings*`, `MainActivity.kt`, and focused tests.

- [x] Run window, sequential scheduling, overlap reuse, revision/project cancellation, failure isolation, native-text/OCR fallback, settings persistence, and API 34 slider tests.
- [x] Confirm the reader launch and page-change effects use the configured window and that Page Tutor consumes matching cached results; no wiring gap requiring a code change was found.
- [x] Confirm zero-disable, current-page-inclusive window, source-revision cache identity, and local-only preparation remain intact.
- [x] Run focused app JVM and settings instrumentation, `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug`, and `git diff --check`.
- [x] Record this audit in SPEC/task evidence and push the documentation and verification slices.

### Task 4: Dwell shape recognition end-to-end

**Files:** `feature-pen/PenShapeRecognizer.kt`, `PenCanvasView.kt`, `PdfAnnotationVectorDraft.kt`, `MainActivity.kt`, and focused unit/instrumentation tests.

- [x] Run recognizer, vector conversion, and View dwell tests for supported shapes, early lift, ambiguous path, movement after preview, highlighter exclusion, and cancellation.
- [x] Confirm the existing Activity regression proves native-shape persistence and page-scoped undo/redo; add and pass a Reader Activity regression proving failed annotation persistence falls back to the original freehand stroke.
- [x] No integration fix was needed: the failure fallback already preserves the stroke, pressure samples, and project schema.
- [x] Run focused feature/app JVM tests, View and Reader Activity instrumentation on API 34, `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug`, and `git diff --check`.
- [x] Update SPEC/task evidence and changelog; commit and push this slice.

## Current State

- Existing implementations for tasks 39-42 were revalidated with focused JVM tests and an API 34 tablet AVD. The outside-page setting now has UI coverage for default-off, localized accessibility name, save, and restored state.
- `PdfAnnotationSelectionClipboardActivityTest.endpointDwellPersistsNativeShapeAndUsesAnnotationUndoHistory` already proves native-shape persistence and undo/redo. This execution adds `recognizedShapeWriteFailureFallsBackToOriginalFreehandStroke` to cover the remaining persistence-failure fallback path.
- API 34 tablet AVD results: reader navigation Activity 5/5, pen View 9/9, settings 2/2, native-shape persistence/history 1/1, and failed-write fallback 1/1. Focused JVM policies/stores/coordinator/loader/geometry/recognizer/vector tests, AndroidTest Kotlin compilation, `:app:assembleDebug`, and `git diff --check` pass.
