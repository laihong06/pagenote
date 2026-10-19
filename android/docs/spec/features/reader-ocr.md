# Reader OCR Action

## Scope

Connect the bundled offline OCR engine to the PDF reader through an explicit current-page action. Keep this Android integration SPEC under `android/docs/spec/`.

The action renders a separate bitmap from the active PDF page, runs the reusable on-device engine away from the UI thread, and presents recognized text, per-block confidence, normalized positions, and warnings. The reader's displayed bitmap remains owned by the PDF view and is never passed to OCR or recycled by it.

The full-page OCR action remains explicit. Page Tutor may run lazy current-page OCR only after the user starts that workflow. When Page Tutor starts from a successful region OCR result, it reuses the selected region text and must not run full-page OCR again. This spec does not merge OCR and native text or define persistent prepared-page caches; region selection and caching are specified separately in `reader-ocr-selection.md`.

## Behavior

- OCR is started only by an explicit user action for the current PDF page.
- Repeated OCR requests are disabled while one is active; page navigation and project switching are also disabled during inference.
- Model initialization is lazy and reused for later pages in the activity.
- OCR returns through the established `OcrResult` contract. Failed results and low-confidence results remain visible with warnings.
- Each result is associated with the PDF page that was actually recognized.
- OCR input bitmap and model resources are released after work or when the reader is disposed.

## Acceptance

- The user can run OCR on a PDF page without a network request.
- Text, confidence, normalized boxes, and warnings are inspectable after recognition.
- Low-confidence blocks produce a visual-review warning.
- An OCR failure does not alter or replace the visible PDF page and is not shown as successful recognition.
- A result is not accidentally displayed for a different page.
- Page navigation and project switching remain unavailable until the current OCR call completes.

## Verification

- Existing OCR policy and mapper unit tests cover failures, confidence, and normalized boxes.
- App compilation verifies the reader action integration.
- A strict API 35 / 16 KB AVD run disables airplane mode exceptions, Wi-Fi, and mobile data, confirms no active default network, then recognizes the bundled Chinese/English fixture with the installed instrumentation APK. This verifies engine availability offline; it does not replace physical-device reader interaction or real-project acceptance.
