# Independent Markdown Reading Theme

## Scope

Add a Markdown-specific `classic`/`paper` style independent from the application reader appearance. This setting changes reading/editing presentation only, never note source text or PDF rendering.

## Behavior

- Persist `markdownTheme` with values `classic` and `paper`, default `classic`, in the existing atomic ReaderSettings DataStore save.
- `classic` follows the app's current surface/text palette and sans-serif typography.
- `paper` applies a warm low-glare reading surface, legible system-serif text, restrained heading/rule colors, and compatible code/formula colors to Markdown source and rendered note content.
- Apply the style to the Markdown source pane and note editor/preview content, not settings dialogs, menus/toolbars, PDF page pixels, or non-Markdown surfaces.
- App appearance (`system`, `light`, `dark`, existing reader-wide `paper`) remains independent. The paper Markdown palette derives contrast from the resolved app light/dark mode.
- Do not transform Markdown source or saved project files when switching themes.

## Acceptance

- Reader settings show a separate Markdown theme control and persist/cancel it with the existing settings transaction.
- Invalid stored values fall back to `classic`; valid neighboring settings survive.
- Both source and note content reflect the selected Markdown style while PDF and surrounding UI preserve their existing palette.
- Theme changes do not change source bytes, page revision, undo history, or saved note contents.
- Text, links, tables, code, formulas, images, and Mermaid previews remain readable in both modes on the API 34 tablet target.

## Verification

- JVM policy and DataStore reopen tests; API 34 tablet settings/render instrumentation; focused Markdown visual regression; AndroidTest compilation; Debug APK assembly; `git diff --check`.
