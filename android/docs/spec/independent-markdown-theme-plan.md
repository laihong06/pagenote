# Independent Markdown Theme Implementation Plan

> Android-only implementation plan for task 48. Implement test-first and update root `CHANGELOG.md` for every push.

## Steps

1. Add policy tests for the `classic`/`paper` values, defaulting, validation, and independent application-reader appearance.
2. Add `markdownTheme` to the ReaderSettings DataStore transaction and settings UI; cover persistence/reopen, bad stored values, save, and cancel.
3. Introduce a small Markdown palette/type style used only by Markdown source and note content. Apply it consistently to source editor, preview, syntax highlighting, LaTeX fallback, Mermaid, and image placeholders without recoloring the enclosing toolbar/dialog or PDF bitmap.
4. Add API 34 tablet instrumentation for selecting/saving the theme, source/note rendering, and preservation of Markdown text/page revision. Keep a compact visual fixture for classic and paper modes.
5. Run focused JVM tests, API 34 instrumentation, AndroidTest compilation, and `:app:assembleDebug`; record exact results and mark task 48 complete.

## Constraints

- Default is `classic`; no project schema migration.
- Paper colors must maintain readable contrast in both light and dark reader appearance.
- No theme selection may rewrite note contents or PDF pixels.
