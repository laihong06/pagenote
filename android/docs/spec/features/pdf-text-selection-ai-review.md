# PDF Text Selection to AI Review

## Goal

On the supported Android 14 Xiaomi Pad 5 Pro, let a user drag-select searchable PDF text and save it as a page-scoped AI review that remains compatible with the desktop review record format.

## Scope

- Keep Android `PdfRenderer` as the displayed-page renderer. Use `com.tom-roush:pdfbox-android:2.0.27.0` only to read ordered text positions from the app-private PDF snapshot; initialize its resource loader once before extraction.
- Extract selectable text only when the user enters PDF text-selection mode. Cache up to three page results per PDF session, including empty results, and clear the cache when the session closes.
- Do not add a second native PDF ABI or replace the current renderer. Keep the existing offline OCR path for scanned pages; region OCR remains the supported selection action when no searchable text is available.
- Support touch and stylus drag selection on PDF text. Selection mode locks page zoom/pan, ink input, page changes, and annotation gestures until the user saves or cancels.
- Source-PDF review records belong in `ai-reviews.json`; normal visual annotations remain in `annotations.json`.
- Markdown note selection keeps its existing behavior. Office selection is out of scope.

## Interaction

1. The user enters PDF text-selection mode from the AI annotations menu.
   The current page is extracted on demand; changing pages alone does not parse the PDF text layer.
2. A one-finger or stylus drag selects the ordered text range between the nearest start/end glyphs. The selected glyphs are highlighted over the rendered PDF. A tap or empty range does not create a draft.
3. The user chooses the existing selected-text review command, reviews the exact quote, and enters an instruction. If the current note page is dirty, it must save against its captured revision before the review draft opens.
4. Save creates an open page-scoped document selection review. A note revision change after selection capture rejects the save and preserves the project file.
5. If the PDF page has no selectable text, show a clear message and leave region OCR available; do not create fake character anchors from OCR rectangles.

## Data Contract

- `side="document"`, `kind="selection"`, `scope="page"`, and the one-based PDF page number.
- `context` is the complete text extracted for that PDF page. `revision` is the lowercase SHA-256 of the context's UTF-8 bytes.
- `quote` is the exact non-whitespace substring represented by the selected ordered glyph range; existing review limits apply (quote <= 100,000 UTF-16 units, context <= 1,000,000 UTF-16 units).
- `anchor` uses the desktop-compatible `coordinateSystem="width-1000"`, a `shape.type="rect"` bounding the selected glyphs, and `text=quote`. Its rectangle scales both page axes against the displayed page width. Do not write PDFBox offsets as desktop `textStart`/`textEnd`, because those offsets are not PDF.js text-layer offsets.
- Add optional top-level `targetNoteRevision` to document selection reviews. It captures the note page revision when selection is confirmed, allowing same-run Agent writes to mark the source review applied without confusing PDF text revision with note revision. Note-side reviews retain their existing `revision` authorization behavior.
- Existing readers preserve this optional field as an unknown JSON field; no project manifest/schema version change is required.

## Acceptance

- On API 34, a searchable PDF page produces ordered text with usable glyph bounds from the local snapshot; the original PDF displayed by `PdfRenderer` is unchanged.
- Dragging in either direction selects the same logical text range and draws highlights at the corresponding page coordinates after viewport zoom/pan transforms.
- Saving a selection review persists its quote, full-page context, source revision, width-1000 shape anchor, and target note revision. A changed note revision prevents the write.
- Desktop paints a stored width-1000 shape anchor when a document selection quote cannot be matched in the current PDF.js text layer.
- App-process note writes are serialized with selection-review revision validation and persistence for the same note URI. Other apps/providers cannot join this lock; SAF does not expose a generic cross-document compare-and-swap.
- Agent `applied` authorization for a document-side review matches a changed note write against `targetNoteRevision`; legacy note-side authorization remains unchanged.
- Scanned pages without text remain on the existing OCR-region flow and never claim character-level selection.

## Verification

- One JVM test covers ordered/reversed selection, exact quote boundaries, and width-1000 bounding-rectangle mapping.
- One API 34 tablet instrumentation workflow uses the real reader and local PDF snapshot to select text under zoom/pan, save the document review after a dirty-note save, compare persisted anchor coordinates, verify rotated/cropped-page extraction, serialize a concurrent note write, and reject stale selection without changing the review file.
- Run affected JVM tests and `:app:assembleDebug`; run the focused desktop AI-workspace test for anchor fallback. Do not run the full instrumentation suite or any other device/API matrix. Xiaomi Pad 5 Pro remains the only hardware target.

## Verification Record

- `Pixel_Tablet_API34(AVD) - 14`: the focused end-to-end instrumentation workflow passes 1/1, including a 90-degree rotated/cropped PDF page and a concurrent app-process note write paused across review creation. This is Android 14 platform verification only, not Xiaomi/MIUI/Snapdragon hardware acceptance; the single real-device workflow remains in SPEC task 12.
- `node tests/ai-workspace.mjs`: desktop fallback highlights an Android-style width-1000 shape when its quote is absent from the current PDF text layer.
- Focused core-reader selection, app viewport mapping, and Agent review-authorization JVM tests pass; `:app:assembleDebug` succeeds.

## Verification Limits

- PDFBox text order can differ from PDF.js for unusual multi-column, rotated, or malformed text. The saved quote/context are internally consistent; desktop may use the shape anchor if its text layer cannot match the quote.
- The note URI lock coordinates this Android app process only. A simultaneous writer in another app remains outside the generic SAF lock/CAS contract.
- Scanned-only PDFs require OCR region selection; per-character selection is not promised without an extractable text layer.
