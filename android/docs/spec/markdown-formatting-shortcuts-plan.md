# Markdown Formatting Shortcuts Implementation Plan

> Android-only implementation plan for task 49. Implement test-first and update root `CHANGELOG.md` for every push.

## Steps

1. Add policy tests for Ctrl/Cmd+B, I, K and unmodified/unsupported combinations.
2. Extend the Activity shortcut handler so formatting keys are considered only while the native Markdown editor owns focus and is writable/enabled/not busy.
3. Reuse `applyMarkdownInlineFormatting(BOLD/ITALIC)` and `applyMarkdownLinkInsertion()`; preserve existing undo/redo mutation tracking.
4. Add API 34 tablet Activity tests comparing shortcuts with toolbar output and selection/caret, plus a dialog/other-input focus guard case.
5. Run focused JVM tests, API 34 instrumentation, AndroidTest compilation, and `:app:assembleDebug`; record exact results and mark task 49 complete.

## Constraints

- Existing Ctrl/Cmd+S and Alt+Arrow navigation remain unchanged.
- Do not consume formatting keys from a dialog, another text field, preview, PDF pane, or read-only editor.
