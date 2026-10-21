# Note Text Selection to AI Review

## Goal

Let a user turn selected Markdown note source into a located `selection` AI review that the Android Agent can read and apply without changing the project format.

## Scope

- Selection is captured from the current page's native Markdown `EditText` in Edit mode. The existing Word menu command `Selected text to AI annotation` is enabled only while a valid selection exists and the note is writable.
- Source-PDF character selection, Office editing/selection, and visual selection highlights are not implemented by this slice. It must not manufacture character-level PDF anchors from page text blocks or OCR rectangles.
- Review records remain in `src/ai-reviews.json`; normal point/region annotations remain in `src/annotations.json`.

## Interaction

1. The user selects a non-whitespace range in the current Markdown editor and chooses `Selected text to AI annotation`.
2. The app captures the exact Markdown source quote, full current page context, zero-based half-open UTF-16 source offsets, page, and note revision. If the note has unsaved changes, the existing revision-checked save runs first; a save failure aborts review creation.
3. The existing review dialog shows the captured quote and an instruction field. The user may save or cancel. Saving creates an open, page-scoped review; it never changes note text.
4. The Agent's `read_review` tool can list review summaries and fetch one record by ID, returning its quote, context, revision, and anchor. Existing `write_note`/`patch_note` tools remain the only way the Agent changes note content.
5. A review can be marked applied only after the same Agent run successfully changes that review's target note page. A no-op write, failed write, unrelated page write, or user-only status action cannot mark it applied. Users may still dismiss or delete reviews.

## Data Contract

- `side="note"`, `kind="selection"`, `scope="page"`, and the one-based current page.
- `quote` is the exact selected Markdown source and must be non-empty, non-whitespace, and at most 100,000 UTF-16 code units.
- `context` is the complete page Markdown and must be at most 1,000,000 UTF-16 code units. Oversized data is rejected visibly rather than silently truncated.
- The selection action remains disabled when quote or page context exceeds those limits and explains that the selection or page is too large; it must not report the generic missing-selection reason for an otherwise valid oversized range.
- `revision` is the SHA-256 revision of `context` as defined by `PageNotes.revision`.
- `anchor` is `{ "coordinateSystem": "markdown-source-utf16", "start": <start>, "end": <end> }`; `0 <= start < end <= context.length`, and `context.substring(start, end) == quote`.
- The SAF adapter preserves `quote`, `context`, `revision`, and `anchor` using the existing `ReviewCreate` fields. Unknown review fields remain untouched.
- Agent review reads support optional `id` (at most 128 characters). A list read returns at most 20 records, each with id/side/kind/page/status/revision and instruction/quote previews capped at 160/320 UTF-16 code units with an explicit truncation marker; it omits context/anchor. List metadata fields are bounded before serialization: id 128, side 16, kind 16, page 10 ASCII digits, status 16, and revision 128 characters; a field outside its bound is returned as null. An ID read returns the full stored record, including quote/context/anchor, for that one record, but fails rather than returning a JSON detail over 8 MiB UTF-8.
- Review JSON reads and rewrites are capped at 64 MiB UTF-8. Larger files fail before full parsing, and oversized updates fail before overwrite.
- Review JSON updates create and populate a sibling recovery copy when the SAF parent advertises directory-create support, recheck the expected original before overwrite, and restore the original after a partial-write failure. If rollback also fails, the recovery copy is retained and its name is reported. If the target write succeeds but recovery-copy cleanup fails, the mutation remains a success and the retained copy name is surfaced to the user/Agent. Providers that explicitly do not support sibling creation use the documented best-effort overwrite path; capability-query or backup-preparation errors fail closed. SAF still provides no generic cross-process compare-and-swap.
- Agent application authorization is scoped to one handler/Agent run and records only a successful note write whose resulting page revision differs from its expected revision. The write's expected revision must equal the review's captured revision, and the target page must match the review page.

## Acceptance (EARS)

- When the current editable note has a valid non-empty selection, the app shall enable `Selected text to AI annotation`; without a valid selection or write access, it shall keep the action disabled.
- When the user invokes the action with unsaved note text, the app shall save against the existing expected revision before showing the review draft, and shall not create a review if that save conflicts or fails.
- When the user saves a selection review, the app shall persist the exact quote, full page context, page revision, and matching UTF-16 anchor as an open `kind=selection` record without modifying the note.
- When selection bounds, quote, or context exceed the contract, the app shall report an error and shall not write a partial review.
- When a valid selection exceeds the quote or context limit, the menu shall show a localized size-limit reason instead of the generic selection-required reason.
- When the Agent lists or reads a review, the tool shall return enough record metadata to identify and apply the selected request; ID reads shall include quote, context, revision, and anchor.
- When review JSON exceeds 64 MiB UTF-8 or one serialized detail exceeds 8 MiB UTF-8, the Agent shall return a bounded failure instead of parsing/returning the oversized payload; writes over 64 MiB fail before overwrite, and list metadata shall obey the field limits above.
- When a review JSON write fails after truncating or partially writing the target, the app shall restore the original from its snapshot; if restoration fails, it shall retain and report the recovery copy when the Provider supports it.
- When a review JSON update succeeds but cleanup of its recovery copy fails, the app/Agent shall report the successful update and retained copy name, not a false save failure.
- When first-time review-file initialization fails and the provider also cannot delete the incomplete file, the app shall warn that `ai-reviews.json` may remain and localize the warning for the current UI language.
- When a user invokes selection review from the real reader Activity with a dirty note, the app shall save the note against its captured base revision before displaying the review dialog; if that save conflicts, it shall preserve the external note, keep the review dialog closed, and perform no review-file write.
- When a dirty note saves successfully from the real reader Activity, the app shall display a review draft for the selected quote and persist the exact selection metadata when the user confirms it.
- When an Agent requests `status=applied`, the tool shall accept it only if that same run has already changed the review's target note page through a successful `write_note` or `patch_note` whose `expectedRevision` equals the review revision; otherwise it shall return a tool failure.
- When the user dismisses or deletes a review, the app shall not modify its note page.

