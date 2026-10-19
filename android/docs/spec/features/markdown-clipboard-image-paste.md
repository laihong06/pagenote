# Android Markdown Clipboard Image Paste

## Scope

Add a `Paste image` command to the Insert menu for writable Markdown page notes. The command reads the foreground system clipboard only after an explicit user tap, imports the first supported `content://` image URI into the active project's `src/assets/`, and inserts a project-relative Markdown reference at the captured editor selection.

The feature reuses the existing image importer and note editor flow. Supported inputs remain PNG, JPG/JPEG, GIF, and WebP, with the existing 200 MiB input and 24-megapixel decoded-image limits. It does not add clipboard polling, network access, a new project-format field, annotation-canvas paste, SVG support, or image compression.

## Behavior

- The command is enabled only for a writable Markdown note editor when the reader is not busy. It shares the existing image insertion disabled reason.
- Clipboard contents are inspected only after the user taps the command. Select the first clipboard item with a hierarchical `content://` URI, a nonempty authority, and a supported image MIME type. Do not query other URI schemes or later items after a supported image is found. Ignore text, HTML, file/opaque URIs, unsupported MIME types, and items without a URI.
- If no supported image is available, show a localized status and leave the editor, project assets, selection, and draft unchanged.
- Capture the active project identity/source directory, page, editor instance, Markdown snapshot, and UTF-16 selection before importing. Consume the clipboard URI immediately; never persist it.
- Reuse `ProjectImageImporter` validation, private temporary staging, UUID asset naming, and SAF cleanup. When the clipboard provider has no display name, derive a safe fallback extension from its validated MIME type and use a generic `clipboard image` alt label.
- Insert `![clipboard image](assets/<uuid>.<ext>)` through the existing Markdown reference helper, replacing the captured selection and placing the caret after the reference. Do not auto-save the note.
- If the project, page, editor, or draft changes during import, reject the insertion and delete the unreferenced imported asset where possible. Report its generated name if cleanup fails.
- Preview, save, and reopen behavior remains the existing offline project-image flow.

## Acceptance (EARS)

- When the user taps `Paste image` with a supported clipboard image in a writable page note, Android shall import it into the active project's `src/assets/` and insert a relative Markdown reference at the captured selection.
- When the clipboard has no supported image, Android shall show a localized status without changing the note draft or project assets.
- When clipboard access, image validation, or SAF import fails, Android shall leave the note unchanged and clean up any partial asset; if cleanup fails, Android shall identify the residual asset by its generated name.
- When the project, page, editor, or draft changes during import, Android shall not insert the reference into the new context and shall attempt to remove the imported asset.
- When the note is saved and reopened, Android shall retain the reference and render the imported image offline.
- When the editor is absent, read-only, in preview mode, or busy, Android shall disable the command.

## Verification Scope

- JVM: verify supported MIME selection, first-match behavior, rejection of non-content URIs/unsupported items, and MIME-derived fallback names.
- Android 14/API 34 tablet AVD: one focused MainActivity/SAF flow using an injected clipboard `ClipData`; no system chooser and no second device/API matrix.
- Build: affected focused JVM test, the single API 34 AVD instrumentation method, and `:app:assembleDebug`.
- The AVD is a proxy only; Xiaomi Pad 5 Pro hardware validation remains in parent SPEC task 12.
