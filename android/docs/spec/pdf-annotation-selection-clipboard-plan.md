# Android PDF Annotation Selection and Clipboard Plan

> Follow SPEC-first TDD. Keep PDF overlays in `ProjectAnnotationStore`; preserve the separate pen store and use the single Android 14/API 34 tablet AVD scope.

**Goal:** Extend desktop-compatible selection and clipboard operations to Android-rendered PDF overlay annotations.

**Architecture:** Add a pure Kotlin annotation clipboard/geometry/history helper in `app`; preserve source JSON indexes in overlay items; route selection gestures through the existing native canvas view; commit one guarded SAF page update at gesture end. Keep pen/highlighter selection, clipboard codec, and history in `feature-pen` unchanged.

**Spec:** `android/docs/spec/features/pdf-annotation-selection-clipboard.md`

## Tasks

### Task 1: Pure annotation contract tests

- [x] Add failing tests for clipboard marker/schema validation, type gating, page-space translation, resize, hit-test precedence, source-index preservation, and page history.
- [x] Run only the focused app JVM tests and record the expected failures.
- [x] Implement the helper and rerun the focused tests.

### Task 2: Reader selection and actions

- [x] Preserve raw page JSON indexes alongside visible overlay objects and draw the selected frame/resize handle.
- [x] Route select-mode taps and drags to native annotations while preserving freehand selection when no native object is hit.
- [x] Wire copy/paste/delete and page-scoped annotation undo/redo to the existing SAF recoverable write path.
- [x] Add a focused Activity test for selection, clipboard round-trip, move/delete history, image/data validation, and freehand coexistence.
- [x] Run that test on the sole Android 14/API 34 tablet AVD.

### Task 3: Traceability and delivery

- [x] Update Android task 29 and the root `CHANGELOG.md` Unreleased section.
- [x] Run affected JVM tests, focused instrumentation, Android test compilation, `:app:assembleDebug`, and `git diff --check`.
- [x] Commit and push the feature and its change log to `fork/codex/android-port`; record Xiaomi Pad 5 Pro acceptance as outstanding.

## Verification Record

- TDD red phase confirmed the focused contract test could not compile before the clipboard/geometry/history helper existed.
- Eight focused `PdfAnnotationClipboardTest` cases and the affected `feature-pen` JVM suite pass, including arrowhead hit testing and multi-line text bounds.
- `PdfAnnotationSelectionClipboardActivityTest` passes 1/1 and the existing `InkSelectionClipboardActivityTest` regression passes 1/1 on `Pixel_Tablet_API34` (Android 14/API 34).
- Native selection gestures use the canvas' active pointer index rather than assuming pointer zero, preserving correct pointer routing when contacts coexist.
- Android test Kotlin compilation, affected JVM tests, `:app:assembleDebug`, and `git diff --check` pass.
- Xiaomi Pad 5 Pro physical-device acceptance remains open under Android task 12; no public binary was released.
