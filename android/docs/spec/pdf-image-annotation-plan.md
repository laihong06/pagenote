# PDF Image Annotation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement desktop-compatible offline image annotations on Android PDF pages.

**Architecture:** Reuse `ProjectImageImporter` for validated SAF asset import and `ProjectAnnotationStore` for recoverable annotation persistence. Add a small pure draft/placement helper, then extend the current-page overlay with bounded asynchronous project-asset decoding; capture and revalidate the reader context across the system document picker.

**Tech Stack:** Kotlin, Jetpack Compose, Android `OpenDocument`, SAF `DocumentsContract`, `PdfRenderer`, existing annotation/project modules.

**Spec:** `android/docs/spec/features/pdf-image-annotation.md`

## Global Constraints

- Minimum and only routine Android target: Android 14 / API 34, Xiaomi Pad 5 Pro (Snapdragon 870); a single API 34 tablet AVD is a proxy, not device acceptance.
- Keep standard `project.pagenote` and annotation schemas: `annotations.json` uses desktop `/attachments/<uuid>.<ext>` URLs, while project image bytes live under `src/assets/<uuid>.<ext>`; Android resolves that safe URL to the matching SAF child without external access.
- Root `CHANGELOG.md` must describe every push; source pushes go to `fork/codex/android-port`, not a public binary release.
- Instrumentation tests inject the selected image URI and remain inside MainActivity; do not launch DocumentsUI or another app.

## Review Focus

- A picker result arriving after page/project/session change must not write to the stale destination; pin this in the Activity flow test.
- Asset import success followed by annotation-write failure must clean up the new asset or report its residual name; pin provider write failure and inspect assets/annotation state.
- Malformed or remote image URLs must not trigger external access or crash page composition; pin the parser/loader test.
- Missing or undecodable local assets must show a visible placeholder while text/vector annotations and the PDF remain visible; pin an overlay pixel test.
- Portrait pages and extreme aspect ratios must retain page-width coordinate semantics without invalid/non-finite geometry; pin placement helper tests.

### Task 1: Image Draft and Placement Contract

**Files:**
- Create: `android/app/src/main/java/io/pagenote/android/PdfImageAnnotationDraft.kt`
- Create: `android/app/src/test/java/io/pagenote/android/PdfImageAnnotationDraftTest.kt`
- Test: `android/app/src/test/java/io/pagenote/android/PdfImageAnnotationDraftTest.kt`

**Interfaces:** `PdfImageAnnotationDraft.visibleTopY(bounds: PdfViewportBounds, transform: PdfViewportTransform, density: Float): Double?` maps the visible top inset to width-1000 page Y. `PdfImageAnnotationDraft.create(assetName: String, pixelWidth: Int, pixelHeight: Int, visibleTopY: Double, style: PenStyle): JsonObject?` returns a validated annotation object or null. `assetName` is a single validated project filename; coordinates use page-width 1000 units.

- [x] Write tests for x=80, visible-top Y with minimum 90, width=350, aspect-preserving height, captured style, and malformed names/dimensions.
- [x] Run `./gradlew :app:testDebugUnitTest --tests io.pagenote.android.PdfImageAnnotationDraftTest`; confirm the new symbols/behavior fail.
- [x] Implement the pure helper and run the focused JVM test to green.

### Task 2: PDF Image Overlay Rendering

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/PdfAnnotationOverlay.kt`
- Create: `android/app/src/main/java/io/pagenote/android/PdfAnnotationImageLoader.kt`
- Create: `android/app/src/main/java/io/pagenote/android/PdfAnnotationImageReference.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Test: `android/app/src/test/java/io/pagenote/android/PdfAnnotationImageReferenceTest.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/PdfAnnotationImageOverlayTest.kt`

**Interfaces:** `PdfAnnotationImageReference.assetName(raw: String): String?` accepts only desktop-compatible `/attachments/<uuid>.<ext>` raster URLs. Keep `PdfAnnotationOverlayItem.fromPage` pure and carry only that validated filename in parsed items. `PdfAnnotationImageLoader.load(assetName: String): Bitmap?` resolves the filename under the project `assets` directory and returns a bounded bitmap or null. Overlay receives decoded current-page images keyed by asset name.

- [x] Write JVM tests rejecting remote, absolute, traversal, malformed, and unsupported image paths.
- [x] Run the focused JVM class and confirm invalid-path/image-render behavior is not implemented.
- [x] Add bounded background decode (maximum edge 2048 px), current-page-only loading, Compose image drawing in the PDF's width-1000 coordinate space, and a visible failure placeholder.
- [x] Run focused JVM tests and compile the API 34 Android test source; exercise missing/corrupt SAF assets in the AVD test provider flow.
- [x] Add/run one API 34 AVD pixel test covering image bounds/aspect ratio, adjacent existing vector rendering, and visible missing-image fallback.

### Task 3: Handwriting > Insert Image Flow

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Create: `android/app/src/androidTest/java/io/pagenote/android/PdfImageAnnotationActivityTest.kt`

**Interfaces:** Add an `OpenDocument` image picker with the existing Activity-result test injection pattern. Capture project/session/page/style before launch; after import and before save, require the same project/session/page and writable state. On success append with the existing serialized annotation write path, then refresh the overlay. On persistence error, remove the newly imported asset and surface any residual filename.

- [x] Write an Activity test for no-project/Markdown disabled gates, cancellation, successful import, JSON schema, current-page-only persistence, decoded overlay pixels, and stale-context/write-failure cleanup.
- [x] Run the focused test on the API 34 AVD; observe the imported image being rolled back because the initial `assets/<uuid>` annotation URL violates the desktop validator contract.
- [x] Connect the menu action, SAF picker, shared importer, context validation, rollback, status messages, and overlay refresh; correct annotation serialization to the desktop `/attachments/<uuid>` URL.
- [x] Run the focused Activity flow on API 34 only (3/3); run affected JVM classes (7 tests); run `:app:compileDebugAndroidTestKotlin` and `:app:assembleDebug`.

### Task 4: Record, Commit, and Push

**Files:**
- Modify: `android/docs/spec/tasks.md`
- Modify: `android/docs/spec/pdf-image-annotation-plan.md`
- Modify: root `CHANGELOG.md`

- [x] Mark task 6.7 complete with actual API 34/JVM/build evidence and explicitly leave Xiaomi hardware validation in task 12.
- [x] Run `git diff --check`, inspect the complete diff and APK output, then commit as `feat(android): add pdf image annotations`.
- [x] Push `codex/android-port` to `fork`; verify remote SHA and clean tracked worktree.
