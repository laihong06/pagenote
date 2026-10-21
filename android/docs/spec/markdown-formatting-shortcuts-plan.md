# Markdown Formatting Shortcuts Implementation Plan

> Android-only implementation plan for task 49. Implement test-first and update root `CHANGELOG.md` for every push.

## Steps

1. [x] Add policy tests for Ctrl/Cmd+B, I, K and unmodified/unsupported combinations.
2. [x] Extend the Activity shortcut handler so formatting keys are considered only while the native Markdown editor owns focus and is writable/enabled/not busy.
3. [x] Reuse `applyMarkdownInlineFormatting(BOLD/ITALIC)` and `applyMarkdownLinkInsertion()`; preserve existing undo/redo mutation tracking.
4. [x] Add API 34 tablet Activity tests comparing shortcuts with toolbar output and selection/caret, plus a dialog/other-input focus guard case.
5. [x] Run focused JVM tests, API 34 instrumentation, AndroidTest compilation, and `:app:assembleDebug`; record results and mark task 49 complete.

## Verification Record

- `ReaderShortcutPolicyTest`, `MarkdownInlineFormattingTest`, and `MarkdownLinkInsertionTest`: 14/14 JVM tests passed, including Ctrl/Cmd mapping, modifier exclusions, and unchanged save/navigation mappings.
- Two focused real-editor Activity tests passed on `Pixel_Tablet_API34(AVD) - 14`: shortcut output and caret match toolbar behavior for selected and collapsed ranges; shortcut edits are undoable; repeated key-down, unfocused/read-only editors, and the review-dialog text field do not change the note.
- AndroidTest Kotlin compilation and `:app:assembleDebug` passed. `scripts/check_16kb_apks.py app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` and `git diff --check` passed.
- The Xiaomi Pad 5 Pro hardware was not re-tested in this slice.

## Constraints

- Existing Ctrl/Cmd+S and Alt+Arrow navigation remain unchanged.
- Do not consume formatting keys from a dialog, another text field, preview, PDF pane, or read-only editor.
