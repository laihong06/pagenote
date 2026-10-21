# PDF Vector Annotation Overlay

## Scope

Render imported document-side `line`, `arrow`, and `ellipse` annotations on Android PDF pages. This is a read-only rendering slice; it does not add annotation authoring controls or rewrite saved items.

## Contract

- Coordinates follow the desktop annotation SVG: `1000` units per page width on both axes; portrait-page Y values may exceed `1000`.
- `line` and `arrow` use the first two points in order; if only one point exists, the desktop renderer reuses it as the second point. Their stroke uses the saved color/width and round caps/joins.
- `arrow` draws the saved shaft plus two head segments at the second point. Head length is `14 + width * 2` SVG units and its two wings use angles `direction +/- 0.45` radians, matching `src/annotations.js`.
- `ellipse` treats the first two points as opposite bounds (or reuses the first point when it is alone) and uses their midpoint and absolute half-differences as center/radii, independent of drag direction.
- The vector overlay shares the PDF page's clip, zoom, and pan transform. Invalid supported-shape records and still-unsupported shapes are skipped without modifying `annotations.json`.
- Vector coordinates must be finite and within `+/-1,000,000` page-width units; out-of-range records are treated as malformed and skipped before conversion to Canvas pixels.

## Acceptance (EARS)

- When a PDF page contains a valid `line` annotation, the reader shall draw a stroke between its first two points using the stored color and width.
- When a line, arrow, or ellipse contains only one point, the reader shall match the desktop fallback by treating that point as both endpoints.
- When a PDF page contains a valid `arrow` annotation, the reader shall draw the shaft and desktop-compatible arrow head at its second point.
- When a PDF page contains a valid `ellipse` annotation, the reader shall draw an outline bounded by its first two points, including reverse-ordered points.
- When the user zooms or pans a PDF, vector annotations shall remain aligned with the PDF and existing rectangle/text overlays.
- When a vector annotation is malformed or unsupported, the reader shall skip it without crashing or rewriting the saved annotation data.

## Verification

- JVM: mapper tests cover all three vector types, one-point fallback, point ordering, portrait Y values, malformed geometry, and deferred types.
- API 34 tablet AVD: one Compose pixel test draws line, arrow, and ellipse together and checks representative pixels for each primitive, including the arrow tip and both wings.
- Build: compile Android test Kotlin and assemble Debug app/test APKs. Run only the API 34 tablet AVD; Xiaomi Pad 5 Pro hardware remains the final environment check under task 12.

## Deferred

- Creating/editing these shapes from Android tools, drag-to-create region annotations, and image/HTML rendering remain separate slices. Existing stored annotation data remains unchanged.
