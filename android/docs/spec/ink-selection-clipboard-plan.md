# Android Ink Selection and Clipboard Plan

> Follow SPEC-first TDD. Implement the small native selection/clipboard slice without broadening the Android 14/API 34 tablet test scope.

**Goal:** Enable PDF-page pen/highlighter selection, desktop-compatible copy/paste, and selected-item deletion.

**Architecture:** Keep selection state in `PenCanvasView`; keep authoritative mutations in the existing `PenPageStore` and `MainActivity` page save flow. Add a bounded, pure Kotlin clipboard codec in `feature-pen`, and use Android's existing system `ClipboardManager`.

**Spec:** `android/docs/spec/features/ink-selection-clipboard.md`

## Tasks

### Task 1: Clipboard contract tests

- [x] Add failing unit tests for exact marker encoding/decoding, uniform point translation, pressure and extra-field round-trip, and invalid/unsupported/oversized input rejection.
- [x] Run only the feature-pen focused JVM tests and confirm the codec is missing.
- [x] Implement the bounded codec and rerun those tests (6/6).

### Task 2: Native selection and menu commands

- [x] Add an API 34 tablet Activity test for tap selection, copy marker, paste at the specified inset, delete, and page-scoped undo/redo.
- [x] Implement the Select tool, visible selection outline, stale-selection clearing, and menu enabled states.
- [x] Wire copy/paste/delete through `ClipboardManager`, `PenPageStore`, and existing recoverable save handlers; localize all user-facing statuses.
- [x] Run the focused Activity test on the Android 14/API 34 tablet AVD (1/1).

### Task 3: Traceability and delivery

- [x] Update Android SPEC task 28 and root `CHANGELOG.md`.
- [x] Run focused feature-pen JVM tests, the single Activity test, `:app:assembleDebug`, Android test-source compilation, and `git diff --check`.
- [x] Commit `8e7800c` and push this source slice to `fork/codex/android-port`; no binary release or PR was created.

## Review Focus

- Clipboard payload exactly matches the desktop prefix and JSON item contract.
- Pasted coordinates shift uniformly and preserve pressure/metadata while ignoring desktop-only layout anchors.
- Clipboard parsing is size-bounded and validates before any page-store mutation.
- Selection indices cannot survive page/project changes or mutations that could retarget another item.
- Delete/paste remain undoable and use the existing serialized recoverable SAF writer.
