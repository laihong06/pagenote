# PDF Text Selection AI Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Android 14 searchable-PDF text selection and save the selection as a desktop-compatible AI review.

**Architecture:** Keep `PdfRenderer` for rendering. Add a PDFBox-Android adapter over the existing private PDF snapshot, map ordered text positions into platform-independent page text geometry, and route a drag-selection draft through the existing review dialog/store. Store the PDF context revision separately from the captured target note revision so Agent apply authorization remains valid.

**Tech Stack:** Kotlin, PdfBox-Android 2.0.27.0 (Apache-2.0), existing PdfRenderer/Jetpack Compose/SAF review store, JUnit and Android instrumentation.

**Spec:** `android/docs/spec/features/pdf-text-selection-ai-review.md`

## Global Constraints

- Supported device: Xiaomi Pad 5 Pro / Snapdragon 870 / Android 14 (API 34).
- Preserve the existing `PdfRenderer` rendering path and 16-megapixel cap.
- Keep scanned-page OCR region selection; do not invent character offsets from OCR blocks.
- Preserve desktop `width-1000` anchors and existing note-review behavior.
- Extract PDF text on selection entry, with a three-page session cache; do not parse every page during ordinary reading.
- Serialize in-process note writes with the note revision check and review write. Do not claim this prevents concurrent external SAF writers.
- Run affected module tests, Debug build, and only one focused API 34 instrumentation workflow for this UI/platform change.

## Review Focus

- Reversed drag must produce the same reading-order quote as forward drag; the JVM selection test covers this.
- PDF source revision and note target revision must not be conflated; the Agent authorization assertion in the focused workflow covers this.
- Empty/scan-only pages must not create character reviews; when extraction returns no text, the UI reports unavailability and leaves the existing region-OCR command in place. No additional device matrix is added.
- Zoom/pan must not shift the saved anchor; the focused workflow selects after a non-default viewport transform and compares persisted anchor coordinates with the selected glyph bounds.
- Stale note changes must not write a review; the focused workflow verifies unchanged review JSON after rejection.
- A concurrent in-process note write waits until the selection review has passed revision validation and persisted; one forced race in the same API 34 workflow verifies the order.
- The same API 34 workflow exercises a rotated/cropped page and width-1000 geometry; desktop `ai-workspace` verifies shape fallback when the quoted text is absent.

---

### Task 1: Extract Android 14 PDF text geometry

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create: `android/core-reader/src/main/java/io/pagenote/core/reader/PdfTextSelection.kt`
- Create: `android/app/src/main/java/io/pagenote/android/PdfBoxTextExtractor.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/PdfDocumentSession.kt`
- Test: `android/core-reader/src/test/java/io/pagenote/core/reader/PdfTextSelectionTest.kt`

**Interfaces:**
- Produce `PdfPageText(text: String, revision: String, pageWidth: Double, pageHeight: Double, glyphs: List<PdfTextGlyph>)`, with each glyph holding its UTF-16 `[start,end)` span and page-point bounds.
- `PdfDocumentSession.extractSelectableText(pageIndex: Int): PdfPageText?` returns null when there is no local snapshot or no usable text.

- [x] Write one JVM test for forward/reversed drag over ordered glyphs, exact quote/context substring, and width-1000 bounding-rectangle mapping.
- [x] Run `cd android && ./gradlew :core-reader:testDebugUnitTest --offline`; verify it fails because the selection model is absent.
- [x] Implement the pure selection model and mapper in `core-reader`; run the same focused test.
- [x] Add PDFBox-Android 2.0.27.0 and implement one-page extraction from the existing snapshot using `PDFTextStripper` positions; keep the adapter in `app` and keep rendering on `PdfRenderer`.
- [x] Run the focused core-reader test, affected app JVM tests, and `:app:assembleDebug`.

### Task 2: Add PDF drag selection and review persistence

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/ReaderOcrSelection.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/ProjectReviewStore.kt`
- Modify: `android/feature-annotations/src/main/java/io/pagenote/feature/annotations/ReviewStore.kt`
- Modify: Android string resources for selection mode/status.
- Test: one focused test in `android/app/src/androidTest/java/io/pagenote/android/PdfTextSelectionReviewTest.kt`.

**Interfaces:**
- `PdfPageViewport` accepts the current `PdfPageText`, a `textSelectionEnabled` state, and an `onTextSelectionChanged` callback; it emits a selected quote range and page-width-1000 shape.
- `ReviewCreate` gains nullable `targetNoteRevision`; stores persist it only when supplied.

- [x] Add one API 34 instrumentation workflow that selects extracted text in the real reader, saves the document review, and asserts the quote, context hash, anchor, and target note revision in `ai-reviews.json`.
- [x] Run only `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.pagenote.android.PdfTextSelectionReviewTest` on `Pixel_Tablet_API34(AVD) - 14`; 1/1 test passes.
- [x] Implement the AI menu selection mode, transformed highlight overlay, cancel/empty-text behavior, dirty-note save guard, and review creation.
- [x] In the same test workflow, mutate the target note after capture and assert the review file is unchanged after the stale save is rejected.
- [x] Run the focused test, affected app/agent JVM tests, and Debug build.
- [x] Parse selectable text only on selection entry and cache the latest three page results for the session.
- [x] Serialize selection-review revision validation/persistence with all app-process writes to the same note URI; verify the race in the existing API 34 workflow.
- [x] Verify rotated/cropped page text and width-1000 geometry in the same API 34 workflow.
- [x] Render the desktop width-1000 shape anchor when a document selection quote cannot be found; cover with `node tests/ai-workspace.mjs`.

### Task 3: Preserve Agent apply semantics for PDF reviews

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/ProjectReviewStore.kt`
- Test: `android/app/src/test/java/io/pagenote/android/ProjectReviewStoreTest.kt`.

**Interfaces:**
- For `side="document"`, `authorizesApplied` compares the changed note write's expected revision with `targetNoteRevision`; for `side="note"`, keep comparing with `revision`.

- [x] Add one unit assertion proving document review applies against `targetNoteRevision`, not the PDF context `revision`.
- [x] Run `ProjectAgentToolsTest.matchingChangedWriteAuthorizesItsReview` and affected app JVM tests.
- [x] Run `:app:assembleDebug` and `git diff --check`.
