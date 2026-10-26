# Rain Classroom Live Project Creation

## Requirements

- Android 14 can start live capture without an open project, including while a Markdown project is open.
- Historical import and live capture are separate actions. Live capture offers a new project as its primary destination; append to the current writable PDF remains available.
- A new-project dialog collects the project name and SAF parent directory. Entering a live classroom requires explicit attendance consent. Canceling the dialog or directory picker never joins a classroom.
- Validate the destination before connecting. Receiving the first real slide creates a standard PDF project and opens it; before that the UI shows a waiting state. Do not add a blank placeholder page or write an invalid zero-page PDF.
- Subsequent slides are inserted into the same project, preserving notes and source identities. Group by first-seen classroom/PPT, then original slide index, as specified in [canonical slide order](yuketang-slide-order.md). Replayed slides are deduplicated and focused. Failed first-page creation remains retryable through the existing pending-page queue.
- Joining mid-lesson replays all available published slide entries in the initial classroom timeline before processing later live messages. Resolve `sid` page references against the authenticated presentation metadata; do not assume every entry includes `si` or download unseen future slides.
- Track the first usable snapshot independently of WebSocket connection count. Reconnect matches either the last source index or the corresponding page ID, so `sid` timelines retain the existing cursor-based recovery behavior.
- Keep earlier-slide backfill in the existing retryable queue and expose remaining work. Do not silently report completeness if a slide is unavailable or the server's reconnect timeline lacks the saved cursor.
- An ongoing classroom rejected by the report API with `LESSON_ON` offers live-project creation; switching mode must not imply attendance consent or automatically connect.
- Protect unsaved notes when changing projects. Do not switch capture destinations silently while capture or pending writes are active.

## Implementation

- Reuse `RainProjectWriter.create` for the first slide and `append` for subsequent slides through an Android-local `RainLiveProjectCapture` coordinator.
- Keep the chosen SAF target in that coordinator before notifying the reader, so a reader-open failure can retry without creating a second project.
- Keep creation settings in the reader, separate from the Rain Classroom dialog. Scope historical and live classroom selections independently.
- Use the current-class list for real-time selection. A single current classroom may be preselected; attendance consent remains explicit.

## Focused Acceptance

- API 34 tablet AVD only; no additional device/version matrix.
- Offline SAF regression: first slide creates one real PDF page; duplicate replay does not grow the PDF; the next slide preserves notes and adds a second page/source record.
- Compose regression: new capture remains available without a writable PDF, append stays disabled, and new-project confirmation requires name, folder, and live consent.
- Protocol regression: `LESSON_ON` is distinguishable from authentication and unrelated report failures.
- Build/install with data preservation. Do not run Gradle connected tests that uninstall the logged-in app. Default automated tests never join a real classroom; a separate opt-in probe requires explicit human authorization for possible attendance records.

## Verification Record (2026-10-22)

- API 34 tablet AVD: three Compose action/consent/cancel regressions passed. New-project capture is enabled without a PDF; append remains disabled in that state.
- SAF first-slide/replay/append regression passed: one actual first page, duplicate ignored, second slide appended, saved page-one note preserved, and matching source identities retained. The test provider now implements the standard document-path lookup needed by note recovery writes.
- Real account, explicit user authorization: opt-in `RainLiveCaptureSmokeTest` entered the current classroom and created a project containing one actual PDF page, one note page and one source record. The additional 30-second observation window received no new slide; real multi-slide append is not claimed. The client was stopped and the test project deleted.
- Actual `MainActivity` entry: opt-in `RainClassroomEntrySmokeTest` opened the new-project form with no project open, checked name/folder controls and disabled pre-consent confirmation, and canceled without joining. One screenshot was visually inspected; text and controls fit the tablet viewport.
- Real-account tests are separate from default automated acceptance and require `rainLiveCaptureSmoke=true` (attendance side effect) or `rainEntrySmoke=true` (read-only classroom discovery).

### Mid-Lesson Backfill Fix (2026-10-22)

- Reproduced two defects with failing JVM tests: initial replay kept only its final entry, and timeline entries identified by `sid` were discarded because `si` was required.
- Added coverage for complete initial replay, page-ID resolution without index guessing, repeated-page deduplication, problem-entry filtering and page-ID reconnect cursors.
- API 34 tablet AVD, existing authorized account: rejoining the active classroom backfilled 18 actual PDF pages, 18 note pages and 18 source entries. The first retained source index was 1; only timeline-published pages were included. No subsequent live flip arrived during the additional observation window. Stopped the client and removed the test-only project.
- The first online probe failed at DNS resolution before entering the classroom. Toggling only the emulator Wi-Fi restored connectivity; the updated live smoke passed. No credentials were cleared, no TLS validation was weakened, and temporary diagnostic code was removed.
