# Android Reader Page Jump

## Goal

Let users jump directly to a one-based page from the persistent reader page status, matching the desktop page-number control while retaining Android-native touch behavior.

## Scope

- The current `page / total` status becomes an accessible button in both phone and tablet reader layouts.
- Activating it opens a numeric page field initialized to the current page.
- Page numbers are one-based and valid only in `1..totalPages`; blank, malformed, zero, negative, and out-of-range values cannot be confirmed.
- Confirmation reuses the existing page-change flow. A dirty current note must save against its captured page revision before navigation; a save conflict or failure leaves the reader on the current page and preserves the draft.
- A successful jump loads the target page note and pen strokes, resets the PDF viewport bookmark as previous/next navigation does, and stores the target page's own note revision as its base revision.
- No project-format or file-picker behavior changes.

## Acceptance (EARS)

- When the user activates the page status, the app shall show a page-number dialog initialized to the current one-based page.
- When the input is not an integer in `1..totalPages`, the app shall keep confirmation disabled and show localized validation feedback.
- When a valid target differs from the current page and the current note is dirty, the app shall save the current page against its existing expected revision before changing pages.
- When the current-page save fails or conflicts, the app shall remain on that page, retain the dirty draft, and not update the page checkpoint.
- While a page jump is in flight, the app shall reject duplicate navigation requests and disable the page field, confirmation, and dismissal until the save/navigation settles.
- When navigation succeeds, the app shall show the selected page, load that page's note and committed pen strokes, and use that page note's revision as the next save baseline.
- When the target is the current page, the app shall close the dialog without resetting the viewport or rewriting the note.

## Tests

- JVM tests cover whitespace trimming, valid first/last pages, malformed numbers, empty input, zero, negative, and upper/lower range rejection.
- Activity instrumentation covers the real page-status control and dialog, initial page value, invalid input, dirty-note save before a jump, target-page draft/revision/stroke loading, viewport reset, and revision-conflict preservation through the SAF fixture.
- A controllably paused SAF note write verifies that a pending jump disables duplicate confirmation, input edits, and dismissal.
- A fixture-seeded Activity test does not claim system picker, real persisted SAF grant, physical-device, or stylus verification.

## Verification

- `PageJumpPolicyTest`: 3/3 JVM cases pass.
- `ReaderPageJumpActivityTest`: 3/3 tests pass on `Pixel_Tablet_API34` (API 34, 4096-byte pages), `Pixel_Tablet` (API 35, 4096-byte pages), and `Pixel_Tablet_16KB` (API 35, 16384-byte pages). Coverage includes successful dirty-note save/navigation (with target note/stroke loading, viewport reset, and untouched page 2), a `PageNoteConflictException` that leaves the reader on page 1 with its local draft and jump dialog intact while preserving the external note, and a paused SAF write proving duplicate confirmation/input/dismissal are disabled until save completes.
- Fresh app and Agent JVM suites pass 178/178 and 56/56 with zero failures, errors, or skips. Android instrumentation Kotlin compilation and `assembleDebug` pass.
- All four debug ABI APKs pass `check_16kb_apks.py`; arm64-v8a and x86_64 native LOAD segments are at least 16 KB aligned, OpenCV ARM64 contains no `RDVL`, and ZIP alignment passes.
- One initial 16 KB conflict-test run timed out waiting for the status update. The focused retry and the complete three-AVD matrix passed; this timeout was not reproduced.

## Related

- Desktop command mapping and page-number field: `desktop-ui-and-office-parity.md`.
- Adaptive navigation surface: `adaptive-menu-navigation.md`.
- Integrated task ledger: `../tasks.md`, task 16.
