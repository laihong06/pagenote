# PDF Vector Annotation Authoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create desktop-compatible PDF line, arrow, rectangle, and ellipse annotations with native touch/stylus dragging.

**Architecture:** Add a pure inverse-transform mapper and vector-item factory, then wire one drag path through the existing PDF viewport. The active tool supplies a live overlay preview; release appends the serialized item through the current SAF read/merge/write flow.

**Tech Stack:** Kotlin, Jetpack Compose pointer input/Canvas, kotlinx.serialization JSON, JUnit, Compose Android instrumentation tests.

**Spec:** `android/docs/spec/features/pdf-vector-annotation-authoring.md`

## Global Constraints

- The supported device is Xiaomi Pad 5 Pro / Snapdragon 870 / Android 14 (API 34); one API 34 tablet AVD is the only routine proxy.
- Annotation coordinates are 1000 units per page width on both axes, not OCR's square normalization.
- Keep existing text/region entry and pen persistence behavior unchanged.
- Preserve existing and unsupported JSON entries during a shape save.
- Every source push includes a root `CHANGELOG.md` Unreleased entry; no public binary release is implied.

## Review Focus

- Portrait-page Y coordinates greater than 1000 survive inverse mapping and serialization.
- Zoomed/panned coordinate conversion follows the displayed page, not the viewport origin.
- Starts in letterbox margins and degenerate drags do not persist.
- Tool mode blocks conflicting pen and viewport gestures but exits cleanly when another tool is selected.
- A failed SAF write does not leave a false persisted-state preview or discard existing annotations.

---

### Task 1: Drag-to-Create PDF Vector Annotations

**Files:**
- Create: `android/app/src/main/java/io/pagenote/android/PdfAnnotationVectorDraft.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/PdfViewportTransform.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/PdfAnnotationOverlay.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Test: `android/app/src/test/java/io/pagenote/android/PdfViewportTransformTest.kt`
- Test: `android/app/src/test/java/io/pagenote/android/PdfAnnotationVectorDraftTest.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/PdfAnnotationAuthoringTest.kt`

**Interfaces:**
- Consumes: `mapPdfTapToAnnotationPoint`, `PdfPageViewport`, `PdfAnnotationOverlay`, and `ProjectAnnotationStore`.
- Produces: a page-width inverse drag mapper returning two ordered annotation points, and a validated vector draft that can provide both a preview item and JSON item.

- [x] Add focused JVM tests for zoomed/panned portrait mapping, clipped drag end, invalid start, vector item serialization, and zero geometry; run them red before implementation.
- [x] Add one API 34 Compose gesture test that drags a vector tool across the viewport, observes the preview, and verifies the completed ordered endpoints; run it red.
- [x] Implement the pure mapper and vector draft factory, then route active line/arrow/shape menu actions through drag input with a live preview.
- [x] Persist completed items by merging with the target page's stored annotation array; preserve unrelated types, allow deleting supported vector annotations through the existing annotation list, and surface failed writes without showing a saved item.
- [x] Run the focused JVM classes, the single API 34 authoring instrumentation class, compile Android test Kotlin, assemble Debug app/test APKs, update `android/docs/spec/tasks.md` and root `CHANGELOG.md`, review, commit, and push `codex/android-port` to `fork`.
