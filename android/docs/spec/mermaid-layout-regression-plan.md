# Android Mermaid Layout Regression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure an asynchronously rendered Mermaid block in Android Markdown reserves its actual vertical space and never paints over surrounding note text.

**Architecture:** Keep the existing native TextView/Markwon path and isolated offline Mermaid WebView. First reproduce the imported page-12 layout at the tablet note-pane width and trace the async drawable/span measurement boundary; then make one minimal production change at the layer proven to be wrong. Do not migrate the Markdown renderer as part of this fix.

**Tech Stack:** Kotlin, Jetpack Compose AndroidView, Markwon 4.6.2, AndroidSVG 1.4, Android instrumentation tests, Pixel Tablet API 34/35 AVD.

**Spec:** `android/docs/spec/features/note-rich-render.md`; library decisions: `android/docs/spec/renderer-library-evaluation.md`.

## Global Constraints

- Android `minSdk` remains 34.
- Mermaid scripts and sanitization stay bundled and offline; no external requests or JS bridge.
- Project Markdown images remain restricted to Pagenote's SAF-authorized assets; do not enable remote image loading.
- Keep the original 69-page PDF/project unmodified; use only the disposable imported test copy or synthetic regression text.
- Preserve source text, surrounding Markdown order, and formula rendering on failure or re-layout.
- Commit this feature only after its regression test, full Android tests, APK build, and all-ABI 16 KB checks pass; update root `CHANGELOG.md` in the same commit.

## Review Focus

- Long Chinese paragraphs before and after Mermaid at narrow tablet note-pane width do not paint beneath the SVG.
- The async loading placeholder-to-SVG transition does not change source order or erase later formulas/text.
- A Mermaid parse/render failure remains readable and does not reserve an unbounded height.
- Rebinding the same Markdown, navigating away/back, and recomposition do not leave stale drawable bounds or duplicate diagrams.
- Mermaid line breaks and SVG intrinsic dimensions are measured in the same pixel coordinate system as the host TextView.

---

### Task 1: Reproduce and fix Mermaid block overlap

**Files:**
- Modify: `android/app/src/androidTest/java/io/pagenote/android/MarkdownMermaidPreviewTest.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MermaidPreviewHost.kt` or the specific Markwon image/span integration proven responsible by investigation
- Modify: `android/app/src/main/java/io/pagenote/android/MermaidSvgMediaDecoder.kt` if the failing evidence identifies SVG drawing bounds as the source
- Modify: `android/docs/spec/features/note-rich-render.md`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: the existing `MermaidPreviewHost.setMarkdown`, `NotePreviewRewrite.forPreview`, `MermaidRenderCoordinator`, and bundled Mermaid renderer.
- Produces: a passing instrumentation regression named `mermaidBlockDoesNotOverlapFollowingParagraphAtTabletNotePaneWidth`, asserting the rendered SVG block's measured/drawn region is disjoint from preceding and following Markdown text after async load.

- [x] Capture the disposable imported project's page-12 baseline on the Pixel Tablet API 35 AVD; record the 1115x1272 preview host and 856x332 diagram plus the visible overlap.
- [x] Add the named instrumentation regression using the page-12 graph, long Chinese paragraphs, formulas, and tablet note-pane width. The pre-fix drawable-bound assertion failed as expected with 15,553 painted pixels outside the declared bounds.
- [x] Trace the failure through the asynchronous Markwon image span and `MermaidSvgDrawable.draw()`; the SVG Canvas was not clipped to drawable bounds. Record the root cause in `note-rich-render.md`.
- [x] Clip SVG drawing to the drawable bounds and retain the asynchronous `TextView` re-measure after intrinsic drawable dimensions resolve; offline image policy and renderer remain unchanged.
- [x] Run the focused regression on API 35 and API 34; run the full 66-test app instrumentation suite on each AVD. The automated `NotePanel` pixel regression verifies the imported page-12 content shape at tablet note-pane width. Manual inspection of original-project pages 1, 35, and 69 remains under task 12.
- [x] Run `:app:testDebugUnitTest`, `:feature-notes:testDebugUnitTest`, and the full Android instrumentation suite on API 34 and API 35; run `:app:assembleDebug` and the four-ABI 16 KB APK checks.
- [x] Update the feature/task docs and Unreleased changelog with only verified behavior.

Repository: commit as `fix(android): prevent Mermaid preview overlap` and push to `fork/codex/android-port` after the checked acceptance gates pass.
