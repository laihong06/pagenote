# Bookshelf Implementation

Approved scope: [bookshelf-home.md](bookshelf-home.md).

- [x] Requirements and storage/navigation design approved.
- [x] Add safe appearance metadata, bound-directory preferences and bounded SAF discovery.
- [x] Add cached PDF/image thumbnails, notebook covers and customization.
- [x] Add native shelf/grid/list, search and sorting.
- [x] Connect reader navigation, save protection and unified creation/import destinations.
- [x] Run focused JVM/storage/UI checks on the Android 14 tablet AVD; preserve installed data.
- [x] Update the changelog and record actual verification and remaining limitations.

No desktop runtime changes, release, new worktree or subagents are required.

## Verification (2026-10-22)

- `:app:testDebugUnitTest`: 350 tests, zero failures/errors, including appearance,
  local search/default cover policy, reading-position validation and the command
  checklist. Kotlin/kapt deprecation warnings remain from the existing toolchain.
- `:app:assembleDebug :app:assembleDebugAndroidTest`: successful. Install both APKs
  with `adb install -r` (test APK also `-t`), never the uninstalling connected-test
  task; existing app data and Rain Classroom credentials are retained.
- Three selected API 34 tablet instrumentation tests pass:
  `BookshelfRepositoryTest`, `BookshelfHomeTest`, `BookshelfFlowTest`.
  Coverage includes discovery boundaries, invalid-project warnings, unavailable
  roots, malformed optional metadata, first/specified PDF pages, copied image
  independence, appearance reset, separate reading checkpoints, bound-root
  creation, saved notes on return, search, list switching, customization,
  Activity recreation and the settings directory entry.
- Used Android DocumentsUI to select the existing `/sdcard/pagenote` folder and
  confirm its read/write authorization. The real shelf discovers its three
  existing projects, showing two actual PDF covers and one paper notebook.
  No existing files were moved or removed. Only UUID-scoped test fixtures were
  deleted by test cleanup, and the original library binding was restored.
- Shelf screenshot: `app/build/bookshelf-preview.png` (local build artifact).
  The full PDF pages fit their covers; title slots and shelf dimensions remain
  stable, including the long course title.
- A first test run exposed an unavailable root being mistaken for an empty
  folder; root validation now rejects it. Another run exposed asynchronous view
  selection in the UI test; it now waits for the actual list route before
  clicking. The final three-test run passes.
- Default paper covers now display the literal title `Note`, independently of
  locale and the project name shown below the cover. The existing four cover
  colors remain available. Rebuilt and installed both APKs with data-preserving
  replacement, reran `BookshelfFlowTest` successfully (1 test), and refreshed
  the real three-project shelf screenshot to confirm the visible label.

## Limits

- Only the requested Android 14 tablet AVD was exercised. No extra device/OS
  matrix or new live Rain Classroom attendance/session was used for this change.
- The complete legacy Activity instrumentation suite was not rerun. Some older
  fixtures assume the pre-bookshelf startup route; historical test results are
  not claimed as current bookshelf acceptance.
- Discovery is bounded at depth 8 / 2,000 directories (10,000 children per
  directory); partial scans warn explicitly. Thumbnail work is limited to two
  concurrent loads and a 48 MiB cache; non-seekable PDFs stage at most 512 MiB.
- Changing the root does not migrate old projects. Resetting a cover does not
  garbage-collect imported image assets, avoiding deletion of shared resources.
