# Dwell Shape Recognition

## Scope

Convert a deliberate freehand pen stroke into an existing native geometric annotation when the user holds the stylus still at the end of the stroke. Keep the project annotation schema unchanged.

## Interaction and Recognition

- Recognition applies to the Pen tool, not highlighter, eraser, or selection gestures.
- After a candidate stroke has moved, keeping the stylus contact within a small stationary radius at its endpoint for 550 ms triggers shape analysis and a live geometric preview.
- Lifting before the dwell timeout commits the original freehand stroke. Moving again after preview cancels recognition and resumes freehand input.
- Recognize only shapes already supported by the project format and native renderer: line, arrow, rectangle, and ellipse. Ambiguous or low-confidence paths remain freehand.
- A recognized shape is committed as one native vector item in the existing `annotations.json` page array. It uses the existing `line`, `arrow`, `rect`, or `ellipse` schema, preserves color and width, omits freehand pressure samples, and uses the native annotation page history. The original freehand candidate is never separately appended, so reload cannot duplicate it. No background model/API is used.

## Acceptance (EARS)

- When a clear supported shape ends with a stationary 550 ms dwell, the preview and committed item shall use its native geometry type.
- When the stylus lifts before the dwell or the path is ambiguous, the item shall remain freehand.
- When the user resumes movement after preview, the preview shall be discarded and the final freehand path retained.
- When a shape is committed, one undo shall remove the native vector item and redo shall restore it through the existing annotation page history.

## Verification

- Pure JVM tests cover line/arrow/rectangle/ellipse recognition, noisy paths, short strokes, ambiguous paths, and conversion to the native vector schema without pressure samples.
- One focused native View instrumentation test covers dwell, lift-before-timeout, movement-after-preview, and cancel behavior; one reader-flow test verifies native persistence and page-history undo/redo on the API 34 AVD.
