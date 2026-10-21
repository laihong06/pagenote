# PDF Vector Annotation Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display persisted PDF line, arrow, and ellipse annotations with desktop-compatible geometry.

**Architecture:** Extend the existing annotation mapper to admit and validate three vector types. Draw them in the existing Compose Canvas within the PDF page's transformed layer; preserve the saved JSON unchanged.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, kotlinx.serialization JSON, JUnit, Compose Android instrumentation tests.

**Spec:** `android/docs/spec/features/pdf-vector-annotation-overlay.md`

## Global Constraints

- The only routine device target is Xiaomi Pad 5 Pro / Snapdragon 870 / Android 14; use one API 34 tablet AVD as the platform proxy.
- Coordinates use `1000` units per page width on both axes; do not reuse the square OCR-region normalization.
- Match desktop arrowhead length `14 + width * 2`, wing angle `0.45` radians, and round vector stroke caps.
- Do not mutate saved annotation records or add image/HTML rendering in this slice.
- Every push includes an Unreleased root `CHANGELOG.md` entry; no public binary release is implied.

## Review Focus

- Portrait pages can have Y values greater than 1000; confirm all three vector types use the page-width scale.
- Reverse-ordered ellipse bounds must produce the same geometry as forward bounds.
- Arrowhead direction must follow the ordered first-to-second points and keep the head on the second endpoint.
- Invalid points/widths and deferred types must be skipped, not crash drawing or alter storage.
- Vectors must stay inside the current PDF zoom/pan/clipping transform.

---

### Task 1: Vector Mapping and Rendering

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/PdfAnnotationOverlay.kt`
- Test: `android/app/src/test/java/io/pagenote/android/PdfAnnotationOverlayMapperTest.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/PdfAnnotationOverlayTest.kt`

**Interfaces:**
- Consumes: `PdfAnnotationOverlayItem.fromPage(JsonElement)` and the existing PDF Canvas overlay.
- Produces: parsed `line`, `arrow`, and `ellipse` items rendered in the current page transform.

- [x] Add JVM cases for valid vector parsing, one-point fallback, reversed ellipse bounds, malformed points, and still-deferred `image`/`html` types; run the focused class and observe the missing-type failure.
- [x] Add one Compose pixel test drawing all three vector primitives on a non-square canvas; check the arrow tip and both wings, then run it on the API 34 tablet AVD and observe the missing pixels.
- [x] Implement desktop-compatible vector geometry in the mapper/Canvas while retaining the existing rectangle/text behavior.
- [x] Run the focused JVM classes and the single API 34 overlay instrumentation class.
- [x] Compile Android test Kotlin, assemble Debug app/test APKs, update the task ledger and Unreleased changelog, review, commit, and push `codex/android-port` to `fork`.
