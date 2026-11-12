# Dwell Shape Recognition

## Scope

Convert a deliberate freehand pen stroke into an existing native geometric annotation when the user holds the stylus still at the end of the stroke. Keep the project annotation schema unchanged.

## Interaction and Recognition

- Recognition applies to the Pen tool, not highlighter, eraser, or selection gestures.
- After a candidate stroke has moved, keeping the stylus contact within a small stationary radius at its endpoint for 400 ms triggers shape analysis and a live preview, matching `TouchEventManager.TIME_PAINT_SHAPE_AUTO`.
- Lifting before the dwell timeout commits the original freehand stroke. Moving again after preview cancels recognition and resumes freehand input.
- Follow the reference `GraphUtils.recogniseShape` branch order: open polyline, line/arrow, curve, then closed contour. Ambiguous paths remain freehand. The reference's per-branch screen-space thresholds decide whether a candidate is eligible; do not add a generic document-space path-length gate.
- Line, arrow, rectangle and ellipse use their existing native annotation types. Open polylines, sampled quadratic curves and closed paths use multi-point `pen` items with `pagenotePolyline` or `pagenoteCurve` metadata, preserving the existing project format and pressure-free vector path. The source candidate is committed once and is not duplicated as freehand ink. No background model/API is used.

## Acceptance (EARS)

- When a supported candidate ends with a stationary 400 ms dwell, recognition shall use the matching reference branch and commit exactly one compatible annotation or multi-point pen item.
- When a short line has an endpoint distance above the reference `isLine` threshold and meets `createLine`'s minimum, no generic document-space threshold shall prevent recognition (`REF-SHAPE-05`).
- When the stylus lifts before the dwell or the path is ambiguous, the item shall remain freehand.
- When the user resumes movement after preview, the preview shall be discarded and the final freehand path retained.
- When a shape is committed, one undo shall remove the native vector item and redo shall restore it through the existing annotation page history.

## Verification

- Pure JVM tests cover open/closed recognition branches, noisy paths, short and ambiguous strokes, and compatible serialization.
- Native View instrumentation covers dwell, lift-before-timeout, movement-after-preview, cancel behavior and the short-line threshold. Reader-flow tests verify the short line persists as `line` and one undo removes it on the Android 14/API 34 landscape tablet AVD.