## Verification

- [x] JVM tests cover valid/invalid selection bounds, whitespace-only selections, exact UTF-16 offsets including supplementary characters, quote/context limits, and review-apply authorization for changed, unchanged, failed, and wrong-page writes.
- [x] Instrumentation tests cover native EditText selection reporting, menu enablement, quote display, cancel-without-write, and a real SAF-provider review JSON round trip.
- [x] Agent tests cover list summaries, ID detail reads, rich selection metadata, and rejection of premature `applied` status.
- [x] Final-review hardening tests cover localized oversized-selection feedback, early rejection before scanning/materializing oversized selections, exact quote/context boundaries, bounded review JSON reads/details/metadata, strict UTF-8 input, SAF recovery-copy rollback after a partial write, retained-copy reporting after failed cleanup, and localized incomplete-file warning after failed initialization cleanup. Fresh app/Agent JVM suites pass 175/175 and 56/56. The four focused instrumentation classes pass 33/33 on API 34 4 KB, API 35 4 KB, and API 35 16 KB AVDs. Android test Kotlin compilation and `assembleDebug` pass; independent review reports no remaining findings.
- [x] Activity integration tests launch `MainActivity`, attach the SAF fixture to its reader session, edit/select text in the native note editor, invoke the real menu command, and verify both successful save-and-review creation and save-conflict abort with zero review writes. The conflict test checks the exact `PageNoteConflictException` message and retained dirty draft. The 2 tests pass on API 34 4 KB, API 35 4 KB, and API 35 16 KB tablet AVDs.
- [x] After adding Activity integration coverage, fresh app/Agent JVM suites pass 175/175 and 56/56; Android instrumentation Kotlin compilation and `assembleDebug` pass; all four ABI APKs pass the 16 KB checker.
- [x] `./gradlew :app:testDebugUnitTest :agent:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug --offline` succeeded. The two JVM tasks were up-to-date in this run; a forced fresh rerun of both JVM tasks passed with 171 app tests and 56 Agent tests, with zero failures, errors, or skips. Android test Kotlin compilation and `assembleDebug` succeeded.
- [x] The focused classes `NoteSelectionReviewUiTest` (7), `ExistingProjectSafRoundTripTest` (6), `ReviewUiErrorMessageTest` (2), and `ReviewHandoffUiTest` (5) passed, 20/20 on each tested AVD: `Pixel_Tablet_API34` (API 34, 4096-byte pages), `Pixel_Tablet` (API 35, 4096-byte pages), and `Pixel_Tablet_16KB` (API 35, 16384-byte pages). Results had zero failures, errors, or skips.
- [x] After final-review hardening, the focused classes `NoteSelectionReviewUiTest`, `ExistingProjectSafRoundTripTest`, `ReviewHandoffUiTest`, and `ReviewUiErrorMessageTest` pass 25/25 on both `Pixel_Tablet_API34` (API 34, 4096-byte pages) and `Pixel_Tablet_16KB` (API 35, 16384-byte pages). Fresh app/Agent JVM suites pass 173/173 and 56/56; Android test Kotlin compilation and `assembleDebug` succeed.

## Verification Limits

- System picker/SAF grant and full Activity project-open interactions are not covered by the Activity-level selection tests; those tests seed the Activity's existing reader session from the SAF fixture. Dirty-save success and conflict abort are covered through the real Activity menu handler.
- Source-PDF and Office text selection are not implemented. Physical-device and physical-pen behavior were not tested.
- Generic SAF has no cross-process compare-and-swap; review-store serialization covers app-process instances only.
- Unsaved-draft process-reclamation recovery is already implemented and tested separately under task 15, including the API 34 SIGKILL task-recovery smoke test. That coverage is separate from selection-review verification.

## Implementation Tasks

- [x] Add a pure selection-draft model and boundary validation.
- [x] Track note-editor selection and wire the existing menu action to the review dialog after revision-checked note save.
- [x] Extend SAF review creation/list rendering to preserve and display selection fields.
- [x] Return selection details through Agent review reads and guard applied status using same-run changed-page evidence; remove manual apply status control.
- [x] Add JVM, Compose/AndroidView, SAF-provider, and Agent regression coverage; build and run focused AVD instrumentation.
- [x] Add real-Activity selection-review success and save-conflict instrumentation coverage; keep picker/SAF grant validation as a separate acceptance. The real Activity handler saved the dirty page before creating the exact review record, and an external note update caused the expected conflict with zero review writes.
