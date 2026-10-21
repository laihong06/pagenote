# Android Note Import

## Scope

Connect `File > Import notes` for writable Android projects. Accept desktop-compatible JSON page-note backups and Markdown/plain-text files through Android's document picker. Import notes only; never replace the PDF, project manifest, annotations, pen strokes, or assets.

## User Story

As a learner, I can restore page notes from a pagenote JSON backup or insert Markdown/text into the current page without losing unrelated pages or project data.

## Behavior

- The command is available when a writable project is open and the reader is idle. Read-only projects cannot import.
- Use Android's `OpenDocument` picker for JSON, Markdown, and plain text. Detect JSON by a case-insensitive `.json` display-name suffix, falling back to the provider MIME type `application/json`; other supported files are UTF-8 text.
- A JSON backup is accepted when its top-level `notes` field is an object. `version`, `document`, and `pages` are informational and are not required to match the currently open project, matching desktop behavior.
- Map one-based decimal page keys to strings. Apply only keys whose numeric page is in `1..currentPageCount`; skip malformed keys, out-of-range pages, and non-string values. An empty string is a valid note and clears that page. Missing pages remain unchanged. An object with no valid entries is a valid no-op import.
- Before applying JSON, show an overwrite confirmation. Canceling performs no write. After confirmation, save any dirty current page using its captured page revision; stop on save failure or conflict. Read the latest notes under the existing app-process mutation lock, apply valid pages, and perform one recoverable SAF write guarded by the exact original content. A conflict or write failure must not report success.
- After JSON import succeeds, refresh the current page, its revision baseline, and the note-history mirror. Preserve all unlisted pages and all non-note project files. The batch is committed as one notes-document write, so a later page failure cannot leave a partial batch.
- For Markdown/plain text, replace the current editor selection with the imported text. If the preview tab was active, switch to Edit and insert at the end of the current page. Treat the content as text; do not parse it as a project or JSON unless selected as JSON by the detection rule.
- Capture project identity and current page when opening the picker. If the project/page context changes before the result is applied, reject the result without writing. Picker cancellation is a no-op.
- Read/decode, malformed JSON, confirmation, revision conflict, SAF failure, and success states use localized status text and never expose provider exception details beyond existing local error policy.

## Acceptance (EARS)

- When a writable project is open and the user selects `Import notes`, Android shall offer a system picker for JSON, Markdown, and text files.
- When a valid JSON backup is selected, Android shall confirm before overwriting represented pages and preserve unrepresented pages.
- When a JSON page key is invalid, out of range, or maps to a non-string value, Android shall skip that entry; an empty string shall clear only its represented page.
- When JSON is malformed or lacks an object-valued `notes` field, Android shall report failure without modifying project notes.
- When a dirty note is present, Android shall revision-save it before import and stop if the save conflicts or fails.
- When the note document changes before the import write, Android shall reject the write; successful imports shall use the existing recoverable SAF write path.
- When Markdown or plain text is selected, Android shall insert it into the current page draft, replace the current selection when the editor is active, and switch from preview to Edit when needed.
- When the picker is canceled or the captured project/page context is stale, Android shall leave project data unchanged.

## Verification Scope

- One focused JVM test class covers JSON validation, page filtering/empty values, atomic merge behavior, and Markdown selection insertion.
- One focused Android 14/API 34 tablet AVD activity test covers menu/picker dispatch, JSON confirmation/apply/cancel/error, Markdown insertion, stale context, and preservation of an unlisted page through the real reader and SAF fixture.
- Run affected app/JVM tests, that single AVD test, and `:app:assembleDebug`. Do not run full Android suites, other API/device matrices, 16 KB checks, or unrelated renderer/OCR/pen tests for this feature. The AVD is a proxy; Xiaomi Pad 5 Pro hardware acceptance remains in task 12.

## Not Included

- Importing/replacing a complete `.pagenote` project or any non-note data.
- Importing images as assets or rewriting Markdown asset references.
- Transactional import across multiple provider documents; the supported JSON batch changes only the single notes document.
- Importing files with encodings other than UTF-8.
