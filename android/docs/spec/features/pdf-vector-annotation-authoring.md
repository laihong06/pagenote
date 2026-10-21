# PDF Vector Annotation Authoring

## Scope

Add desktop-style drag-to-create for PDF `line`, `arrow`, `rect`, and `ellipse` document annotations. Keep the existing tap-to-enter `text` and default-size `rect` dialog intact. This slice does not add selection/move/resize, color or width controls, image/HTML annotations, or freehand pen changes.
Newly created vector annotations can be removed through the existing current-page annotation list.

## Interaction

- The Android handwriting menu exposes active `line`, `arrow`, and `shape` tools. `shape` offers rectangle and ellipse choices; a separate select tool exits the active vector-draw mode.
- With a vector-draw tool active, one finger or stylus drag on the rendered PDF page creates a shape. Pointer-down is the first point and pointer-up is the second point; the active tool remains selected for repeated drawings.
- The drag displays a temporary shape preview. It is removed after a successful save or a rejected/failed draft; storage errors are shown through the existing reader status.
- During vector drawing, the viewport consumes the active pointer gesture and suspends pan/zoom, OCR selection, and pen/highlighter/eraser input. Selecting another tool restores its normal input behavior.
- The drag must start within the rendered page. The end point is clipped to the rendered page bounds, including when the page is zoomed or panned. A start in the letterbox margin is rejected.

## Data and Coordinates

- Convert both endpoints through the inverse `PdfViewportTransform` into desktop annotation coordinates: `x = pageX / pageWidth * 1000`, `y = pageY / pageWidth * 1000`. Both axes use page width, so portrait-page Y can exceed 1000. Do not use the OCR region's independent 0..1000 X/Y normalization.
- Preserve endpoint order for all saved shapes. `line` and `arrow` direction follows start-to-end; `rect` and `ellipse` render their two points as opposite corners.
- Persist items as `{type, color, width, points}` with default `color="#dc3545"`, `width=3.0`, and two ordered points. Do not add a text field to vector shapes.
- Reject non-finite/out-of-page starts and zero-length lines/arrows or zero-area rectangles/ellipses. Clamp only the drag end to the page bounds.
- Append to the current page's existing `annotations.json` item array using the existing serialized SAF write path, preserving all unrelated and unsupported entries. Refresh the page overlay after save.

## Acceptance (EARS)

- When a vector tool is active and a finger or stylus drags from the PDF page, the reader shall preview the selected shape and save it with the captured page's desktop-compatible endpoints after release.
- When a line or arrow is drawn, the reader shall preserve the start/end order so the arrow head appears at the release point.
- When a rectangle or ellipse is drawn in either direction, the reader shall render it using the two drag points as opposite bounds.
- When a drag begins outside the PDF page or produces zero geometry, the reader shall not call the SAF writer and shall restore input with a recoverable status.
- When a vector annotation is saved, the reader shall preserve existing page annotations and refresh the overlay without changing other pages.
- While vector drawing is active, the same pointer gesture shall not create pen strokes, change the viewport transform, or alter the OCR selection.

## Verification

- JVM: cover inverse viewport mapping under zoom/pan on a portrait page (including Y greater than 1000 and end clipping) and vector draft serialization/degenerate rejection.
- Android 14/API 34 tablet AVD: one focused Compose gesture test covers drag preview, completed endpoints, and rendered arrow pixels. Xiaomi Pad 5 Pro hardware validation remains task 12.
- Build: run only the affected JVM classes, compile Android test Kotlin, and assemble Debug app/test APKs; install/run only the selected API 34 tablet AVD. No other API, ABI, OEM, or form-factor matrix is required.
