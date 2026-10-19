# Android Note Export

## Scope

Connect `File > Export notes` for the Android reader. The user can export either a complete pagenote-compatible v1 JSON notes backup or the current page as Markdown, using Android's system save-location picker. The export does not include the source document, annotations, pen strokes, or image assets. JSON and Markdown/text note import are specified separately in `note-import.md`.

## User Story

As a learner, I can save my page notes in the same JSON format as the desktop app or export the current page as Markdown without replacing or relocating the open project.

## Behavior

- The command is available whenever a project is open and the reader is not busy, including read-only projects.
- Selecting the command opens two native choices: `Full notes backup (JSON)` and `Current page (Markdown)`. Each choice opens `CreateDocument` with the matching MIME type: `application/json` or `text/markdown`.
- The JSON backup matches the desktop v1 contract: `version` is `1`; `document` is the project display name; `pages` is the source document page count (for Markdown, count source pages only); `notes` is a one-based page-number-to-Markdown object created from the project's page notes. Do not synthesize entries up to the source page count; preserve pages represented in the notes document, including blank pages between page breaks.
- The JSON filename is `<project-name>-逐页笔记.json`. The Markdown filename is `<project-name>-第<page>页.md`. The user may choose another location or filename in the system picker.
- Before creating an export snapshot, a dirty editable page must be saved through the existing revision-checked note flow. If saving fails or conflicts, do not open the system picker. For a locally recovered dirty draft in a read-only project, include that page's draft in the export snapshot without writing to the project.
- Persist a pending export's UTF-8 content atomically in app-private storage and keep only its MIME type and filename in saved state. Activity/process recreation while the system picker is open must resume the same snapshot; clear it after cancellation or completion. If the process dies after the picker result is consumed but before writing finishes, offer retry-to-a-new-destination or discard instead of leaving the reader blocked. Never place the full payload in the saved-state Bundle.
- The current-page Markdown export contains the complete page source, not the bounded preview. A dirty local draft is included under the same save/read-only rules.
- Canceling the picker creates no output and does not alter project files beyond a successful pre-export note save. A SAF write failure reports failure and must not report success or modify project notes.
- Export is a snapshot: edits made after the user chose the export format are not included. JSON exports notes only; Markdown exports one page only. Neither format embeds project assets.

## Acceptance (EARS)

- When a project is open and the user selects `Export notes`, Android shall offer the full JSON backup and current-page Markdown outputs.
- When the user exports JSON, Android shall produce the desktop-compatible v1 fields and page mapping without adding annotations or source contents.
- When the user exports the current page as Markdown, Android shall export the complete current page, including text beyond the preview limit.
- When the current editable note is dirty, Android shall revision-save it before opening the picker and stop on save failure or conflict.
- When Android recreates the reader while a save picker is open, it shall retain and write the original export snapshot rather than producing an empty document or recapturing later edits.
- When a restored export has no live picker result to finish it, Android shall allow the user to retry the saved snapshot or discard it without blocking ordinary reading/navigation.
- When a read-only project contains a recovered dirty draft, Android shall include that draft in the export without modifying the project.
- When the user cancels or the chosen SAF destination fails, Android shall report no successful export and leave project data unchanged.

## Verification Scope

- One focused JVM test class covers both output payloads, the v1 JSON schema, one-based page mapping, blank intermediate pages, non-ASCII Markdown, and the current-page full-text payload.
- One focused Android 14/API 34 tablet AVD activity test covers the menu-to-SAF output flow for both formats, Activity recreation, fresh-ViewModel recovery from copied saved state plus the app-private snapshot, recovery state, read-only drafts, save conflicts, write failure, cancellation, and unchanged project notes. This simulates process restoration but does not kill the OS process; the AVD is a proxy, not Xiaomi Pad 5 Pro hardware evidence.
- Run `:app:assembleDebug`. Do not run full Android suites or other API/device matrices for this feature.

## Not Included

- Importing a JSON backup or Markdown file.
- Exporting images or producing a complete `.pagenote` project archive.
- Exporting annotations, handoffs, AI reviews, or pen strokes.
