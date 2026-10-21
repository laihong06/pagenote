# PDF Rich HTML Annotation Compatibility

## Scope

Port the desktop PDF annotation workflow for rich HTML content to Android 14 and later. HTML annotations remain ordinary `type: "html"` entries in the existing `annotations.json`; no project-format migration or new server is introduced.

The Android reader shall render existing HTML annotation data, allow selection/move/resize/copy/paste/delete and page-scoped undo/redo, and accept HTML copied from the system clipboard while PDF annotation mode is active. Desktop clipboard objects retain the `PAGE_NOTES_ANNOTATION:\n` marker and their current geometry/data fields.

## Requirements

- When a PDF page contains a valid `type: "html"` annotation, Android shall display its sanitized rich content inside the annotation's page-coordinate bounds and keep it aligned with PDF zoom and pan.
- When Select is active and the user taps an HTML annotation, Android shall select its bounds; move, resize, delete, copy, undo and redo shall use the same page-scoped recoverable annotation writer as native vector annotations.
- When the clipboard contains a desktop-marked HTML annotation, Android shall validate it, sanitize its HTML, translate its first point to the canonical paste inset, preserve supported metadata, and append it to the current writable PDF page.
- When the user explicitly pastes HTML from the system clipboard in PDF annotation mode, Android shall sanitize and measure the content locally, create a desktop-compatible HTML annotation, and save it through the existing recovery-aware write path.
- While rendering or sanitizing HTML, the packaged WebView shall load only app-owned renderer assets, block network requests and active navigation, and never enable access to local files or content-provider data from HTML.
- When HTML exceeds the desktop schema limit or its rasterization would exceed the Android bitmap budget, Android shall avoid allocation/write, preserve existing page data, and show a localized failure or placeholder state.
- Sanitization shall remove scripts, event-handler attributes, unsafe URL schemes and other active content while preserving the safe formatting accepted by the bundled DOMPurify version. Persisted HTML and copied clipboard payloads shall contain the sanitized form.
- Rendering shall remain offline. External images/resources are blocked; only bounded inline data images may render.
- PDF annotation bytes and page note data shall remain unchanged when rendering alone occurs. Annotation mutations shall preserve sibling items, unknown JSON fields, pen-stroke ownership and undo history separation.

## Desktop Reference

- `src/main.js` reads `text/html` from the clipboard in PDF annotation mode and delegates to `annotations.addRich`; marker payloads with `type: "html"` use `annotations.pasteObject`.
- `src/annotations.js` sanitizes with DOMPurify, stores `color`, `width`, two page-coordinate points and HTML, renders through an SVG `foreignObject`, and selects/moves/resizes using its bounding rectangle.
- `src/desktop.css` styles `.ink-rich-content` at 22 px with 1.6 line height and 8 px padding; h1/h2/h3 and tables use the existing desktop sizes.
- Android already bundles the same DOMPurify asset used by its Mermaid renderer. Reuse that pinned APK asset; do not fetch a library at runtime.

## Limits and Security

- Maximum HTML input and sanitized output: 100,000 UTF-16 code units, matching `AnnotationValidation` and desktop storage.
- Maximum raster side: 16,384 physical pixels; maximum aggregate HTML/image raster allocation for one annotation page: 4,194,304 pixels.
- Run the WebView with JavaScript enabled only for the fixed local sanitizer/renderer entry point, strict CSP, `blockNetworkLoads`, disabled file/content access, no JS bridge, and an allowlist limited to packaged assets. Clipboard HTML is data, never a navigation URL.
- A failed/oversized render does not invalidate or rewrite the source annotation. It displays a bounded local placeholder and keeps the original item available for selection, copy, deletion or recovery.

## Acceptance (EARS)

- When an existing desktop project contains safe rich HTML on a PDF page, Android shall render the content offline and preserve its data through project open/close.
- When a valid desktop marker contains a rich HTML item, Android shall paste, move, resize, copy, delete, undo and redo it without changing neighboring annotations or pen data.
- When the system clipboard contains HTML with script/event/remote-resource payloads, Android shall store only sanitized markup, render no active content, and make no network request.
- When HTML is malformed, unsupported or above the resource limits, Android shall show a failure/placeholder without claiming a successful write or corrupting the annotation page.
- When the page changes or the project closes, Android shall release HTML raster bitmaps using the existing delayed-recycle rule and discard stale render results.

## Verification

- Focused JVM tests cover HTML type parsing, validation, marker round-trip, sanitization result policy, geometry hit-testing, translation, resize, and size rejection.
- One Android 14/API 34 tablet AVD flow covers local HTML clipboard paste, desktop marker paste, rendered pixels, selection/move/delete/undo, malicious markup rejection and offline request blocking.
- Run the affected JVM tests, the focused API 34 AVD flow, Android test compilation, `:app:assembleDebug`, and `git diff --check`. No other API/OEM matrix is required.

## Related

- Parent PDF annotation contract: `pdf-annotation-selection-clipboard.md`.
- Desktop parity contract: `desktop-ui-and-office-parity.md`.
- Implementation breakdown: `../pdf-rich-html-annotation-plan.md`.
