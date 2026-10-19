# Markdown Preview Visual Capture

## Goal

Make Android Markdown preview inspection repeatable without opening the reader
and manually navigating rich content. The capture suite exercises the same
NotePanel preview used by the app on one Android 14/API 34 tablet AVD and
produces one screenshot for visual review.

## Scope

- Test-only tooling; no debug screen or test route is included in production.
- Render through NotePanel, switch to its Preview tab, and retain the existing
  Markwon, LaTeX, Mermaid, image, and offline-warning paths.
- Capture one deterministic light-theme fixture with Chinese text, a table,
  inline/block formulas, highlighted code, Mermaid, a missing project image,
  and a blocked remote image.
- Wait for asynchronous formula, image, and Mermaid drawables to settle before
  capture. The successful Mermaid drawable must reach a matching onDraw frame.
  A timeout fails instead of saving an incomplete screenshot as successful.
- Save one PNG under the target app's external files directory at
  markdown-preview-captures/rich-light.png; the runner pulls it into
  android/build/reports/markdown-preview/<device>/.
- The helper selects exactly one connected API 34 emulator, or the explicit
  TARGET_AVD_SERIAL. It installs the app and test APKs only there, runs one
  instrumentation test, and pulls one screenshot. Other connected devices are
  never targeted.
- Mermaid SVG is rasterized at decode time into a width-bounded bitmap capped
  at 4,194,304 pixels and 16,384 pixels per edge. Batched screenshots exposed that SVG drawables painted
  at the origin could disappear when Markwon drew their spans on a translated
  canvas; rasterization makes the preview render reliably without adding a
  runtime dependency or test route.

## Acceptance

- One instrumentation test renders the rich fixture through the real NotePanel
  preview and saves one readable PNG.
- The image is non-empty, decodes as PNG, has the Compose content width, and
  excludes status/navigation bars.
- The test waits for valid formulas and Mermaid output and verifies the Mermaid
  drawable paints visible pixels in its Markdown span.
- The same test verifies a large SVG raster stays within the 4,194,304-pixel
  bitmap budget.
- Existing renderer assertions remain authoritative for semantic correctness;
  this capture supplements them for visual review and is not a golden baseline.
- The API 34 AVD verifies Android platform behavior only. Xiaomi Pad 5 Pro
  (Snapdragon 870, Android 14/MIUI) must be tested on the physical target before
  claiming target-device acceptance.

## Verification

Run android/scripts/capture_markdown_preview_avds.sh as the sole capture
entry point. It pins installation, instrumentation, and screenshot collection
to one selected API 34 emulator. If multiple API 34 AVDs are attached, set
TARGET_AVD_SERIAL to select one.
