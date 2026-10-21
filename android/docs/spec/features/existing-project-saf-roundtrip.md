# Existing Project SAF Round Trip

## Goal

Prove that Android can read and update an existing desktop-format `.pagenote` project through Android's `ContentResolver` and SAF document URI contract without changing the project format or losing other page notes.

## Scope

- Exercise the production `ProjectDocumentOpener`, `PdfDocumentSession`, `ProjectNoteStore`, and `PageNotes` implementations against an isolated Android instrumentation `DocumentsProvider`.
- Build a valid 69-page PDF fixture and a matching v1 `project.pagenote` manifest with 69 page-note sections.
- Open the selected tree root, validate the manifest and note file, render pages 1, 35, and 69, edit page 35, save through the normal note store with its expected-original guard, and reopen from the same provider.
- Verify that the updated page 35 is persisted and page 1/page 69 note text remains byte-for-byte unchanged.

## Out of Scope

- No changes to the desktop project schema, production SAF implementation, Android reader UI, or user project files.
- This provider-backed automated test does not claim real DocumentsUI selection, persisted grants from a storage provider, or physical-device behavior. The user's completed 69-page project has separate manual AVD evidence below.
- No OCR inference is performed; the offline OCR runtime gate is tracked by `features/offline-ocr.md`.

## Acceptance (EARS)

- When the provider exposes a valid pagenote v1 project tree, `ProjectDocumentOpener` shall resolve `project.pagenote`, the PDF source, and `src/notes.md` through the selected tree URI.
- When the project PDF contains 69 pages, `PdfDocumentSession` shall report 69 pages and render the requested zero-based indices 0, 34, and 68 as non-empty pages.
- When a note is changed on page 35 and saved against the original note text, `ProjectNoteStore` shall persist the joined page-note document.
- When the saved project is reopened, page 35 shall contain the new note and page 1/page 69 shall retain their original content.
- When a stale expected note is supplied, the existing conflict guard shall reject the write without changing the provider document.
- The test fixture shall be isolated to the instrumentation provider and shall never mutate a desktop or user-selected project.

## Test Design

- Implement the fixture as an Android test-only `ContentProvider` exposing SAF-shaped tree/document URIs, stable document IDs, `ContentResolver` streams, and the `findDocumentPath` contract required by the production recovery-safe note writer. This avoids pretending that a direct instrumentation call received a system picker grant.
- Generate the PDF during test setup with distinct page labels, avoiding a checked-in binary fixture.
- Use one round-trip test for open/render/edit/reopen/page isolation and a separate conflict test for stale-write rejection.
- Run on API 34 and API 35 AVDs; record SAF picker and real persisted-grant validation separately.

## Verification

- The instrumentation tests must execute (nonzero count) on API 34 and API 35.
- Each run must assert 69 pages, non-empty rendering at pages 1/35/69, persisted page 35 content after a fresh opener, unchanged neighboring notes, and no mutation after a stale-write conflict.
- `:app:testDebugUnitTest`, `:app:assembleDebugAndroidTest`, and the relevant `connectedDebugAndroidTest` runs must pass. This integration test complements, but does not replace, the task 12 real-provider/manual workflow.

## Verification Results (2026-10-18)

- Both instrumentation tests pass on Pixel Tablet API 34, API 35 4 KB, and the official API 35 16 KB AVD (`getconf PAGESIZE = 16384`). The fixture contains a generated 69-page PDF and 69 note sections; rendered pages 1/35/69 have distinct verified color markers.
- The save/reopen test changes page 35 and confirms pages 1 and 69 are unchanged. The stale expected-original test confirms a concurrent note update is not overwritten.
- `:app:testDebugUnitTest`, `:core-project:testDebugUnitTest`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest` pass.
- This fixture itself does not exercise the system DocumentsUI picker, persistable grant flow, physical device, or the user's actual PDF.

## Separate Real-Project AVD Check (2026-10-18)

- The user's desktop project was read without modifying it. A private copy was assembled outside the repository as a standard v1 project with the 69-page PDF, 69 notes, ten uploaded attachments, and one formerly inline image.
- The copy opened via the real DocumentsUI folder picker and persistable SAF tree grant on API 35 4 KB and 16 KB tablet AVDs. The reader reported 69 pages on both; the 16 KB AVD page size was 16384 bytes.
- Page 31 was visually checked on the API 35 4 KB tablet AVD: PDF, note Markdown, LaTeX formulas, and its local project image rendered. The API 35 16 KB AVD smoke test verified opening page 1; it did not repeat the page-31 visual check.
- This check does not claim physical-device behavior, note editing/save, full-page review, handwriting, OCR, or Agent end-to-end acceptance.
