# Android Markdown Renderer Library Evaluation

> Research date: 2026-10-18. This is an Android-only decision record; it does not change the shared desktop documentation or current runtime dependencies.

## User Outcome and Constraints

- Android must preserve the desktop Pagenote Markdown document semantics while keeping the reader and editor native Kotlin/Compose.
- Markdown image references to project assets must stay inside the SAF-authorized project tree. Remote image URLs remain blocked even when the AI client has network access.
- The app minimum remains Android 14/API 34. Every bundled native library must pass the existing four-ABI and 16 KB page-size checks.
- Mermaid must render offline and must not cause text, formulas, or neighboring blocks to overlap.
- Library popularity is a discovery signal, not proof of production fit; the 69-page imported project remains the compatibility fixture.

## Candidates

| Area | Candidate | Strengths | Risks / gaps | Decision |
| --- | --- | --- | --- | --- |
| Android Markdown baseline | [Markwon](https://github.com/noties/Markwon) 4.6.2 | Android-native CommonMark/Spannable renderer; image, table, syntax highlighting, editor, and LaTeX extensions; Apache-2.0; about 3.4k GitHub stars | Latest upstream release is 4.6.2 and release activity is old; Mermaid and Pagenote SAF policy require app integrations. The actual imported page 12 shows a Mermaid block overlapping following note text, so this integration is not accepted yet. | Keep as the compatibility baseline while fixing and testing the Mermaid integration. Do not replace it solely based on library age. |
| Compose Markdown alternative | [Enriched Markdown](https://github.com/software-mansion/enriched-markdown) 0.3.0 | Kotlin/Compose Android package; MIT; CommonMark/GFM, block/inline images, optional LaTeX and code highlighting; active upstream and about 1.2k stars | Mermaid is not documented as supported. Public Android API documents remote-image headers but no custom project-asset loader hook. Its optional math engine adds native ABIs and must pass our 16 KB checks. | Keep as a migration candidate for an isolated compatibility spike, not the production renderer yet. |
| Compose Markdown alternative | [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer) | Compose/Kotlin Multiplatform, Apache-2.0, about 1.1k stars, custom components, optional Coil 2/3 image adapters | No built-in full LaTeX renderer or Mermaid support; still requires Pagenote-specific block and asset behavior. | Useful fallback if a Compose renderer is required; not a complete replacement. |
| Image pipeline | [Coil 3](https://github.com/coil-kt/coil) | Kotlin/Compose-oriented, Apache-2.0, about 11.9k stars, extensible fetcher/decoder pipeline and caching | A general image loader does not define Pagenote's SAF authorization or offline policy. Markwon's upstream Coil adapter targets an obsolete Coil generation. | Prefer Coil 3 only where an image-loader pipeline is needed; require a Pagenote scheme/SAF fetcher or retain the existing controlled handler. Do not add a network module by default. |
| Image pipeline | [Glide](https://github.com/bumptech/glide) | Long-established Android image loading and caching library, about 35k stars | More View/RecyclerView-oriented than this Compose app; does not supply Markdown or Pagenote URI policy. | Recognized mature option, but not preferred for new Compose-specific integration. |
| LaTeX engine | JLatexMath Android through Markwon `ext-latex` | Already integrated and renders natively without a WebView. | Upstream Android repository identifies GPL-2.0. The shipped artifact metadata and any Classpath Exception must be reviewed before distribution; Markwon's Apache license does not remove this dependency obligation. | Keep only as current baseline pending an explicit license audit. |
| LaTeX engine | [RaTeX](https://github.com/erweixin/RaTeX) 0.1.14 | MIT; native Kotlin/Canvas/JNI Android artifact; KaTeX-compatible math; active release history; Android README lists arm64-v8a, armeabi-v7a, and x86_64 | Newer and less proven than established math engines; 32-bit x86 is not shipped; the AAR contains native code and its ELF LOAD alignment must be checked on every packaged ABI for 16 KB compatibility. Syntax and visual fidelity against Pagenote's formula corpus remain unverified. | Preferred candidate for a focused formula/ABI/license spike, not an approved replacement yet. |

GitHub star counts are approximate repository metadata observed on the research date; they are not used as a substitute for behavior, maintenance, licensing, security, or ABI validation.

## Current Stack and Decision

`android/app` currently uses Markwon 4.6.2, its JLatexMath extension, Markwon image spans, AndroidSVG 1.4, and a Pagenote-owned SAF image scheme handler. Mermaid is rewritten to a private image scheme, rendered with a bundled offline WebView, then decoded to a native SVG drawable. This division keeps PDF and Markdown UI native and keeps project-asset access policy under Pagenote control.

Keep that stack as the baseline while fixing the observed Mermaid layout defect. Do not introduce Enriched Markdown, Coil, or RaTeX into the production dependency graph until an isolated proof covers the 69-page project, its table/attachment/math/diagram cases, text selection, low-width tablet layout, offline behavior, and APK 16 KB ABI checks. The app's tested behavior takes precedence over library novelty.

## Required Compatibility Gate

For each renderer or math-engine migration candidate:

1. Render pages 1, 12, 35, and 69 from a disposable copy of the imported 69-page project; do not modify the user's source project.
2. Cover local SAF attachments, missing/corrupt assets, inline and display math, GFM tables, long Chinese paragraphs, and Mermaid diagrams with surrounding text.
3. Verify text selection, scroll position, narrow note-pane width, and failure fallback on API 34 and API 35 virtual tablets.
4. Verify no remote image request occurs and all local image access uses the selected project's authorized tree.
5. Run `assembleDebug`, the focused and full Android tests, plus APK/AAB 16 KB ELF/ZIP checks for all packaged ABIs.
6. Record the chosen dependency versions, notices/licenses, ABI coverage, and known gaps in Android SPEC before adopting the candidate.

## Sources

- [Markwon README and license](https://github.com/noties/Markwon) and [release list](https://github.com/noties/Markwon/releases)
- [Software Mansion Enriched Markdown](https://github.com/software-mansion/enriched-markdown) and [Android API documentation](https://github.com/software-mansion/enriched-markdown/blob/main/packages/enriched-markdown-android/README.md)
- [Mike Penz multiplatform Markdown renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)
- [Coil](https://github.com/coil-kt/coil) and [image pipeline documentation](https://github.com/coil-kt/coil/blob/main/docs/image_pipeline.md)
- [Glide](https://github.com/bumptech/glide)
- [JLatexMath Android license and integration](https://github.com/noties/jlatexmath-android)
- [RaTeX](https://github.com/erweixin/RaTeX), [Android integration](https://github.com/erweixin/RaTeX/blob/main/platforms/android/README.zh-CN.md), and [releases](https://github.com/erweixin/RaTeX/releases)
