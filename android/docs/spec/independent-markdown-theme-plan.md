# Independent Markdown Theme Implementation Plan

> Android-only implementation plan for task 48. Implement test-first and update root `CHANGELOG.md` for every push.

## Steps

1. [x] Add policy tests for the `classic`/`paper` values, defaulting, validation, and independent application-reader appearance.
2. [x] Add `markdownTheme` to the ReaderSettings DataStore transaction and settings UI; cover persistence/reopen, bad stored values, save, and cancel.
3. [x] Introduce a small Markdown palette/type style used only by Markdown source and note content. Apply it consistently to source editor, preview, syntax highlighting, LaTeX fallback, Mermaid, and image placeholders without recoloring the enclosing toolbar/dialog or PDF bitmap.
4. [x] Add API 34 tablet instrumentation for selecting/saving the theme and source/note rendering. Confirm editable Markdown remains unchanged; settings and render paths do not write page data.
5. [x] Run focused JVM tests, API 34 instrumentation, AndroidTest compilation, and `:app:assembleDebug`; record results and mark task 48 complete.

## Verification Record

- `ReaderSettingsTest`, `ReaderSettingsStoreTest`, and `MarkdownThemePaletteTest`: 24/24 JVM tests passed, including persistence across DataStore reopen, invalid-value fallback, independent app appearance, and both palette variants.
- `ReaderSettingsMarkdownThemeTest`, `NoteMarkdownRendererTest`, `MarkdownSourcePaneTest`, and `MarkdownMermaidPreviewTest`: 22/22 instrumentation tests passed on `Pixel_Tablet_API34(AVD) - 14`; this includes settings save/cancel, unchanged Markdown source, readable paper code/link styling, and distinct light/dark Mermaid output.
- `:app:assembleDebug` passed. `scripts/check_16kb_apks.py app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` and `git diff --check` passed.
- No PDF renderer input, project note file, revision, or history API is touched by the theme selection or rendering path. Xiaomi Pad 5 Pro hardware was not re-tested in this slice.

## Constraints

- Default is `classic`; no project schema migration.
- Paper colors must maintain readable contrast in both light and dark reader appearance.
- No theme selection may rewrite note contents or PDF pixels.
