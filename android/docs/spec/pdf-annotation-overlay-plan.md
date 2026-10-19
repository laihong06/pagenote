# PDF Annotation Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display persisted PDF rectangle and text annotations at desktop-compatible positions in the native Android reader.

**Architecture:** Parse only supported `rect` and `text` entries into immutable overlay items. Draw them with a Compose Canvas inside the PDF's existing transformed page layer, and reload page annotations from the project store after navigation or mutation.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, kotlinx.serialization JSON, JUnit, Compose Android instrumentation tests.

**Spec:** `android/docs/spec/features/pdf-annotation-overlay.md`

## Global Constraints

- Minimum Android version is Android 14 / API 34.
- The only routine device target is Xiaomi Pad 5 Pro / Snapdragon 870 / Android 14; API 34 tablet AVD is the platform proxy.
- Annotation point coordinates must match the desktop 1000-unit page-width SVG coordinate system; vertical coordinates may exceed 1000.
- Do not mutate or drop unsupported entries when parsing the read-only overlay.
- Every push includes a root `CHANGELOG.md` Unreleased entry; no public binary release is implied.

## Review Focus

- Portrait pages have a width-scaled vertical axis, unlike square OCR regions; add a JVM test for tap mapping and an AVD pixel test for overlay geometry.
- Malformed/unknown annotation entries must be skipped, not crash a page; cover the parser with JVM fixtures.
- Zoom, pan, and clipping must be shared with the PDF image; keep the overlay inside the existing transformed page box and verify its painted pixels.
- Page changes and annotation delete/create must not leave stale overlays; refresh from the current project/page after each state change.
- Unsupported item types must not be silently mutated; mapper tests assert that only rect/text are returned.

---

### Task 1: Coordinate Mapping and Overlay Model

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/PdfViewportTransform.kt`
- Create: `android/app/src/main/java/io/pagenote/android/PdfAnnotationOverlay.kt`
- Test: `android/app/src/test/java/io/pagenote/android/PdfViewportTransformTest.kt`
- Test: `android/app/src/test/java/io/pagenote/android/PdfAnnotationOverlayMapperTest.kt`

**Interfaces:**
- Produces: `mapPdfTapToAnnotationPoint(tap, bounds, transform): Pair<Double, Double>?` and `PdfAnnotationOverlayItem.fromPage(items: JsonArray): List<PdfAnnotationOverlayItem>`.

- [x] Write failing tests for width-scaled y mapping, valid rectangle/text parsing, malformed-entry skipping, and unsupported-type skipping.
- [x] Run only the two focused app JVM test classes and confirm the expected missing API/behavior failures.
- [x] Implement the mapper and desktop-compatible tap coordinate function.
- [x] Run the focused JVM tests and confirm all pass.

### Task 2: Compose PDF Overlay

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/PdfAnnotationOverlay.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/PdfAnnotationOverlayTest.kt`

**Interfaces:**
- Consumes: `PdfAnnotationOverlayItem.fromPage` and `mapPdfTapToAnnotationPoint`.
- Produces: `PdfAnnotationOverlay(items, modifier)` drawing the supported items in the PDF page's existing clip/transform layer.

- [x] Write API 34 Compose pixel and SAF tests for page-width geometry and newly-created annotation files.
- [x] Run the focused API 34 AVD tests and confirm the geometry and post-open URI gaps before the fixes.
- [x] Implement the overlay, current-page refresh, and SAF lookup for `annotations.json` created after project open.
- [x] Run both focused API 34 tests and confirm the rectangle pixels and persisted annotations load correctly.

### Task 3: Build and Commit

**Files:**
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

- [x] Run focused JVM tests, `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest`.
- [x] Install and run only `PdfAnnotationOverlayTest` on the selected API 34 tablet AVD; do not fan out Gradle install tasks to other connected devices.
- [x] Mark the ledger task complete, update Unreleased changelog, review the diff, commit, and push `codex/android-port` to `fork`.
