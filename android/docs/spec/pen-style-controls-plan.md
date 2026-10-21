# Android Pen Style Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable desktop-compatible color and width controls for new Android PDF annotations and handwriting.

**Architecture:** Add a pure validated `PenStyle` model in `feature-pen`, expose native Compose dialogs from the existing handwriting menu, and thread the active immutable style to the native canvas and annotation draft factories. Preserve the existing page storage format and never rewrite saved annotations as a side effect of changing style.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, JUnit, Compose Android instrumentation tests.

**Spec:** `android/docs/spec/features/pen-style-controls.md`

## Global Constraints

- The sole hardware target is Xiaomi Pad 5 Pro / Snapdragon 870 / Android 14 (API 34).
- The only routine emulator proxy is one API 34 tablet AVD; do not run connected tests across other devices.
- Desktop width options are exactly `1.5`, `3.0`, `6.0`, and `10.0`; default style is `#dc3545` / `3.0`.
- The color picker includes eight preset swatches and still accepts any valid six-digit RGB hex color.
- No annotation JSON schema change and no mutation of existing annotations when style changes.
- Every source push includes a root `CHANGELOG.md` Unreleased entry; local debug APKs are not public releases.

## Review Focus

- Hex validation rejects short, alpha, and named colors without applying partial input.
- Width choices match desktop exactly and the default remains 3.0.
- The active style reaches freehand, vector, text, and region creation paths while existing item data is untouched.
- Compose dialog dismissal and Activity recreation do not silently reset the style.

---

### Task 1: Pen Style Model and Native Controls

**Files:**
- Create: `android/feature-pen/src/main/java/io/pagenote/feature/pen/PenStyle.kt`
- Test: `android/feature-pen/src/test/java/io/pagenote/feature/pen/PenStyleTest.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/PdfAnnotationVectorDraft.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Test: `android/app/src/androidTest/java/io/pagenote/android/PenStylePickerTest.kt`

**Interfaces:**
- Produces: `PenStyle.DEFAULT`, `PenStyle.WIDTH_OPTIONS`, and `PenStyle.create(color: String, width: Double): PenStyle?`.
- Consumes: the existing `PenCanvasView.penColor` / `penWidth`, annotation draft factory parameters, and handwriting menu commands.

- [x] Add the pure model tests first and run them red.
- [x] Implement color normalization/validation and exact desktop width choices; run the focused JVM test green.
- [x] Implement native swatch/custom-hex and width dialogs, saved style state, and apply the selected style to new freehand and PDF annotations.
- [x] Add one Compose test for selecting a swatch/custom color and width, then draw through `PdfPageViewport` and verify the captured stroke style; compile the Android test source.
- [x] Run focused JVM tests, the one API 34 Compose test on the selected tablet AVD, and build `:app:assembleDebug :app:assembleDebugAndroidTest`.
- [x] Update `android/docs/spec/tasks.md` and root `CHANGELOG.md`, review the diff, commit, and push `codex/android-port` to `fork`.
