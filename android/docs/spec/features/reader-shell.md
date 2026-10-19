# Android Reader Shell

## Scope

This slice makes the Android application open a desktop pagenote project folder and display its PDF source. It establishes the SAF access and manifest-validation boundary for later note, annotation, and asset workflows. Markdown rendering, note editing, PDF text extraction, OCR, and pen input remain required parts of the full Android port and are tracked separately in the parent SPEC.

## Behavior

- The user selects a project directory with Android's Storage Access Framework (SAF).
- The app retains the granted directory permission so the project can be reopened later.
- The project root must contain `project.pagenote`; its JSON fields are decoded using the desktop schema.
- `format` must be `pagenote-project` or `page-notes-project`, `version` must be 1, and the 64-character lowercase hexadecimal project ID is validated.
- `left`, `right`, and optional `preview` links resolve only inside the project's `src/` directory. Traversal above `src/`, absolute paths, and malformed links are rejected before opening a document.
- A PDF source opens with Android `PdfRenderer`; the current page is rendered with the existing 16-megapixel pixel cap and can be changed with previous/next controls.
- The renderer and its file descriptor are closed when the source changes or the activity is destroyed. Replacing a project and disposing its Compose effect must not double-close the same session; closing a session more than once is harmless. Invalid projects and unsupported source types produce a visible error state instead of a blank reader.

## Acceptance

- When a selected project directory contains a valid version-1 manifest whose left source is a PDF, the app shall render page 1 and expose the document page count.
- When the user moves to another page, the app shall render that page and keep the page index within the document bounds.
- When a manifest link escapes `src/`, the app shall reject the project before opening the linked file.
- When the selected folder is reopened after process recreation, the persisted SAF grant shall allow the project to be read again.
- When a project, PDF, or page fails to open or render, the app shall show an actionable error and release opened resources.
- When the current project is replaced and Compose disposes the prior renderer effect, the prior PDF session shall be closed exactly once without crashing; repeated `close()` calls shall be harmless.

## Verification

- JVM unit tests cover manifest decoding, desktop-compatible schema validation, and project-link traversal rejection.
- Android build verifies the `PdfRenderer` and Compose integration compiles.
- An Android instrumentation test opens a valid PDF through `PdfDocumentSession` and verifies repeated close calls do not throw.
- Device acceptance remains required for SAF grants, actual PDF rendering, rotation, and renderer lifecycle.
