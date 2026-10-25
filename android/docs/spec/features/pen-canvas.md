# Android Pen Canvas

## Scope

Add an isolated native Android `View` drawing surface to `feature-pen`. It owns
only rendering and pointer input. It does not own persistence, page selection,
undo/redo, project access, or changes to the `annotations.json` contract.

## Geometry and Data

- The canvas consumes existing `PenStroke` values and draws freehand `pen` and
  `highlight` items, plus the native geometric forms supported by this surface:
  `line`, `arrow`, `rect`, and `ellipse`. Text/HTML/image annotations remain
  owned by their corresponding reader layers.
- Coordinates preserve the desktop annotation transform: `x` and `y` both use
  `1000 / canvasWidth` document units per view pixel. The document width is
  therefore 1000 units and the document height follows the view aspect ratio;
  portrait-page Y values may exceed 1000.
- The pure input mapper converts local view coordinates into this document
  space, clamps them to the canvas, and maps finite positive pressure into
  `0..1`. Missing, non-finite, or non-positive pressure uses the existing
  `PenValidation.DEFAULT_PRESSURE` value.
- A completed pen/highlighter gesture reports one validated `PenStroke` to a
  callback. Erasing reports the index of the topmost intersecting existing
  stroke through a separate callback; it never creates an `eraser` item.

## Input Behavior

- `PenCanvasView` accepts finger and stylus contact, with pen, highlighter, and
  eraser modes. Drawing samples are normalized at input time and stored in each
  returned `PenStroke.points` list.
- The active pointer is selected on `ACTION_DOWN`. Other pointers are ignored;
  the view never switches to a second pointer mid-gesture.
- Android stylus eraser tool type overrides the selected mode. A stylus
  secondary-button state also requests erasing where the device reports it.
- `ACTION_CANCEL` discards the in-progress gesture without a callback.
- Unsupported tool types are ignored. Platform stylus palm rejection is
  respected, and additional pointer contacts never become ink.
- Drawing preserves round joins/caps. Pressure scales segment width. Each
  highlighter gesture is one translucent coat (89/255 opacity): first render its
  pressure-aware segments opaquely into an isolated, viewport-bounded layer,
  then composite that layer once. Retracing, joins and self-intersections within
  one gesture must not accumulate opacity. Separate gestures may overlap.
  Live preview and loaded strokes use the same rendering path; single-point
  highlights remain translucent. No stored stroke format changes are needed.
  Eraser hit-testing uses the rendered stroke geometry
  with a configurable view-pixel radius.
- `PenPageStore` remains the sole owner of page-scoped mutations and undo/redo;
  the caller applies completed-stroke and erase callbacks to that store.

## Acceptance

- A canvas sized with a valid width maps its left/right bounds to X 0/1000 and
  applies the same scale to Y, preserving page aspect ratio.
- Invalid dimensions do not produce points; pressure fallback and clamping are
  deterministic in JVM tests.
- Existing freehand strokes render with their stored color, width, type,
  geometry, and per-point pressure; geometric annotation types supported above
  render without changing their stored data.
- Pen/highlighter gestures emit a `PenStroke` with normalized points only on
  normal completion. Cancellation emits nothing.
- Eraser tool type and stylus secondary-button input request erasure without
  emitting an annotation item. Extra pointers do not add samples.
- Existing `PenPageStore` behavior and `annotations.json` serialization remain
  unchanged.

## Android Instrumentation Acceptance

- A native PenCanvasView instrumentation test dispatches synthetic stylus
  down, move, and up events through the View. It verifies normalized page
  coordinates, retained non-default pressure samples, and one completed
  stroke callback.
- Cancellation and disabled input discard the in-progress gesture without
  writing a stroke.
- Stylus button press and release are tested through generic-motion dispatch,
  which is Android's event route for these actions. Erasing recognizes both
  the stylus-secondary button and the platform secondary-button mapping used
  for BTN_STYLUS.
- Pixel-based native canvas regressions cover retracing, pressure-varying
  intersections, live/reloaded ink, separate strokes, ordinary opaque pen ink,
  single dots and clipped/zoomed highlights on the Android 14 tablet AVD.
- Synthetic events verify application logic only. They do not replace
  physical-device checks for pressure fidelity, palm rejection, latency, or
  hardware-specific button reporting.

## Verification

- Highlighter regression (2026-10-25): the old renderer failed four of five
  focused pixel tests, accumulating alpha 147/185 instead of 89. Per-gesture
  composition passes all five checks and the nine existing native canvas tests
  on `Pixel_Tablet_API34`. These pixel checks use a bitmap-backed Android Canvas;
  no physical-device latency or GPU performance acceptance is claimed.
- The 50 `feature-pen` JVM tests pass on a forced rerun. Debug APK and native
  test APK build successfully offline. The app is installed with `adb install -r`
  on that AVD, without clearing app data; no physical tablet is connected.
- JVM tests cover coordinate normalization, aspect-ratio-preserving Y scale,
  pressure fallback/clamping, and geometry hit-testing.
- Android compilation verifies the native `View`, `Canvas`, and `MotionEvent`
  implementation.
- Physical stylus acceptance is required for latency, pressure fidelity,
  secondary-button reporting, hardware eraser tool type, and device-specific
  palm rejection.
