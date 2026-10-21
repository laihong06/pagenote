# Android Recent Projects Implementation Plan

> Follow the Android SPEC workflow and TDD: write focused tests and observe the missing behavior before production changes.

**Goal:** Enable `File > Recent documents` with a persistent, SAF-safe Android recent-project list.

**Architecture:** Add a small app-private DataStore-backed store for bounded recent project metadata. Record only after the existing `openProjectTree` flow has completed and a persistable read grant is present. Show a native Compose dialog; selecting an item saves any dirty current page, then calls the existing project loader with the stored tree/document identity.

**Spec:** `android/docs/spec/features/recent-projects.md`

## Constraints

- Target remains Xiaomi Pad 5 Pro, Android 14/API 34. The one API 34 tablet AVD is a proxy only.
- Never enumerate arbitrary SAF directories or depend on filesystem paths.
- Keep metadata in private DataStore; do not modify project files, schema, or grants when removing an entry.
- Reuse the existing project-open and page-note revision-check paths; add no dependency.
- Limit history to 12 projects and persist only entries with a persistable read grant.
- Run focused JVM tests, one API 34 tablet AVD Activity test, and `:app:assembleDebug`; do not expand the device/API matrix.
- Update root `CHANGELOG.md` in the push and push only to `fork/codex/android-port`.

## Review Focus

- Recent identity is the pair `(accessTreeUri, rootDocumentId)`, so multiple projects under one SAF tree remain distinct.
- A malformed record cannot crash settings loading or expose an unsafe path.
- Failed or cancelled note saves block project switching; opening and recording recents happen only after successful project load.
- Replacing a PDF renderer must not recycle a bitmap that Compose may still be drawing; retired page bitmaps wait two display frames before reclamation.
- Removing history never releases grants or deletes project data.
- A revoked grant is reported as an open failure; the UI keeps a recovery path through the existing picker.

## Tasks

### Task 1: Recent metadata policy and persistence

**Files:** New `RecentProjectsStore.kt`; focused app JVM tests.

- [x] Add failing tests for move-to-front/deduplication, max-12 truncation, identity removal, and invalid-entry handling.
- [x] Add failing DataStore tests for round-trip across store instances and malformed persisted JSON fallback.
- [x] Run the focused tests and confirm they fail because the recent-project API does not exist.
- [x] Implement the JSON codec, policy, and DataStore store; rerun focused tests (7/7).

### Task 2: File menu dialog and reopen workflow

**Files:** `MainActivity.kt`, new `RecentProjectsDialog.kt`, localized strings, focused Android Activity test.

- [x] Add an Activity test for the empty state, SAF recent-project reopening, dirty-page save-before-switch, and remove-without-deleting-project behavior.
- [x] Implement success-only history recording after `openProjectTree`, with persistable-read-grant checks.
- [x] Replace the disabled menu placeholder with the recent list dialog and empty-state picker action.
- [x] Reopen selected items with the stored tree/document identity only after the current dirty note saves; preserve the existing loader's revision and permission semantics.
- [x] Run the focused Activity test on `Pixel_Tablet_API34` (1/1) and the focused JVM tests (7/7).

### Task 3: Traceability and delivery

- [x] Update `android/docs/spec/tasks.md` task 27 and root `CHANGELOG.md`.
- [x] Run `:app:assembleDebug`, `git diff --check`, and review the final diff.

Delivery: commit and push this source slice to `fork/codex/android-port`. Report API 34 AVD evidence separately from pending Xiaomi hardware acceptance; do not publish a binary release.
