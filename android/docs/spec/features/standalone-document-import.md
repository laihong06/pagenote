# Android Standalone PDF/Markdown Import

## Scope

Match the desktop `Open Document` workflow for standalone PDF and Markdown sources while keeping Android projects in the portable `project.pagenote` format. The source file is copied into the selected project; Android never keeps an external file URI in the project manifest. Existing projects and source bytes are not rewritten by merely opening the picker.

## Entry Points and Flow

- Add `Open document` to the Android File menu and the no-project welcome actions. It launches the platform document picker for PDF and Markdown candidates.
- A selected supported file opens the existing `New project` dialog with a filesystem-safe project name derived from the source filename and the source preselected. The user chooses the destination parent directory and may edit the project name before creation.
- Keep the optional source selector in `New project`; it accepts the same PDF/Markdown types. Canceling a replacement picker preserves the prior source and all other dialog fields. Clearing a source releases its persisted read grant.
- Persist the selected source's read grant while the dialog is pending, including Activity recreation. Release it after successful copy, clear, or dismissal.
- Do not close or replace the active reader project until the new project has been created successfully. A creation/open failure leaves the active project intact and reports a recoverable error.

## Project Contract

- Without a selected source, retain the existing empty Markdown project layout: `src/left.md`, `src/right.md`, `src/assets/`, and a v1-compatible manifest.
- With a PDF, copy the original bytes to `src/document.pdf`, preserve the picked display name as `leftName`, and set `left` to `src/document.pdf`.
- With Markdown, copy the original bytes without decoding or newline normalization to `src/document.md` or `src/document.markdown` according to its supported extension; preserve the picked display name as `leftName` and set `left` to that relative path.
- Both source modes create `src/right.md` and `src/assets/`. The manifest is written last and only points to project-relative paths.
- A default project name removes the final source extension, trims whitespace, replaces path-invalid characters with `_`, and falls back to `Imported document` when empty. Users can edit that suggestion.
- Accept `.pdf`, `.md`, and `.markdown` case-insensitively. Do not treat other extensions as Markdown just because a provider reports `text/plain`. Empty Markdown is valid. PDFs must be readable by `PdfRenderer` and have at least one page.
- Reject a same-name project without overwriting it. On any create/copy/validate/manifest failure, delete newly created documents in reverse order where the SAF provider permits it, preserve dialog values for retry, and report any residual project name.

## Acceptance (EARS)

- When a standalone supported file is selected from `Open document`, Android shall show `New project` with the source selected and a safe editable name suggestion.
- When the user chooses a destination and confirms creation, Android shall copy the selected source bytes, write a v1-compatible manifest last, and open the new project's first page.
- When Markdown is selected, the project copy shall be byte-for-byte equal to the picked file, including CRLF and a UTF-8 BOM if present.
- When a PDF is empty, malformed, unreadable, or has zero pages, creation shall fail before exposing a usable project and leave no partial project when cleanup is supported.
- When a source or directory picker is canceled, the current project remains open and the new-project dialog retains its previous name, destination, and source selection.
- When a file with an unsupported extension is returned by the provider, Android shall reject it without creating a destination project and show a recoverable message.
- When the Activity is recreated while a source is selected, its display name and readable URI grant shall remain available until clear, dismissal, or successful copy.

## Verification

- JVM tests cover extension/MIME policy, normalized source path, filename-to-project-name derivation, and PDF/Markdown manifest fields.
- API 34 tablet AVD tests use the existing SAF fixture provider to cover `Open document` selection, Markdown byte preservation, PDF page-1 opening, cancellation/replacement, unsupported extension, malformed-source and copied-PDF rollback, duplicate project names, and preservation of the current project on failure.
- Persistable read grants are exercised against the fixture provider across Activity recreation, then verified released after source removal, dialog dismissal, and successful project creation. This verifies Android grant mechanics, not the system document picker UI or Xiaomi hardware.
- Compile the affected Android test sources and build `:app:assembleDebug`; use only the API 34 tablet AVD for routine instrumentation verification.

## Not Included

- Word or PowerPoint source import/conversion on Android.
- Linking to an external URI without copying the source into the project.
- Changes to desktop document import, project schema, or project version.
