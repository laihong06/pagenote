# New Project with Optional PDF

## Scope

Extend Android's existing New project flow so a learner can choose the output folder and optionally select a PDF source document. The PDF is copied into the new project before it is opened; the `.pagenote` v1 schema remains unchanged. This supports blank-page PDFs as useful sources for subsequent notes and annotations. It does not create a PDF from scratch.

## User Story

As a learner, I can create a project around a selected PDF, including a blank-page PDF, or keep creating an empty Markdown project when I do not select a document.

## Behavior

- Keep the project name and destination folder required. Add an optional PDF selector and a remove/clear action when a PDF is selected. Show the selected display name. Filter the system picker to `application/pdf`.
- Selecting or canceling either SAF picker must keep the creation dialog open and preserve the name, destination folder, and other selection. Replacing a selected PDF and canceling the picker keeps the previous selection.
- Keep read access to a selected PDF across Activity recreation by taking the system-granted persistable read permission. Release that grant when the selection is cleared, the dialog is dismissed, or the PDF has been copied into the project. Never write the external URI into the project manifest.
- With no PDF selected, preserve the existing v1 Markdown project structure: `src/left.md`, `src/right.md`, `src/assets/`, and `project.pagenote` with `left: "src/left.md"`.
- With a PDF selected, copy its bytes to `src/document.pdf`, create `src/right.md` and `src/assets/`, and write `project.pagenote` with `left: "src/document.pdf"`, `leftName` set to the selected display name, and `right: "src/right.md"`. Omit the unused `src/left.md` in this mode. The note format and all other project fields remain v1-compatible.
- Validate the copied PDF with Android `PdfRenderer` before writing the final manifest; require at least one page. A valid one-or-more-page PDF with blank content is accepted. Empty, malformed, encrypted/unreadable, or zero-page files fail without opening a partial project.
- Write the manifest last. If any create, copy, validation, or write step fails, delete created project children in reverse order where supported, keep the dialog selections available for retry, and report any residual project name if cleanup fails. A same-name project is never overwritten.
- After successful creation, open the new project through the existing `ProjectOpenTarget` flow and render page 1. The source is now owned by the project and remains available if the original PDF is moved or deleted.

## Acceptance (EARS)

- When the user creates a project without selecting a PDF, Android shall create and open the existing empty Markdown project shape.
- When the user selects a readable PDF and a destination folder, Android shall copy the PDF into the new project, set the v1 manifest source fields, and open the PDF at page 1.
- When the selected PDF contains one or more blank pages, Android shall accept it as a valid source and allow page notes and annotations to be added afterward.
- When either picker is canceled, Android shall keep the creation dialog and all previously entered or selected values unchanged.
- When the selected PDF cannot be read or rendered, Android shall report the failure, leave no usable partial project, and preserve the dialog state for correction or retry.
- When a project name already exists, Android shall refuse creation without overwriting the existing project or its files.
- When the Activity is recreated while a PDF selection is pending, Android shall restore the selection and retain read access until copy, removal, or dismissal.

## Verification Scope

- Focused JVM tests cover the picker/dismissal state policy and source-manifest selection for PDF versus Markdown projects.
- One Android 14/API 34 tablet AVD activity test covers selecting a PDF, canceling/replacing a selection without losing dialog state, importing and directly opening a valid blank one-page PDF, verifying copied bytes/manifest/page count, creating the no-PDF Markdown variant, and a malformed-PDF cleanup path. Test pickers may inject SAF results; tests remain inside the app and fixture provider.
- Run `:app:assembleDebug` after the focused checks. Do not run full Android suites, additional API/device matrices, or the 16 KB AVD. The AVD is a proxy; Xiaomi Pad 5 Pro hardware acceptance remains part of task 12.

## Not Included

- Creating a blank PDF in the app.
- Importing Word, PowerPoint, images, or other source formats through this creation dialog.
- Linking to the original PDF URI without copying it into the project.
- Changes to the desktop project format or project version.
