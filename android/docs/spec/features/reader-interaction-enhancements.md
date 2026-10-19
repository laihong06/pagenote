# Android Reader Interaction Enhancements

**Status:** Acceptance contract for task 50. Existing task 39-42 implementations are a baseline to revalidate, not evidence that this contract currently passes.

## Scope

Keep the six requested Android reading and pen interactions available in the single Android 14+ app. Preserve the pagenote project format, page-scoped notes and annotations, pressure data, and shared page-change behavior. Do not change desktop implementations or add an Android-version feature split.

## User Requirements

1. A user reading a PDF can move to the next or previous page with a deliberate vertical touch swipe rather than relying on a page button.
2. The reader header places previous-page and next-page controls on either side of the page-jump control at the upper right.
3. Stylus ink remains aligned with the visible page after pinch zoom and pan, including after persistence and reload.
4. Reader Settings has a persisted option for writing in visible margins outside the PDF page. It is off by default; enabling it affects new strokes only.
5. Reader Settings configures automatic local preparation of a forward page window. A value of 3 prepares pages 1-3 when page 1 opens; after moving to page 2 it reuses pages 2-3 and prepares page 4. Zero disables the queue.
6. Holding the stylus at the end of a deliberate pen stroke for 550 ms previews and converts a confidently recognized supported shape. Lifting early or continuing to move preserves freehand ink.

## Acceptance (EARS)

- When a single-finger vertical PDF swipe meets the documented distance/direction threshold, the reader shall change exactly one page through the common note-save and viewport-reset path. Stylus, multi-touch, selection, annotation, modal, and blocked-save interactions shall not accidentally change pages.
- When the reader displays a PDF page, the upper-right header shall show previous-page, page-jump, and next-page controls in that order. Boundary controls shall be disabled and accessible descriptions shall be localized.
- When the user writes with a stylus on a zoomed or panned page, the saved document coordinates shall map back to the entered screen path. Existing pressure and undo/redo behavior shall remain intact.
- When outside-page ink is disabled, new points shall clamp to the visible page bounds. When enabled, points in the visible viewport margins shall remain attached to the current page and render at the same positions after reload.
- When automatic preparation is enabled, the reader shall process the current page and the configured forward window sequentially off the UI thread, reuse completed page/source-revision cache entries, and cancel obsolete work on page, project, or revision changes. It shall use native text first and invoke only the existing offline OCR fallback policy; the queue shall not call a cloud model.
- When a supported shape reaches a stationary endpoint dwell of 550 ms, the pen canvas shall preview it and commit one native vector annotation with the active color/width. An early lift, ambiguous path, highlighter, eraser, selection, or movement after preview shall not silently replace the freehand path.

## Verification Boundary

- Routine instrumentation target is one API 34 tablet AVD. Simulator input can substitute for hardware acceptance; no OEM/API matrix is required.
- Pure policies, settings persistence, page preparation scheduling, coordinate transforms, shape recognition, and native-vector conversion require focused JVM tests.
- User-visible navigation/settings and the complete recognized-shape save/undo/redo path require focused Android instrumentation. A component-only callback test does not prove the reader workflow.
- Compile affected Android test sources and build `:app:assembleDebug` after each complete feature slice. Update the root Unreleased changelog, commit, and push each slice to the existing Android feature branch.

## Related Contracts

- Page navigation: `reader-page-navigation.md` and `page-jump.md`.
- Stylus transforms and extended canvas: `zoomed-ink-and-extended-canvas.md`.
- Automatic preparation: `page-prefetch.md`.
- Dwell shape recognition: `dwell-shape-recognition.md`.
- Execution checklist: `../reader-interaction-acceptance-plan.md`.
