# Android Formula Preview Rendering Plan

## Goal

Close the task 5.1 evidence gap for actual LaTeX rendering. A parser span or a constructed fallback object alone does not prove that preview rendering works.

## Scope

- Verify valid inline and block formulas produce visible non-transparent pixels through `NoteMarkdownRenderer` and its asynchronous drawable loader.
- Verify invalid TeX reaches the configured renderer error handler, displays a source-preserving `FormulaFallbackDrawable`, and leaves following Markdown visible.
- Change production code only if these integration tests expose a defect.
- Keep Markdown source unchanged; do not change formula syntax, renderer dependency, preview layout, or other rich-content behavior in this slice.

## Acceptance (EARS)

- When valid inline or block LaTeX is rendered, the Android preview shall draw non-transparent formula pixels.
- When invalid LaTeX is rendered, the configured error handler shall return a visible fallback containing the original formula source, and text after the formula shall remain present.

## Tasks

- [x] Update `features/note-rich-render.md` to require actual drawable pixels and renderer-path fallback evidence.
- [x] Add instrumentation tests that exercise the preview renderer and async drawable loader for inline/block formulas.
- [x] Add an integration assertion for invalid-TeX fallback pixels, source preservation, and following text.
- [x] Fix only defects demonstrated by the tests; retain all source text. Existing renderer configuration satisfied the contract; test setup now explicitly drives the LaTeX drawable's loader.
- [x] Run focused instrumentation on API 34 and API 35, the full JVM and instrumentation suites, `assembleDebug`, and four-ABI 16 KB checks. All 61 instrumentation tests pass on each AVD; forced full JVM tests and `assembleDebug` pass; all four APKs pass 16 KB ZIP checks and arm64/x86_64 LOAD alignment.
- [x] Update task status and `CHANGELOG.md`, then commit and push this feature slice.
