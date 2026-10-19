# Pen Project Integration

## Scope

Connect the native Android pen surface to the opened pagenote project's `src/annotations.json`. This integration SPEC stays inside `android/docs/spec/` and follows the existing `PenStroke`/`PenPageStore` shape.

The reader loads persisted `document` strokes for the active PDF page, exposes pen/highlighter/eraser and page-scoped undo/redo/clear, then writes completed mutations back through SAF. The annotation file is created only after the user first changes a stroke and only when both tree and document-provider permissions allow writing.

## Data Integrity

- X uses the existing 0..1000 document-width coordinate space; Y uses the same scale so portrait-page values may exceed 1000. Pressure is stored in the `pressures` array.
- Saving rereads the latest `annotations.json` and changes only freehand `pen`/`highlight` items for the target `document` page.
- Unknown top-level fields on a loaded freehand item are retained when that stroke remains in the edited page; removing a stroke or clearing the page removes its fields together with the item. Extension fields cannot override canonical stroke fields.
- Other annotation types on the same page, other pages, the full `note` side, and unrelated root fields are retained.
- A save error leaves the in-memory stroke visible, reports unsaved state, and permits retry.
- Read-only projects can display existing strokes but cannot activate editable pen tools.

## Acceptance

- Opening a project restores the current PDF page's stored pen/highlighter strokes.
- A new stroke saves to the matching `document` page in `annotations.json`, with normalized pressure points.
- Erasing, undo, redo, and clear affect only the current page and can be saved.
- Round-trip writes retain non-freehand annotations, all other pages, note-side items, and unknown root fields.
- When a page is changed, every retained freehand stroke preserves nested unknown JSON fields through `annotations.json` read, edit, and write.
- Missing `annotations.json` is not created until the first successful stroke save.
- SAF write failures are visible and do not clear the current page's in-memory drawing.

## Verification

- JVM tests cover freehand replacement while retaining unrelated item maps and extension fields on unchanged strokes.
- App compilation verifies SAF and native drawing-view integration.
- Physical device acceptance is required for stylus latency, active pressure, passive stylus/finger policy, palm rejection, eraser-button mapping, and screen-edge behavior.
