# Android PDF vertical continuous scrolling

**Status:** approved for implementation
**Date:** 2026-11-13
**Platform:** Android

## Intent

Let readers move naturally through adjacent PDF pages by dragging or flinging vertically. Keep page-specific notes, ink, annotations, page navigation, and reading checkpoints aligned with the page currently in view. The selected behavior is continuous scrolling by default, with the existing single-page mode available as a setting.

## Reference findings

The reverse-engineered reference source stores `continousPages` in `NoteUserData` (default `true`) and exposes a setting that persists changes and rerenders open panels. `PanelManager.isContinuous()` reads that preference. `PanelGlView` and `PanelManager` lay out adjacent pages in a shared viewport and update the active page as the viewport moves; the reference does not implement this as a `ViewPager` page-at-a-time gesture.

Android currently composes one `PdfPageViewport` for `pageIndex`. Vertical touch swipes call `changePage`, which saves the current note and resets the viewport bookmark. Continuous mode therefore needs its own scrolling path; it must not call the one-page transition for every visible page or reset scroll/zoom as the list moves.

## Goals

- Make continuous vertical scrolling the default for PDF projects and allow users to restore the existing single-page behavior from Reader Settings.
- Keep the toolbar page number and the page shown in the notes pane synchronized to the page nearest the viewport center.
- Render visible and nearby pages lazily, with bounded bitmap retention, and keep each page's existing annotation and ink surface associated with its page index.
- Preserve the current pinch zoom behavior across pages. Scrolling itself must not reset the viewport or wait for a page-save operation.
- Make previous/next buttons and page-jump commands bring their target page into view in continuous mode.
- Save enough reading state to reopen at the active page. Single-page mode retains its existing page transition and fit-to-page behavior.

## Non-goals

- Change Markdown page navigation or desktop PDF reading.
- Change the PDF or annotation file format.
- Rebuild the reference app's OpenGL canvas or reproduce cross-page drawing as one stroke. Each Android page remains an independent annotation surface.
- Add horizontal continuous reading, facing-page spreads, or per-document mode preferences in this milestone.

## Approaches

1. **Always-continuous native list:** replace single-page reading with a vertical lazy list. This is simple, but removes the existing single-page interaction.
2. **Selectable mode with native lazy list (chosen):** add a globally persisted `ReaderSettings` preference, defaulting to continuous; use a vertical lazy list in continuous mode and retain the current viewport path when disabled. This matches the reference's default and toggle while keeping Android on its native Compose/PdfRenderer stack.
3. **Port the reference shared-canvas renderer:** reproduce its OpenGL page layout, transforms, and page iteration. This offers closer internal parity but replaces the Android PDF rendering path and expands the change into a handwriting-kernel rewrite unrelated to the requested reading gesture.

## Interaction and state

- A touch drag or fling in the PDF area scrolls through multiple pages without snapping after each gesture. Stylus drawing continues to target the page canvas under the stylus; multi-touch zoom remains available.
- Use stable page indices and lazy page composition. Current-page tracking follows the page closest to the viewport center, not the page that happened to start a gesture.
- When the active page changes, update the toolbar, note pane, page-scoped OCR/selection state, and checkpoint without resetting the list position. Preserve dirty note drafts and persist outgoing edits asynchronously or through the existing save queue; scrolling must remain responsive and must not emit a save notice for each page crossed.
- In single-page mode, keep the existing swipe thresholds, page boundary behavior, note-save gate, and fit-to-page reset.
- Store the mode in the app-wide `ReaderSettingsStore`, mirroring the reference's user-level preference. Restore continuous reading at the last active page using the existing checkpoint.

## Acceptance cases

- **PDF-CONT-01:** continuous mode is enabled on first use; a vertical drag/fling moves through at least three pages without page-by-page snapping.
- **PDF-CONT-02:** the displayed page number and note page follow the page nearest the viewport center while scrolling, including reverse scrolling and document boundaries.
- **PDF-CONT-03:** visible pages render lazily; adjacent-page scrolling does not clear page ink/annotations or apply one page's edits to another.
- **PDF-CONT-04:** stylus ink, erasing, selection, and pinch zoom continue to work on the page under interaction; scrolling does not reset zoom or block on note persistence.
- **PDF-CONT-05:** previous/next and page jump navigate to the requested page; reopening a project restores the last active page.
- **PDF-CONT-06:** disabling continuous mode restores the existing one-page swipe behavior and fit-to-page transition.

## Constraints and implementation calls

- Use Jetpack Compose's lazy vertical list and the existing `PdfDocumentSession`; do not add a PDF library.
- Keep page bitmaps bounded and serialized through the existing synchronized `PdfRenderer` session.
- The exact Compose decomposition and cache policy remain implementation choices, provided memory stays bounded and current-page synchronization is reliable.
- UI PR evidence must include real horizontal tablet screenshots of continuous scrolling, the active-page/notes synchronization, and the single-page setting, with Android runtime commit and AVD details.
