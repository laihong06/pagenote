# SAF Image Stream Failure Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` to continue this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Keep Android Markdown previews usable when a local image stream fails during decoding.

**Architecture:** Keep `NoteImageMediaDecoder` as the bitmap decoding boundary and return its existing native placeholder for unreadable streams. This increment adds regression coverage only if current behavior already satisfies the contract; it must not introduce redundant production code.

**Tech Stack:** Kotlin, Android `BitmapFactory`, Markwon, Android instrumentation tests.

**Spec:** `android/docs/spec/features/note-rich-render.md`

## Global Constraints

- Android minimum supported API remains 34.
- Project images remain local-only; this change must not add network or filesystem-path access.
- Warning text may contain a MIME label, but not provider exception details, SAF URIs, or local paths.

## Review Focus

- A stream that throws on read must result in a visible warning drawable, not an escaping exception or blank span.
- Malformed but readable images must keep the existing decode-failure placeholder.
- This decoder-level test does not replace a future SAF `ContentResolver` query/open authorization integration test.

---

### Task 1: Pin unreadable stream behavior

**Files:**
- Modify: `android/app/src/androidTest/java/io/pagenote/android/NoteMarkdownRendererTest.kt`
- Modify: `android/docs/spec/features/note-rich-render.md`

**Interfaces:**
- Consumes: `NoteImageMediaDecoder.decode(contentType, inputStream)`.
- Produces: a visible localized decode-failure placeholder when the source stream throws during read.

- [x] Add an instrumentation test passing a throwing `InputStream` to the decoder and assert exact safe warning text and visible drawable pixels.
- [x] Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.pagenote.android.NoteMarkdownRendererTest` on Pixel Tablet API 35; all 9 tests passed. `BitmapFactory` returns the existing null-decode fallback, so no production-code change is needed.
- [x] Run the full Android JVM suite and all 57 Pixel Tablet instrumentation tests; `:app:assembleDebug` succeeds and all four ABI APKs pass the 16 KB checker.
- [x] Update `android/docs/spec/tasks.md` and `CHANGELOG.md`, then commit and push to `fork/codex/android-port`.
