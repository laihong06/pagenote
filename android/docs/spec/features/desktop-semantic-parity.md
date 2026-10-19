# Android Desktop Workflow Semantic Parity

**Status:** Audit baseline plus implementation contracts. This file records behavior gaps that are invisible to a command-ID-only audit. It does not claim those gaps are implemented.

## Scope

Compare user-visible desktop workflows with Android 14/API 34 behavior. Keep the native Android UI, SAF project format, offline OCR, and in-process agent. Do not port desktop host automation or MCP transport. Office editing remains separately tracked by task 36.

## Audited Gaps

| Workflow | Desktop behavior | Current Android gap | Task |
| --- | --- | --- | --- |
| Open a standalone PDF or Markdown file | Open Document accepts `.pdf` and `.md` and imports the selected source | Implemented in task 45: File menu and welcome actions import PDF/Markdown through standard project creation | 45 |
| Change selected annotation style | Color and width controls update the selected annotation | Implemented in task 44: update only the selected property, preserve other fields, and retain the style for future drawing | 44 |
| Paste rich text into a note | Paste as Markdown converts clipboard `text/html` to Markdown | Paste as Markdown reads plain text only | 46 |
| Customize AI prompt modules | AI Settings selects, edits, saves, and resets individual prompt modules | Android exposes endpoint/model/key only; prompt modules are read-only bundled assets | 47 |
| Choose Markdown theme independently | `classic`/`paper` changes Markdown typography and surface independently of app appearance | Android has one reader-wide theme preference | 48 |
| Format with a physical keyboard | Ctrl/Cmd+B, Ctrl/Cmd+I, and Ctrl/Cmd+K invoke bold, italic, and link while editing notes | Android shortcut policy currently handles navigation and save only | 49 |

The earlier `desktop-parity-gap-audit.md` covers command IDs only. It must not be used as proof that command semantics are fully equivalent.

## Contracts

### 44. Selected annotation style

- When exactly one freehand stroke or native PDF annotation is selected and the user applies a color or width, update only that selected item and keep the chosen style active for subsequent drawing.
- When there is no selection, color/width changes affect only subsequent drawing, preserving the existing behavior.
- Persist through the existing page stores; preserve unknown annotation fields and record a page-scoped undo/redo entry. Reapplying the same value is a no-op.
- Invalid values, read-only projects, or stale selection must not partially mutate the page.
- Concurrent edits to other page entries must survive a style update. Deleting an annotation clears selection when it deletes the selected item, and shifts the selected index when it deletes an earlier item.

### 45. Standalone PDF/Markdown import

- Android shall expose an `Open document` action for standalone `.pdf`, `.md`, and `.markdown` files. Because Android work belongs to a standard project, choosing a file opens the existing project-creation flow with a safe name suggestion; the user chooses a SAF parent directory before creation.
- The optional document selector in `New project` shall use the same import contract. Choosing either PDF or Markdown creates/opens a standard project with the source bytes in `src/`, an editable `src/right.md`, `src/assets/`, and a compatible v1 manifest. The manifest preserves the original display name while its relative source link uses the normalized supported extension.
- PDF is validated before and after copy and must contain at least one page. Markdown is copied byte-for-byte without text decoding or rewriting. Empty Markdown is valid.
- Cancelling either picker or an import leaves the current project and the dialog's prior selections intact. Unsupported extensions, unreadable sources, name collisions, and SAF failures report recoverable errors and do not leave a usable partial project; cleanup is attempted in reverse creation order.

### 46. Rich clipboard paste

- When the user explicitly chooses Paste as Markdown and the clipboard has `text/html`, convert supported semantic formatting to Markdown and replace the current selection (or insert at the caret).
- Use a local HTML parser and fixed conversion allowlist. Discard active content, event handlers, style/script payloads, and remote resource loading; never dereference image, stylesheet, or link resources. Preserve safe link destinations and image alt text only.
- If HTML is absent or converts to no useful text, use the current plain-text behavior. Clipboard access remains user initiated and only reads the first clip item.
- Preserve current selection/caret and note-edit history behavior; do not write project files until the normal note save action.

### 47. Prompt module customization

- AI Settings shall expose the same six prompt module IDs as desktop: `system`, `learning`, `page-tutor`, `output`, `tag-edit`, and `learner`, with module selection, editing, save, and reset-to-bundled-default behavior.
- Store overrides locally and independently per module; never rewrite bundled assets, project files, or model credentials. The agent shall use the saved override on its next prompt read/request assembly.
- Validate the module ID and reject blank or over-32768-character text before writing. A rejected save leaves that module's last valid override unchanged. Reset removes only the selected override.
- Preserve the mandatory app safety prefix that treats imported document text as untrusted, even when the editable `system` module changes.

### 48. Independent Markdown theme

- Add a persisted `markdownTheme` preference (`classic` or `paper`) independent from reader appearance (`system`, `light`, `dark`, or Android's existing reader-wide `paper`).
- Apply it only to Markdown source and rendered-note surfaces. `classic` follows the current app reader palette and sans-serif typography; `paper` uses a restrained paper surface, readable serif typography, and theme-aware text/code/formula colors.
- Do not recolor PDF pages, dialogs, toolbars, or other application surfaces. Switching either preference must not mutate Markdown content or the other preference.
- Persist with the reader settings transaction, default to `classic`, and sanitize unknown values to that default without discarding valid sibling settings.

### 49. Markdown formatting shortcuts

- While a writable Markdown editor has focus, Ctrl/Cmd+B, Ctrl/Cmd+I, and Ctrl/Cmd+K shall invoke the same bold, italic, and link transformations as their toolbar commands.
- Require the actual native Markdown editor to own focus, be enabled, writable, and not busy. Do not intercept these keys when a dialog, another text field, preview, or other surface owns focus.
- Reuse existing transformations and preserve selection/caret plus Markdown undo/redo history exactly as the toolbar commands do.

## Verification

- Each task gets focused JVM tests for pure policies and an API 34 tablet AVD test for the user-visible path where applicable.
- Build after each completed implementation slice: compile the affected unit/instrumentation sources and assemble the Debug APK. Do not expand the device/API matrix.
- Re-run the desktop command checklist and update this audit as each gap closes. Full parity remains open while any listed task or task 36 is open.

## Desktop References

- Open: `../../../../index.html`, `../../../../src/main.js`.
- Annotation styling: `../../../../src/annotations.js`.
- Clipboard conversion and editor: `../../../../src/main.js`, `../../../../src/rich-editor.js`.
- Prompt customization: `../../../../index.html`, `../../../../src/main.js`.
- Theme: `../../../../index.html`, `../../../../src/theme.js`.
- Keyboard formatting: `../../../../src/editing.js`.
