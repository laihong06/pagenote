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

- [ ] Run `PdfPageSwipePolicyTest` and the API 34 `ReaderPageJumpActivityTest` navigation cases; verify vertical up/down swipes and adjacent header buttons against the same real three-page PDF reader session.
- [ ] If any acceptance case is missing, add the failing Activity regression first: short/diagonal and horizontal gestures, first/last page boundaries, and a dirty-note save or conflict must not diverge between swipe and controls.
- [ ] Make the smallest implementation fix only after the new regression fails for the expected reason.
- [ ] Run focused JVM/Activity tests, `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug`, and `git diff --check`.
- [ ] Update SPEC/task evidence and changelog; commit and push this slice.

### Task 2: Zoomed ink and optional outside-page canvas

**Files:** `feature-pen/PenCanvasGeometry.kt`, `PenCanvasView.kt`, `ReaderSettingsDialog.kt`, settings stores, and focused tests.

- [ ] Run zoom/pan geometry and View stylus tests plus DataStore round-trip tests on the API 34 AVD.
- [ ] Add a focused Reader Settings interaction regression for the outside-page switch if no existing test proves the visible control saves and restores it.
- [ ] Add any missing failing test for render/input round-trip after zoom, clipping by default, and preserving visible-margin coordinates when enabled; implement only behavior exposed by those tests.
- [ ] Run the focused `feature-pen` and app JVM tests, affected instrumentation, `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug`, and `git diff --check`.
- [ ] Update SPEC/task evidence and changelog; commit and push this slice.

### Task 3: Automatic forward-page preparation

**Files:** `PagePrefetchCoordinator.kt`, `PageTutorSourceLoader.kt`, `ReaderSettings*`, `MainActivity.kt`, and focused tests.

- [ ] Run window, sequential scheduling, overlap reuse, revision/project cancellation, failure isolation, native-text/OCR fallback, settings persistence, and API 34 slider tests.
- [ ] Confirm the reader launch and page-change effects use the configured window and that Page Tutor consumes matching cached results. Add a regression before changing any missing wiring.
- [ ] Preserve zero-disable, current-page-inclusive window, source-revision cache identity, and the no-network preparation contract.
- [ ] Run focused app JVM and settings instrumentation, `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug`, and `git diff --check`.
- [ ] Update SPEC/task evidence and changelog; commit and push this slice.

### Task 4: Dwell shape recognition end-to-end

**Files:** `feature-pen/PenShapeRecognizer.kt`, `PenCanvasView.kt`, `PdfAnnotationVectorDraft.kt`, `MainActivity.kt`, and focused unit/instrumentation tests.

- [ ] Run recognizer, vector conversion, and View dwell tests for supported shapes, early lift, ambiguous path, movement after preview, highlighter exclusion, and cancellation.
- [ ] Add a failing Android reader-flow test proving a recognized stylus shape is persisted once as a native annotation and participates in page-scoped undo/redo; prove failed persistence falls back to the original freehand stroke.
- [ ] Implement only the integration fix required by that regression; preserve active style and the project schema.
- [ ] Run focused feature/app JVM tests, View and Reader Activity instrumentation on API 34, `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug`, and `git diff --check`.
- [ ] Update SPEC/task evidence and changelog; commit and push this slice.

## Current State

- Existing implementation and test artifacts are present for tasks 39-42. They are not marked revalidated by this plan until the named commands run in this execution.
- Known evidence gap at plan creation: checked-in instrumentation proves shape recognition at the View callback boundary and conversion to native-vector JSON separately; no checked-in Reader Activity test was found that proves save, undo, redo, and write-failure fallback end to end.
