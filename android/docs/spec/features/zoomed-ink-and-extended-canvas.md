# Zoomed Ink and Extended Canvas

## Scope

Keep native stylus ink aligned with the PDF at every viewport zoom and optionally accept ink in the visible margins outside the PDF page. The existing portable `annotations.json` coordinate contract remains unchanged.

## Coordinate Contract

- PDF pixels, annotations, and ink share the page-width-based 1000-unit coordinate space already used by desktop projects. The Y coordinate is not height-normalized and may exceed 1000 on portrait pages.
- Pointer input is mapped from viewport coordinates through the inverse viewport transform before storage. Rendering, selection, and erasing map stored points through the forward transform.
- The ink surface covers the reader viewport and is clipped to that viewport; it is not clipped to the transformed PDF page rectangle.
- With `allowInkOutsidePage=false`, newly entered points are clamped to the PDF page bounds. Existing out-of-page points remain readable and are not rewritten.
- With `allowInkOutsidePage=true`, new points in visible margins are stored in the same page coordinate space, subject to the existing absolute-coordinate validation limit. The setting affects new input only and is persisted in app settings, not in the project.
- Both modes support stylus pressure and existing pen/highlighter/eraser/selection behavior. Page changes and viewport changes must not mutate stored coordinates.

## Settings

- Add a persisted reader setting `allowInkOutsidePage`, default `false`.
- Expose it as a localized switch in Reader Settings.

## Acceptance (EARS)

- When the PDF is zoomed or panned, a stylus stroke shall render beneath the same screen-space path used to enter it and remain aligned after save/reload.
- When outside-page ink is disabled, input beyond page edges shall clamp to the nearest page edge.
- When outside-page ink is enabled, input in the visible reader margin shall be retained and rendered while remaining associated with the current PDF page.
- When settings are saved and the app is reopened, the outside-page ink choice shall be restored.

## Verification

- JVM geometry tests cover forward/inverse transforms, zoom/pan, portrait-page Y coordinates, and optional boundary clamping.
- Existing codec/persistence tests verify the project format remains unchanged.
- One API 34 tablet AVD test covers zoomed stylus input and settings persistence; physical pressure behavior remains covered by the user's existing device check.
