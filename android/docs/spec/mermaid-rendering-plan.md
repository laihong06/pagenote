# Android Mermaid Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render Mermaid fenced blocks offline in both Android Markdown reading and note preview, preserving source and surrounding Markdown.

**Architecture:** Rewrite Mermaid fences to a private image scheme consumed by Markwon, preserving the original Markdown block layout. Each native preview host owns one hidden WebView and a serial render queue; the WebView serves only bundled Mermaid assets through `WebViewAssetLoader`, blocks all network and external navigation, and returns SVG through `evaluateJavascript` callbacks without a JavaScript bridge. Markwon decodes the generated SVG into a bounded native drawable; empty/error states use native placeholders that retain the original source.

**Tech Stack:** Kotlin, Jetpack Compose, Android WebView, AndroidX WebKit `1.17.1`, Mermaid `12.2.0` and DOMPurify `3.4.17` browser bundles, AndroidSVG `1.4`, existing Markwon preview.

**Spec:** `android/docs/spec/features/note-rich-render.md`

## Global Constraints

- Android `minSdk` remains 34.
- WebView is used only as a hidden Mermaid rendering engine, with at most one renderer per native preview host; surrounding Markdown and all other app surfaces remain native.
- Mermaid runtime is bundled in the APK; opening or rendering a note must not download scripts, fonts, images, or diagrams.
- WebView file access, content access, file-origin access, external navigation, and JavaScript bridges remain disabled.
- A Mermaid parse/render failure or empty source must remain visible with the original source available.
- Both `MarkdownSourcePane` and the note editor's Preview tab use the same renderer.

## Review Focus

- Fenced code with backticks/tilde and non-Mermaid language remains unchanged and is not split.
- Mermaid source containing quotes, HTML, URL-like text, or JavaScript syntax is passed as data and cannot escape into executable markup.
- Invalid and empty diagrams surface explicit state while preserving source.
- WebView requests outside the APK asset host/path are intercepted; localhost test endpoints receive no request.
- Multiple diagrams render serially and recomposition/source changes do not show stale output.

---

### Task 1: Bundle the fixed renderer

**Files:**
- Create: `android/app/src/main/assets/mermaid/mermaid.min.js`
- Create: `android/app/src/main/assets/mermaid/purify.min.js`
- Create: `android/app/src/main/assets/mermaid/renderer.html`
- Create: `android/app/src/main/assets/mermaid/renderer.js`
- Create: `android/app/src/main/assets/mermaid/LICENSE`
- Create: `android/app/src/main/assets/mermaid/DOMPurify-LICENSE`
- Create: `android/app/src/main/assets/mermaid/VERSION`
- Modify: `android/app/build.gradle.kts`
- Test: `android/app/src/androidTest/java/io/pagenote/android/MermaidRendererAssetTest.kt`

**Interfaces:**
- Produces: `https://appassets.androidplatform.net/assets/mermaid/renderer.html`, served only by `WebViewAssetLoader`, with Mermaid `12.2.0` and DOMPurify `3.4.17` bundled locally.
- Renderer publishes status and request id in `document.title`; it accepts source only through a JSON-escaped function argument. A ready result is retrieved as a JSON-serialized SVG string through `evaluateJavascript`.

- [x] Write an instrumentation asset-contract test for the pinned version, license, local script references, CSP, strict security, and disabled HTML labels; run and observe the expected missing-asset failure.
- [x] Add local renderer asset/license and `androidx.webkit:webkit:1.17.1` dependency.
- [x] Run the asset-contract test and confirm the packaged page references only local scripts and applies the required security settings.

### Task 2: Preserve Mermaid source in a private image URI

**Files:**
- Create: `android/app/src/main/java/io/pagenote/android/MermaidSourceCodec.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/NotePreviewRewrite.kt`
- Test: `android/app/src/test/java/io/pagenote/android/MermaidSourceCodecTest.kt`
- Modify: `android/app/src/test/java/io/pagenote/android/NotePreviewRewriteTest.kt`

**Interfaces:**
- Produces: `MermaidSourceCodec.encode/decode` and `NotePreviewRewrite.forPreview(markdown)`, preserving surrounding Markdown and replacing each closed Mermaid fence with one `pagenote-mermaid:` image reference whose URL-safe Base64 payload contains the original source.

- [x] Write tests for plain Markdown, adjacent diagrams, empty diagrams, alternate fence markers, Mermaid-looking text inside another code fence, and source round-trip through the private image URI.
- [x] Run the focused unit test and confirm it fails because the codec is absent.
- [x] Implement a source-preserving fence rewrite and rerun the focused test.

### Task 3: Secure native Mermaid block view

**Files:**
- Create: `android/app/src/main/java/io/pagenote/android/MermaidRenderCoordinator.kt`
- Create: `android/app/src/main/java/io/pagenote/android/MermaidImageSchemeHandler.kt`
- Create: `android/app/src/main/java/io/pagenote/android/MermaidSvgMediaDecoder.kt`
- Modify: `android/app/build.gradle.kts` (AndroidSVG `1.4`)
- Modify: `android/app/src/main/java/io/pagenote/android/NoteMarkdownRenderer.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/MermaidRenderCoordinatorTest.kt`

**Interfaces:**
- Produces: one attached, hidden WebView per native preview host and a Markwon image scheme for decoded Mermaid SVG; no JS bridge is exposed.

- [x] Add instrumentation tests for valid SVG render, empty source, invalid source preservation, source update ordering, and blocked external request.
- [x] Run the focused tests and verify expected failures before implementation.
- [x] Configure WebViewAssetLoader, exact local-origin/path allowlist, network blocking, strict CSP, and navigation denial.
- [x] Send source via JSON escaping, report render completion via a bounded document-title protocol, and retrieve the SVG only after matching the active request id.
- [x] Bound Mermaid source at 100,000 characters and serialized SVG at 256 KiB, serialize requests, and decode SVG into a drawable constrained to the reader width; render loading/error/empty states natively.
- [x] Run focused tests on Pixel Tablet API 35 AVD.

### Task 4: Integrate both Markdown previews

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/MarkdownMermaidPreviewTest.kt`

**Interfaces:**
- Consumes: `NotePreviewRewrite.forPreview`, the coordinator host, and `NoteMarkdownRenderer.createPreview`.
- Produces: shared native Markdown/diagram rendering used by source reading and editable note preview, with each preview host attaching and disposing its own coordinator/WebView.

- [x] Add Compose test proving Markdown before/after a diagram and diagram state coexist in the same preview.
- [x] Attach and dispose a per-host renderer WebView in both direct Markwon preview hosts, passing the same coordinator into the Markdown image scheme handler.
- [x] Confirm edit mode, note truncation notices, images, formulas, and code spans remain unchanged.

### Task 5: Verify, document, and commit

- [x] Run focused JVM and Android tests, full JVM suite, all Pixel Tablet instrumentation tests, and `assembleDebug`.
- [x] Run APK 16 KB checks for all four ABI splits and inspect rendered SVG pixels plus loading/error/empty AVD states.
- [x] Update `android/docs/spec/tasks.md` and root `CHANGELOG.md` with the verified scope.
- [x] Commit as `feat(android): render Mermaid blocks offline` and push to `fork/codex/android-port`.
