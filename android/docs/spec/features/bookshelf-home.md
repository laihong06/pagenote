# Android Bookshelf Home

Status: approved by the user on 2026-10-22; implemented with focused local verification.

## Intent

Replace the Android reader's empty/startup screen with a local bookshelf, using
the user's reference image for the notebook-cover grid and shelf presentation.
The user chooses one existing directory, such as `pagenote`, once. Future launches
discover projects in that directory without asking for the directory again.
This feature targets the existing Android 14 tablet workflow only.

## Requirements

### 1. Bound Library Directory

- On the first launch without a library binding, the bookshelf shall provide a
  system folder-selection action and persist the selected SAF tree's read/write
  permission. A canceled or denied selection shall not be recorded as a binding.
- When an authorized library binding exists, the app shall reuse it on launch
  and scan for existing `project.pagenote` projects, including projects in
  organizational subfolders. Scanning shall stop descending inside an identified
  project, excluding its `src`, assets, caches and history from further discovery.
- When the app resumes, returns to the bookshelf, or creates/imports a project,
  it shall refresh the library. A manual refresh action shall remain available.
- When permission is revoked or the directory is unavailable, the bookshelf
  shall show a recoverable error and a directory-rebinding action, not an empty
  successful scan. Invalid individual projects shall not prevent other valid
  projects from being listed; scan limits/failures shall be visible.
- Settings shall display the current directory and offer a change-directory
  action. Changing it shall not move/delete old files or revoke permissions
  needed by already-open/recent projects.

### 2. Bookshelf Screen

- A cold launch shall show the bookshelf, not immediately reopen the previous
  reader. Selecting a book shall use the existing SAF reader and restore that
  project's reading position and recoverable draft.
- The bookshelf shall use a cover grid with shelf rows, notebook-style covers,
  titles, modification/reading metadata, and a per-book overflow menu. Cover
  size and title space shall remain stable while previews load.
- The first screen shall provide new-notebook, import-document, search, sorting,
  refresh and settings actions. Search shall filter book names locally. Sorting
  shall include recent update and name; grid/list presentation shall be supported.
- Empty, scanning, unavailable-folder, invalid-project and thumbnail-failure
  states shall be distinguishable. Do not add the reference app's cloud-sync
  banner or a new account/cloud service.
- The reader shall provide a return-to-bookshelf action. Before leaving it,
  dirty notes and pending ink writes shall use the existing save/recovery
  protection. A failed save shall not silently discard the draft.

### 3. Default and Custom Covers

- A PDF project shall default to a thumbnail of its first actual PDF page.
  The full page shall fit inside the cover; do not crop away slide/page content.
- A Markdown project shall default to a reusable paper-notebook cover marked
  "Note" (the user's 2026-10-22 clarification: "草纸本" described its style, not
  literal cover text),
  not a screenshot of raw Markdown or an empty PDF placeholder.
- Proposed customization: change the bookshelf display title, choose a local
  cover image, select another PDF page as the cover, choose a paper-cover color,
  and reset to the format's default cover.
- Cover selection shall not alter the source PDF, notes, annotation page
  numbers, or project-directory name. Selected images shall be copied into the
  project's assets so the cover does not depend on a temporary picker URI.
- Per-book presentation metadata shall be stored separately from the existing
  project manifest contract. Missing/malformed optional appearance metadata
  shall fall back to the default cover without making the project unreadable.
- PDF/image thumbnails shall load off the UI thread, with bounded concurrency,
  size/memory limits, cancellation, and revision-sensitive disk caching. A failed
  thumbnail shall retain an openable book and a visible fallback/error state.

### 4. Unified Creation and Import

- Normal new notebooks, optional PDF/Markdown imports, Rain Classroom historical
  imports and live-capture projects shall default to the bound library directory.
  They shall no longer require a new parent-folder selection for each project.
- Creating a notebook shall retain the existing standard project format,
  uniqueness checks, source-document validation and failure cleanup. The
  selected library directory shall not be stored as an absolute path in project
  contents.
- A successfully created project shall appear on the next bookshelf refresh
  automatically, including after restarting the app. Do not maintain a separate
  authoritative book list that can drift from directory contents.
- Rain Classroom live capture shall continue creating its PDF from the first
  real received slide. The book shall appear once that standard project exists,
  with the first captured page as its default cover.
- Existing external projects shall remain openable through the existing picker.
  They shall not be moved or copied into the library without an explicit import
  action. Loose PDF/Markdown files shall use the existing document-import flow;
  directory discovery shall not fabricate projects around arbitrary files.

## Proposed Design

### Storage and Discovery

- A dedicated local DataStore holds the bound SAF tree URI, display name and
  bookshelf sort/view preferences, plus separate per-project reading positions.
  Reading positions are keyed by the SAF tree/root and checked against the
  project ID; a replaced project cannot inherit another book's position.
  It is independent of model credentials and
  reader-layout settings.
- A `BookshelfRepository` scans the authorized tree using structured
  `DocumentsContract` queries and existing manifest validation. Book identity is
  `(tree URI, project root document ID)`, not just a possibly duplicated manifest
  ID. Traversal is cancellable and bounded; reaching a bound reports a partial
  scan instead of claiming completeness.
- Discovery reads manifests and lightweight file metadata, not all PDF/Markdown
  document contents. Recognized projects retain the existing `ProjectOpenTarget`
  and project-open pipeline.
- An optional versioned `src/bookshelf.json` stores display-title and cover
  choices. Custom images use `src/assets/`; no changes to `project.pagenote` v1
  are required and desktop clients may ignore this sidecar safely.

### UI and Reader Integration

- Add a native Compose bookshelf screen, independent of the PDF/Markdown reader
  panels. Use the reference's shelf/notebook composition, restrained neutral
  surfaces and actual document-cover images. Existing reading themes still
  apply to reader content.
- Keep creation/import dialogs and save protection in the existing reader
  workflow, but supply the bound library root as their destination. Add explicit
  bookshelf/reader navigation instead of relying on `project == null` as the
  entire startup-navigation model.
- Delay automatic reader restoration until a project is selected; preserve
  checkpoint and draft recovery. Avoid running reader OCR/prefetch solely
  because a previous book was open while the bookshelf is displayed.
- Refresh after project creation, cover changes and returning from the reader.
  Do not depend on Android file watching being reliable for every SAF provider.

## Focused Acceptance

- JVM: scan filtering/identity, sort/search, safe appearance metadata and cover
  selection/default policy.
- One Android 14 tablet AVD: bind directory, restart/reuse binding, discover an
  existing Markdown and PDF project, create a notebook, reopen it from the shelf,
  return while preserving a note, change/reset cover, and change the bound root
  without deleting old content.
- Verify the actual PDF first-page thumbnail and Markdown default cover in one
  shelf screenshot; check title wrapping and stable cover dimensions.
- Check missing-grant and malformed-project recovery using the test SAF
  provider. Keep the device matrix limited to the existing API 34 tablet AVD.
- Rebuild/install the Android debug APK with data preservation. This feature
  does not publish a release or modify desktop runtime packages.

## Non-Goals

- Cloud synchronization, accounts, arbitrary-storage permissions or a background
  service scanning outside the user-authorized directory.
- Automatic relocation of existing project folders, deletion/archive management,
  or a replacement for the cross-platform project format.
- Reimplementing the reader, OCR, pen engine or Rain Classroom protocol.
