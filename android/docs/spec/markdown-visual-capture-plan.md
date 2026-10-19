# Markdown Preview Visual Capture Implementation Plan

**Goal:** Add one-command Android 14/API 34 AVD capture for the rich Markdown preview used on the Xiaomi Pad 5 Pro target.

**Architecture:** A test-only Compose instrumentation fixture drives the existing `NotePanel` Preview tab with one rich-content case, waits for async drawables and a matching rendered frame, and saves one PNG to app-specific external storage. A shell wrapper installs app/test APKs on a single API 34 AVD and pulls the image into the Android build reports directory. This avoids the connected-test Gradle task uninstalling the target app and deleting its screenshot before collection.

**Tech Stack:** Kotlin, Jetpack Compose UI instrumentation, Android `Bitmap`, Bash, Gradle connected instrumentation.

**Spec:** `features/markdown-visual-capture.md`, `features/note-rich-render.md`.

## Global Constraints

- Add no production UI, test route, or runtime dependency. The capture suite may include a narrowly scoped rendering fix when a real preview defect is exposed.
- Preserve offline image behavior; the fixture must not request remote resources.
- Current device validation is Android 14/API 34 only; Android 15, 16 KB, other OEMs, and phone layouts are out of scope for this slice.
- Screenshot outputs are generated under ignored `android/build/`, never committed.
- Run one instrumentation test on one API 34 emulator after installing the app/test APKs only to that serial.
- Fail closed if the selected AVD is not API 34 or if the target serial is unavailable.

## Review Focus

- Formula, image, and Mermaid asynchronous work in the single rich-light fixture must finish before the screenshot is accepted.
- The fixture must show successful formula and Mermaid rendering; renderer-level tests remain responsible for invalid-input fallbacks.
- The selected AVD's output path must remain isolated and deterministic.
- Screenshots must contain only the rendered Compose surface, not time-varying system chrome.
- A disconnected device or missing screenshot must fail with a useful message.

---

### Task 1: Add the reusable rich-preview capture test

**Files:**
- Create: `android/app/src/androidTest/java/io/pagenote/android/MarkdownPreviewVisualCaptureTest.kt`
- Create: `android/app/src/androidTest/java/io/pagenote/android/MarkdownPreviewCaptureFixtures.kt`
- Test: One instrumentation method asserts PNG decoding, dimensions, settled renderer states, and the Mermaid raster budget.

**Interfaces:**
- Consumes: `NotePanel`, `NotePreviewRewrite`, `MermaidPreviewHost`, existing Markwon/LaTeX renderers.
- Produces: one rich-light PNG in `targetContext.getExternalFilesDir("markdown-preview-captures")`.

- [x] Keep one rich-light instrumentation fixture for representative preview coverage.
- [x] Assert missing output, invalid PNG, wrong dimensions, and incomplete async states fail the focused instrumentation test.
- [x] Add asynchronous-ready checks and a helper that captures the Compose root without system bars.
- [x] Save one rich-light PNG and validate it decodes with the expected content dimensions.
- [x] Wait for the valid Mermaid drawable to reach a matching `onDraw` frame before screenshot capture.
- [x] Rasterize Mermaid SVG into a max-width, 4,194,304-pixel, 16,384-pixel-edge-bounded `BitmapDrawable` after the screenshot suite exposed translated-span rendering that left SVG output invisible.
- [x] Fold the large-SVG 4,194,304-pixel cap assertion into that same instrumentation test.

### Task 2: Add the one-command AVD wrapper

**Files:**
- Create: `android/scripts/capture_markdown_preview_avds.sh`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: one explicitly selected API 34 emulator and its app-specific screenshot file.
- Produces: one PNG in `android/build/reports/markdown-preview/<serial>/`.

- [x] Build both APKs, then explicitly install them to the selected API 34 AVD with adb -s.
- [x] Run exactly one focused capture test and pull its screenshot into the selected serial-named directory.
- [x] Fail clearly if no API 34 target AVD is selected or the screenshot is missing/invalid.
- [x] Run shell syntax validation and the wrapper on one API 34 AVD; the focused test passes and produces one screenshot.
- [x] Record the result in Android SPEC task 5 and the root Unreleased changelog.
- [x] Commit the complete feature slice.
