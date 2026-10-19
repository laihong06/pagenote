# PDF Annotation Overlay

## Scope

Render saved document-side rectangle and text annotations over the native PDF page. This slice also aligns tap-created annotation coordinates with the desktop annotation layer. It does not change note-side Markdown annotations or create new annotation types.

## Contract

- Annotation points use the desktop SVG page coordinate system: the page width is 1000 units and both axes use the same scale. On portrait pages, a valid `y` may therefore be greater than 1000.
- The Android app reads the current document page from `annotations.json` and renders `rect` and `text` items in the same clipped, zoomed, and panned layer as the PDF and pen strokes.
- If the project was opened before `annotations.json` existed, a later page reload locates the file in the project's `src/` directory instead of treating the page as empty.
- Rectangles use the first two points, normalized with min/max so reverse-ordered points still render. Text uses the first point as its baseline anchor and retains its stored color, width, and line breaks.
- The overlay refreshes when the project/page changes and after the current page's annotations are created or deleted. It never rewrites unsupported annotation entries.
- Malformed or unsupported overlay items are skipped without preventing the PDF page from rendering. Pen/highlight remain rendered by the existing native pen canvas; line, arrow, ellipse, image, and HTML overlays are deferred.

## Acceptance (EARS)

- When a PDF page contains a valid `rect` annotation, the Android reader shall draw its outline at the same page-relative position as the desktop annotation layer.
- When a PDF page contains a valid `text` annotation, the Android reader shall draw the text at its stored anchor with its color and line breaks.
- When the user zooms or pans the PDF, the annotation overlay shall remain aligned with the page and be clipped to the viewport.
- When an annotation is malformed or uses an unsupported shape, the reader shall skip that overlay item without crashing or modifying the source data.
- When an annotation is removed from the current page, the reader shall remove it from the visible overlay after the save completes.
- When the current page has no cached annotations URI but `src/annotations.json` exists, the reader shall resolve and read that file.

## Verification

- JVM: mapper tests cover rectangle/text geometry, desktop width-based coordinates, malformed entries, and unsupported types.
- API 34 tablet AVD: one Compose pixel test verifies width-based rectangle geometry; one focused SAF test verifies reads after `annotations.json` is created post-open.
- Build: compile Android test Kotlin and assemble the Debug APK. No other device/API matrix is in scope; Xiaomi Pad 5 Pro physical acceptance remains task 12.

## Deferred

- Drag-to-create region rectangles and line/arrow/ellipse/image/HTML rendering are separate Android SPEC slices. The current region-entry default-size behavior is unchanged here.
