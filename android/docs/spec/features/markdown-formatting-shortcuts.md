# Markdown Formatting Keyboard Shortcuts

## Scope

Add desktop-equivalent physical keyboard shortcuts for inline Markdown formatting without changing reader navigation shortcuts or stealing keys from other editors/dialogs.

## Behavior

- With the writable native Markdown note editor focused, Ctrl+B or Cmd+B applies bold, Ctrl+I or Cmd+I applies italic, and Ctrl+K or Cmd+K inserts a Markdown link.
- Reuse the same formatting functions as the existing toolbar buttons, including placeholder behavior for a collapsed selection.
- Require the editor to be focused, enabled, writable, and not busy. Preview, PDF/source panes, dialogs, and every other input retain native key behavior.
- Ignore key-up/repeated events and unrelated modifier combinations. Existing Alt+Left/Right page navigation and Ctrl/Cmd+S save behavior remain unchanged.
- Selection/caret movement and undo/redo history must match toolbar invocation.

## Acceptance

- Each shortcut produces the same Markdown and resulting selection/caret as its toolbar equivalent for both selected and collapsed text.
- A focused dialog text field and an unfocused/disabled note editor do not trigger note formatting.
- Save, navigation, and other existing shortcuts continue to work without regression.

## Verification

- JVM shortcut-policy tests and API 34 tablet Activity tests with a real focused EditText for formatting, selection, history, and non-editor focus cases; AndroidTest compilation; Debug APK assembly; `git diff --check`.
