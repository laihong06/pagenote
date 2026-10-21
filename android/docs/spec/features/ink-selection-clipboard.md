# Android Ink Selection and Clipboard

## Scope

Enable the Android handwriting menu's select, copy, paste, and delete commands for native PDF pen and highlighter strokes. Selection is single-item and page-local. Keep the existing `src/annotations.json` data model and interoperate with desktop clipboard text using the `PAGE_NOTES_ANNOTATION:\n` prefix.

This slice does not add moving/resizing, multi-selection, note-side ink, or selection of vector shapes, text boxes, rich HTML, and image annotations. Those objects use a separate annotation overlay/store path. It does not change project data or clipboard contents until the user explicitly copies an item.

## Behavior

- Selecting the handwriting Select tool changes PDF ink input from drawing to hit-testing. A tap selects the topmost pen/highlighter stroke at that point; a tap on empty page clears selection. The selected item is indicated by a visible bounding outline.
- Selection is scoped to the current document page and is cleared when the page/project changes, when the user changes to a drawing/eraser tool, and after deletion.
- Copy is enabled only when a supported item is selected. It writes one plain-text clipboard item with the exact desktop marker `PAGE_NOTES_ANNOTATION:\n` followed by a JSON annotations item. The item retains its type, color, width, points, pressure, and unknown supported fields.
- Paste is enabled only for writable PDF projects when the system clipboard contains a valid marked `pen` or `highlight` annotations item. Pasting offsets every point by the same delta so the first point lands at the native page's upper-left inset `(80, 90)` in the shared 1000-unit coordinate space. It appends to the current page and uses the existing page-scoped undo, validation, SAF persistence, and failure recovery paths.
- Invalid, unsupported, empty, oversized, or unmarked clipboard text does not mutate the project and reports a localized recoverable status.
- Delete is enabled only when an item is selected and the current PDF page is writable. It removes only that item, clears selection, and uses the existing page-scoped undo and persistence path.
- Pasting a desktop-created item into Android and copying an Android item for desktop paste use the same marker and JSON shape. Android ignores desktop-only layout anchoring when decoding clipboard data.
- Copy is not destructive. Delete is separate; no implicit cut operation is added.

## Acceptance Criteria (EARS)

- When the user activates Select, Android shall stop adding pen strokes and shall select the topmost pen/highlighter stroke hit by a tap.
- When the user taps empty page space in Select mode, Android shall clear the current selection without modifying annotations.
- While a supported item is selected, Android shall show a selection outline and enable Copy and Delete only when the applicable clipboard/project/writability conditions are met.
- When the user copies a selected item, Android shall place the desktop-compatible marker and one validated item in the system plain-text clipboard without changing project data.
- When the user pastes a valid marked item into a writable PDF page, Android shall validate it before mutation, translate all points by a uniform offset, append it to that page, and persist it using the existing recoverable writer.
- When clipboard input is invalid, unsupported, empty, or too large, Android shall leave the current page and clipboard unchanged and report a localized status.
- When the user deletes a selected item, Android shall remove only that item from the current page and persist the updated page through the existing writer.
- When page or project identity changes, Android shall clear the selection so commands cannot target a stale item index.
- When the user undoes or redoes a paste or delete, Android shall restore only the current page's prior or next item list through the existing handwriting history.

## Verification

- Focused feature-pen JVM tests cover desktop-compatible encoding/decoding, validation, point translation, unknown-field preservation, and malformed/unsupported/oversized clipboard rejection.
- One Android 14/API 34 tablet AVD Activity flow covers select, copy, clipboard marker, paste placement, delete, and page-scoped undo/redo through the real reader UI.
- Run `:app:assembleDebug` and `git diff --check`. Do not expand the API/device matrix; Xiaomi Pad 5 Pro physical interaction remains part of parent task 12.
