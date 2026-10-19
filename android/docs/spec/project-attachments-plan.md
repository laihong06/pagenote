# Project Attachments Implementation Plan

**Goal:** Enable project-local attachment browsing, importing, safe reference-aware deletion, and offline image insertion on Android.

**Architecture:** Add a SAF-backed attachment store scoped to the current project's `src/assets/`, with pure validation/reference helpers. Integrate it through the existing File menu and Compose dialog; reuse the existing Markdown image renderer and note draft/write contracts.

**Spec:** `features/project-attachments.md`

## Tasks

- [x] Add JVM tests for attachment name/type validation, note/annotation reference detection, and delete eligibility.
- [x] Implement SAF list/import/delete operations with collision-resistant UUID names and partial-write cleanup reporting.
- [x] Add the project attachment dialog, import picker, image-to-Markdown insertion, and explicit deletion confirmation.
- [x] Add a focused API 34 Compose regression for image insertion callback and explicit delete confirmation. Full SAF Picker/Provider end-to-end was deferred under the user's reduced-test scope and is not claimed as verified.
- [x] Run focused JVM policy tests, the API 34 AVD dialog test, Android test compilation, app build, and `git diff --check`.
