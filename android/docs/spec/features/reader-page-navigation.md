# PDF Page Navigation Gestures

## Scope

Add vertical touch-swipe navigation and explicit previous/next controls to the Android PDF reader. Reuse the existing page-change workflow so page saves, note drafts, OCR state, and viewport reset retain their established behavior.

## Interaction

- An intentional single-finger vertical swipe up advances one PDF page; a swipe down returns one page.
- A swipe is accepted only when vertical movement is at least 72 dp and at least 1.35 times horizontal movement. Multi-pointer gestures, stylus input, note-pane scrolling, OCR/text selection, annotation drawing, and active modal flows do not navigate pages.
- Swipes at the first/last page are no-ops and do not wrap.
- Previous and next icon buttons sit beside the page-jump control in the reader header. They are disabled at the respective boundary or while a page transition is blocked, expose localized accessibility descriptions, and call the same `changePage` path as menu commands.
- A page change continues to save the current note before switching and resets the viewport to fit-to-page.

## Acceptance (EARS)

- When a qualifying upward/downward touch swipe occurs in the PDF viewport, the reader shall navigate exactly one page in the corresponding direction.
- When the gesture is stylus input, multi-touch, or below threshold, the reader shall not change pages.
- When the current page is the first/last page, the matching button and swipe shall not wrap.
- When a page change is accepted, the reader shall preserve existing note-save and page-scoped state behavior.

## Verification

- JVM tests cover gesture direction, threshold, diagonal rejection, and boundary policy.
- One API 34 tablet AVD reader test covers swipe navigation and adjacent toolbar buttons; no cross-version or OEM matrix is required.
