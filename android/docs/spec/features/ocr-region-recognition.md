# OCR Region Recognition

## Scope

Extend the bundled offline OCR engine to recognize a selected rectangle from a page bitmap while returning detections in the original full-page 0..1000 coordinate space. This slice defines the reusable engine API; selection UI, automatic triggers, and persistent prepared-page caching remain separate reader integration work.

## Behavior

- A caller supplies the original page bitmap and a positive rectangle in normalized page coordinates (0..1000).
- The engine converts the rectangle to a clipped pixel crop that covers the requested area. Rectangles with no area or no intersection with the page fail explicitly.
- OCR runs on the crop using the existing bundled PP-OCRv6 engine. Each returned block is mapped from crop coordinates back to full-page normalized coordinates while preserving text and confidence.
- The original bitmap remains caller-owned, unmodified, and unrecycled on success, failure, and cancellation.
- A temporary crop bitmap created by the engine is recycled after inference, including exceptional exits. A full-page request must not recycle the caller bitmap.
- Engine status and warnings are preserved; recognition failure must not become a successful empty result.

## Acceptance

- Crop geometry uses deterministic floor/ceil rounding, clips to bitmap bounds, and rejects empty/outside regions.
- Detection bounds map back to the selected page rectangle with independent x/y dimensions and remain inside 0..1000.
- Confidence, engine id, warnings, and failure state survive region mapping.
- Source bitmap ownership is unchanged and temporary crop ownership is released on every exit path.

## Verification

- JVM tests cover normalized-to-pixel crop bounds and crop-to-page result mapping, including non-square images and edge clipping.
- Android module compilation verifies the bitmap crop and inference integration.
- Device acceptance recognizes a selected Chinese/English region without network access and visually checks the returned page-relative boxes.
