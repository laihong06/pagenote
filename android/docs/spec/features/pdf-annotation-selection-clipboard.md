# Android PDF Annotation Selection and Clipboard

## Scope

Extend the PDF handwriting Select/Copy/Paste/Delete commands from pen/highlighter strokes to PDF overlay objects. Native Android coverage currently includes line, arrow, rectangle, ellipse, text, and project-local image; rich HTML compatibility is specified separately in `pdf-rich-html-annotation.md`. Keep the existing `annotations.json` shape and desktop clipboard marker `PAGE_NOTES_ANNOTATION:\n`.

Pen/highlighter strokes remain owned by `PenPageStore` and keep their existing history. Native overlay annotations remain owned by `ProjectAnnotationStore`; this feature must not replace or reorder unrelated items. Unknown JSON fields on supported items are preserved. A pasted image is accepted only when its `/attachments/<uuid>.<ext>` asset already exists in the active project's `src/assets/` directory.

## Behavior

- The existing Select tool hit-tests visible native overlay items using their rendered geometry and source-page order. A tap on empty space clears annotation selection; a hit selects one item and displays a bounding frame. If no native item is hit, existing pen/highlighter hit-testing remains available.
- Dragging a selected native object translates every point by the same page-space delta. Rectangle, ellipse, line, arrow, and image objects expose a bottom-right resize handle; resizing scales every point about the selection bounds' top-left. Text remains moveable but is not resizable, matching its single-anchor desktop geometry.
- Copy emits exactly the desktop marker and one JSON annotation item. It does not mutate the project. Paste offsets all points uniformly so the first point lands at `(80, 90)` in the shared 1000-unit page coordinate space, removes desktop-only `layoutAnchor`, appends the object, and selects it.
- Delete removes only the selected native item. Paste, delete, move, and resize use per-project/page undo snapshots and the existing SAF recoverable writer. Native annotation history remains separate from `PenPageStore` history; Undo/Redo prefers the most recently changed history kind for that page.
- The app validates clipboard payload size, supported type, complete annotation schema, coordinate bounds, and local image-asset availability before mutation. Invalid or unsupported clipboard content leaves project data untouched and reports a localized status. Rich HTML is sanitized and handled through the offline renderer specified in `pdf-rich-html-annotation.md`.
- Selection is cleared when the project or page changes, when a non-selection drawing tool is activated, and after deletion. Copy/Delete menu enablement follows the selected object and project writability; Paste follows clipboard validity and project writability.

## Acceptance Criteria (EARS)

- When Select is active and the user taps a visible supported native PDF annotation, Android shall select that source annotation and show its selection frame without adding pen input.
- When Select is active and the user taps empty space, Android shall clear native annotation selection and retain existing freehand selection behavior.
- When the user drags a selected object, Android shall move it in shared page coordinates and save one recoverable page update at gesture end; when the user drags a supported resize handle, Android shall scale its points and save one update.
- When a native annotation is selected, Copy shall write the desktop marker plus the complete supported JSON item without modifying `annotations.json`.
- When the system clipboard contains a valid supported item and its local image asset is present when applicable, Paste shall translate the item to the canonical inset, append it to the current writable PDF page, and select the new item.
- When a native annotation is selected and the project is writable, Delete shall remove only that item while retaining sibling annotations and unknown item fields.
- When the user undoes or redoes a native annotation mutation, Android shall restore only that project/page's prior or next complete JSON array; pen history remains available and independent.
- When clipboard data is malformed, oversized, unsupported, invalid under the shared annotation schema, or references an absent local image, Android shall not mutate project data.
- When the page or project changes, Android shall clear the selected source index and any active gesture so subsequent commands cannot target stale data.

## Verification

- Focused JVM tests cover source-index preservation, hit-testing, translation, resize, exact desktop clipboard encoding, validation, unsupported/malformed/oversized payload rejection, image reference checks, and page-scoped undo/redo.
- One Android 14/API 34 tablet AVD Activity flow covers native overlay selection, copy, paste, move, delete, undo/redo, and coexistence with a freehand stroke on the same page.
- Run the focused app/feature tests, the one relevant API 34 tablet AVD test, `:app:assembleDebug`, and `git diff --check`. Xiaomi Pad 5 Pro physical acceptance remains part of parent task 12.
