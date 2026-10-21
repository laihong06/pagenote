# Native PDF Text

## Goal

Extract selectable text and page-relative geometric blocks from Android PDF pages, and expose an explicit OCR fallback state when the platform text API is unavailable. Android 14 Page Tutor may use the PDF's existing text layer through PDFBox before OCR.

## Behavior

- On API 35 and later, read text with `PdfRenderer.Page.getTextContents()`.
- Keep every non-empty platform text item in page full text, including items whose bounds list is empty (unbounded text).
- Emit one geometric block per text item with usable bounds. The block carries the text item once; when the platform provides multiple line rectangles, use their clipped union as the block bounds.
- Treat bounds as PDF points. Clip each rectangle to the page, omit non-finite, zero-area, inverted, or wholly out-of-page rectangles, and omit a block when no valid area remains.
- Normalize left, top, width, and height independently against the page width and height into the existing 0..1000 coordinate space. This must work for non-square pages.
- On API 34 (the Android 14 minimum), return an unavailable result explicitly marked for fallback. Do not report native extraction as successful and do not attempt `getTextContents()`.
- For Page Tutor on API 34, use `PdfDocumentSession.extractSelectableText()` from the local PDF snapshot when it contains selectable text. This is a PDFBox text-layer fallback, not platform-native extraction or OCR; use offline OCR when neither source has usable text or the sparse-image trigger applies.
- The same cached PDFBox page inspection reports raster image XObjects, including images nested in form XObjects. Page Tutor uses the existing sparse-text OCR trigger for mixed pages and retains both text sources when OCR supplements a nonempty text layer.
- Close each opened `PdfRenderer.Page` on success and failure. Keep the current 16-megapixel rendering cap unchanged.

## Boundary

`core-reader` owns platform-independent text result types, bounds normalization, clipping, and API support policy. `PdfDocumentSession` owns page access and calls Android text APIs only inside the API-35 guarded path; its existing PDFBox snapshot extraction supplies selectable text on API 34. Page Tutor owns the platform-text, PDFBox-text, then OCR source order.

## Verification

- Existing JVM tests cover bounds normalization, clipping, text edge cases, and unsupported API fallback policy. The Page Tutor source-policy test covers PDFBox-before-OCR selection.
- One focused API 34 tablet AVD flow verifies searchable text avoids OCR and sparse mixed text/image content retains both sources after OCR; build the Debug APK. Do not expand this slice into API/device matrices. The AVD does not count as Xiaomi Pad 5 Pro hardware acceptance.
