# Reader Page Prefetch

## Scope

Prepare the current PDF page and a configurable forward window for reading and Page Tutor context. Preparation is local and sequential; it does not generate notes, modify annotations, or send requests to a cloud model.

## Settings and Window Semantics

- Add `prepareAheadPages`, an integer setting from 0 through 10, defaulting to 3. Zero disables automatic preparation.
- The configured window includes the current page. At page 1 with a window of 3, prepare pages 1, 2, and 3. At page 2, prepare pages 2, 3, and 4; completed work and cache hits are reused, so only page 4 is newly prepared.
- Clamp the window at document boundaries. Markdown documents are not included in this PDF/OCR queue.

## Preparation and Lifetime

- Use native PDF text extraction first and invoke the existing offline OCR engine only when its existing `OcrTrigger` policy requires it.
- Persist successful full-page OCR results through the existing portable `prepared/` project cache and source revision rules. Reuse in-memory prepared page text for the current reader session.
- Run one page at a time off the UI thread. Page/project changes cancel the obsolete queue and schedule the new current-page window; already completed results remain reusable.
- A source revision change invalidates prepared text/cache reuse. OCR failures and cache-write failures remain explicit warnings and never replace or claim to recover the original PDF.
- User-initiated Page Tutor still makes its own explicit model request. Prefetch itself never reads model credentials or performs network access.

## Acceptance (EARS)

- When the setting is 3 and page 1 opens, the queue shall request pages 1-3 in order.
- When navigation reaches page 2, the queue shall reuse pages 2 and 3 and prepare page 4.
- When the setting is 0, no automatic preparation work shall start.
- When the project/page changes during preparation, obsolete work shall be cancelled and shall not be written under the new project or source revision.
- When valid prepared OCR cache data exists for the same source revision, the queue shall reuse it instead of rerunning OCR.

## Verification

- JVM tests cover window calculation, clamping, de-duplication, cancellation/revision identity, and cache reuse.
- One API 34 tablet AVD test covers opening at page 1 and advancing to page 2 with a three-page window. OCR model inference itself remains covered by existing offline OCR tests.
