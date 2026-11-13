# PDF Page Navigation Gestures

## Scope

Android PDF reading defaults to continuous vertical scrolling. Reader Settings can switch back to the original single-page mode. Explicit previous/next controls and page jumps remain available in both modes.

## Interaction

- In continuous mode, touch drag and fling scroll through the PDF without snapping at page boundaries. The page number and note pane follow the page nearest the PDF viewport center.
- In continuous mode, touch scrolls and pinches while stylus input writes to the page beneath the pen. Page ink and annotations remain associated with their original page.
- In single-page mode, an intentional single-finger vertical swipe up advances one page; a swipe down returns one page.
- A single-page swipe is accepted only when vertical movement is at least 72 dp and at least 1.35 times horizontal movement. Multi-pointer gestures, stylus input, note-pane scrolling, OCR/text selection, annotation drawing, and active modal flows do not navigate pages.
- Single-page swipes at the first/last page are no-ops and do not wrap.
- Previous and next icon buttons sit beside the page-jump control in the reader header. They are disabled at the respective boundary or while a page transition is blocked and expose localized accessibility descriptions; all explicit page navigation follows the selected reading mode.
- In continuous mode, previous/next and page-jump requests scroll to the requested page. Scrolling saves dirty outgoing notes asynchronously and does not show a success message for each page crossed.
- In single-page mode, a page change continues to save the current note before switching and resets the viewport to fit-to-page.
- The continuous/single-page preference is app-wide, defaults to continuous, and is independent of Markdown reading.

## Acceptance (EARS)

- When continuous mode is enabled, a vertical touch drag or fling in the PDF viewport shall scroll through multiple pages without per-page snapping.
- When continuous mode is enabled, the reader shall update its page number and note pane to the visible page nearest the viewport center.
- When continuous mode is enabled, touch scrolling and pinch zoom shall remain available while stylus input writes to the page under the pen.
- When single-page mode is enabled, a qualifying upward/downward touch swipe shall navigate exactly one page in the corresponding direction.
- When the gesture is stylus input, multi-touch, or below threshold, the reader shall not change pages.
- When the current page is the first/last page, the matching button and swipe shall not wrap.
- When a continuous page becomes active, dirty notes shall remain recoverable and the reader shall not wait for note persistence before scrolling.
- When a single-page change is accepted, the reader shall preserve existing note-save and page-scoped state behavior.

## Verification

- JVM tests cover gesture direction, threshold, diagonal rejection, and boundary policy. Continuous page policy tests cover center-page selection.
- API 34 tablet AVD tests cover continuous page synchronization and interaction plus the single-page setting. No cross-version or OEM matrix is required.

## Current acceptance status

- Verified on a landscape Android 14/API 34 tablet AVD: continuous scrolling, center-page/note synchronization, per-page draft retention, the setting switch, single-page mode, and touch/stylus/pinch interaction.
- Not covered by this milestone's focused reader-flow tests: process-reopen restoration from a checkpoint, every explicit navigation source, and physical-stylus/device variation. See the [runtime screenshots and exact environment](../../../../docs/android/guide/images/pdf-continuous/README.md).
