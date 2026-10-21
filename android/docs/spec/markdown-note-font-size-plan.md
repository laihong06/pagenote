# Android Markdown Note Font Size Implementation Plan

> Follow the repository's Android SPEC workflow and TDD: write and observe focused tests fail before production changes.

**Goal:** Enable `Paragraph > Font size` for Android Markdown notes using the same seven values as desktop.

**Architecture:** Add a validated `noteFontSize` field to the existing `ReaderSettings` and DataStore. Replace the disabled menu placeholder with a selection dialog gated by `canFormatMarkdown`. Pass the saved size to the note editor and preview renderer; do not alter the source-document pane or project files.

**Spec:** `android/docs/spec/features/markdown-note-font-size.md`

## Constraints

- Minimum/target device remains Android 14/API 34 Xiaomi Pad 5 Pro (Snapdragon 870).
- Keep the desktop options exactly `[12, 15, 18, 20, 24, 28, 32]`; default is 15.
- Reuse the existing Compose, Markwon, DataStore, menu, and localized string patterns; add no dependency.
- No note text mutation, auto-save, project schema change, or unrelated source-pane scaling.
- Run focused JVM tests, one API 34 tablet AVD Activity test, and `:app:assembleDebug`; no broader device matrix.
- Update root `CHANGELOG.md` in the implementation push and push only to `fork/codex/android-port`.

## Review Focus

- Invalid persisted values fall back independently to 15; validation rejects unsupported sizes.
- Cancel makes no DataStore write; Apply writes before the new value becomes active.
- A persistence failure retains the last active size and presents a localized error.
- Editor and preview sizes update without resetting draft text, cursor selection, history, or revision.
- The command remains disabled outside the writable Markdown editor; the PDF/Markdown source pane is unaffected.

## Tasks

### Task 1: Settings policy and persistence

**Files:** `ReaderSettings.kt`, `ReaderSettingsStore.kt`, focused JVM tests.

- [x] Add policy tests for the desktop value set, default, invalid save rejection, and independent fallback.
- [x] Add a DataStore round-trip test for a non-default size across store instances.
- [x] Run the focused test class before implementation; it failed on the missing setting field and policy API.
- [x] Implement the setting field, validation, fallback, and persistence; all focused settings tests pass.

### Task 2: Reader menu and rendering

**Files:** `MainActivity.kt`, `MermaidPreviewHost.kt`, `ReaderSettingsDialog.kt` or a dedicated font-size dialog, localized strings, focused Activity instrumentation.

- [x] Add one Activity test for no-project/read-only/preview gating, apply/cancel, editor and preview base sizes, unchanged note draft, and persisted preference.
- [x] Implement the localized choice dialog and wire `font_size` to `canFormatMarkdown`.
- [x] Apply the selected preference to the active editor and preview, including LaTeX base size, without changing note content.
- [x] Run the focused API 34 Activity test; it passes, including Activity recreation.
- [x] Rerun the focused settings JVM tests; 13/13 pass.

### Task 3: Traceability and delivery

- [x] Update `android/docs/spec/tasks.md` task 26 and root `CHANGELOG.md`.
- [x] Run `:app:assembleDebug`, `git diff --check`, and review the final diff.

Delivery: commit and push this slice to `fork/codex/android-port`; report the AVD result separately from pending Xiaomi hardware acceptance. This is a source push, not a public binary release.
