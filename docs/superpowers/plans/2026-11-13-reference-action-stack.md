# Android Reference Action Stack Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace reader layer-label history with a page-scoped, cursor-based history of edit action objects.

**Architecture:** Mirror the recovered reference `ActionStack` list/cursor semantics. Keep PageNote's pen and PDF annotation snapshot stores behind action adapters for this milestone, and preserve pending async gesture reservations and failure rollback.

**Tech Stack:** Kotlin, Android Gradle Plugin, JUnit, Android instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-11-13-reference-action-stack-design.md`; source evidence and current limitations are also recorded in `docs/spec/reference-action-history.md`.

## Global Constraints

- Keep the page-file format unchanged and preserve current reading, editing, and saving flows.
- Keep this implementation Android-only; do not change Windows or macOS code or packages.
- Every push updates root `CHANGELOG.md` and `docs/android/CHANGELOG.md` under Unreleased.
- Android runtime acceptance uses only `Pixel_Tablet_API34_Acceptance`, serial `emulator-5558`, in landscape.
- Do not claim full reference kernel parity, process-persistent history, real-stylus verification, or desktop round-trip.
- No UI changes are planned; state the screenshot exemption in the PR update.

## Review Focus

- An edit after undo truncates only the redo suffix for that project/page; test branch replacement.
- An in-flight gesture preserves its original chronology while later actions can commit; test pending-group ordering.
- A failed undo or redo leaves the same action retryable; test both directions.
- Empty or unchanged transactions do not create phantom entries; test no-op completion.
- Page reorder preserves the action list under the new page identity; test remapping and project isolation.

---

### Task 1: Integrate the reference-shaped action list into the reader

**Files:**
- Create: `android/app/src/main/java/io/pagenote/android/ReaderActionStack.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Delete after migration: `android/app/src/main/java/io/pagenote/android/ReaderEditHistoryOrder.kt`
- Test: `android/app/src/test/java/io/pagenote/android/ReaderActionStackTest.kt`
- Update: `android/app/src/androidTest/java/io/pagenote/android/PdfAnnotationSelectionClipboardActivityTest.kt`
- Update: `docs/spec/reference-action-history.md`
- Update: `docs/android/CHANGELOG.md`, `CHANGELOG.md`

**Interfaces:**
- Consumes: Existing `PenPageStore`, `PdfAnnotationEditHistory`, and current persistence callbacks.
- Produces: `ReaderActionStack.record(projectId, page, action)`, `beginGroup(projectId, page)`, `recordInGroup(...)`, `endGroup(...)`, cursor-based `beginUndo`/`completeUndo` and redo equivalents, rollback hooks, and page remapping. Actions carry their affected layer operation and can contain ordered child actions for one gesture.

- [x] **Step 1: Write failing unit tests** for `REF-ACTION-01/02/03`, max-depth eviction, page isolation/remapping, empty group completion, and failure rollback. Follow-up delayed-persistence tests first failed for undo/redo with later edits, then passed with the rollback fix.
- [x] **Step 2: Run `./gradlew :app:testDebugUnitTest --tests io.pagenote.android.ReaderActionStackTest`**; all 6 focused tests pass after the fix.
- [x] **Step 3: Implement the minimal cursor-based action model** and integrate pen, annotation, composite erase, recognition, and selection history callsites in `MainActivity.kt`.
- [x] **Step 4: Run `./gradlew :app:testDebugUnitTest :feature-pen:testDebugUnitTest`**; 660 app and 153 feature-pen tests pass.
- [x] **Step 5: Run connected acceptance only on `emulator-5558`**: `PdfAnnotationSelectionClipboardActivityTest` 23/23 and `PdfAnnotationAreaEraserInstrumentedTest` 5/5 pass on `Pixel_Tablet_API34_Acceptance`, API 34, landscape 2560×1600.
- [x] **Step 6: Update the history investigation record, changelogs, and existing Draft PR description** with exact milestone, acceptance IDs, device, results, and remaining limits; note that no UI layout changed.
- [ ] **Step 7: Commit and push to the existing fork branch** after verification; do not merge or publish.
