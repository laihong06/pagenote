# Reader Interaction Enhancements Plan

## Baseline and Constraints

- Target is the existing native Android app, minimum API 34, with the Pixel Tablet API 34 AVD as the only routine device acceptance target.
- Preserve `project.pagenote`, `annotations.json`, pressure data, PDF source bytes, page-note revision checks, and the existing page-change/save flow.
- Keep all Android SPEC documents under `android/docs/spec/`; do not move them into desktop documentation.
- Each functional slice gets focused tests, a Debug build, its own commit, and an Unreleased `CHANGELOG.md` entry. Push source only; this plan does not authorize a public release.

## Ordered Slices

1. Page navigation: add a pure swipe policy and tests; wire vertical touch swipes and header previous/next buttons through `ReaderHome.changePage`; add localized labels and one API 34 reader-flow test.
2. Ink transforms and margins: extend `PenCanvasGeometry` to map viewport points through the inverse `PdfViewportTransform` and render/hit-test with the forward transform; make the ink layer viewport-sized; add the persisted `ReaderSettings.allowInkOutsidePage`; add geometry, settings, codec, and focused View tests.
3. Page preparation: add a pure forward-window policy; route Page Tutor source loading through a source-revision-keyed session cache; reuse full-page OCR via `ProjectPreparedCache`; add `prepareAheadPages` settings and a cancellable sequential Compose queue; test window transitions, cache hits, revision changes, and cancellation.
4. Dwell recognition: add a pure `PenShapeRecognizer`, endpoint dwell timer and preview to `PenCanvasView`; submit recognized geometry through the existing full-page native annotation writer/history (not `ProjectPenStore.freehandItems()`); add deterministic recognizer/converter unit tests and focused View/reader-flow instrumentation.

## Expected Files

- Navigation: `MainActivity.kt`, `PageJumpPolicy.kt` or a new `PdfPageSwipePolicy.kt`, `strings.xml`, Android unit/instrumentation tests.
- Ink/settings: `feature-pen/PenCanvasGeometry.kt`, `PenCanvasView.kt`, `ReaderSettings.kt`, `ReaderSettingsStore.kt`, `ReaderSettingsDialog.kt`, settings/geometry tests and strings.
- Preparation: `ReaderSettings*`, `PageTutorSourceLoader.kt`, `ProjectPreparedCache.kt`/`ReaderOcrSelection.kt`, a small core-reader window policy, `MainActivity.kt`, unit tests and strings.
- Shape recognition: a pure `PenShapeRecognizer.kt`, native vector conversion helper near `PdfAnnotationVectorDraft.kt`, `PenCanvasView.kt`, `MainActivity.kt`, and `feature-pen`/app unit and Android tests.

## Verification and Delivery

- Before each slice, add the smallest failing JVM or instrumentation test, then implement until it passes.
- Run affected module JVM tests and `:app:assembleDebug`; compile Android test sources when the test source changes. Run only the named API 34 AVD test for UI/View integration.
- Run `git diff --check`, commit one slice at a time, and push the completed commits to the existing fork `beta` branch. Do not alter Mac packages, stable downloads, or publish a release.
