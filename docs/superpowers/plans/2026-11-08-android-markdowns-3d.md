# Android Markdown S 3D Renderer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Render the current Markdown S genetic-lab chromosome as a native 3D scene on Android without changing the Markdown file format.

**Architecture:** Keep `MarkdownSState` as the cross-platform data contract. Replace the private Canvas-based `NativeChromosomeView` with a SceneView 2.3.0 / Filament-backed Android `View`. The renderer maps genes to procedural helix geometry and maps gestures back to the existing state callback.

**Spec:** `docs/superpowers/specs/2026-11-08-android-markdowns-3d-design.md`; Markdown S requirements in `docs/markdown-s/`.

## Files

- Modify `android/app/build.gradle.kts`: pin the compatible SceneView artifact.
- Modify `android/app/src/main/java/io/pagenote/android/MarkdownSGeneticLabView.kt`: replace the 2D chromosome illustration with the native 3D host and preserve current state persistence.
- Modify `android/app/src/androidTest/java/io/pagenote/android/MarkdownSNoteRendererTest.kt`: assert 3D scene interactions continue to update only the Markdown fence.
- Update `docs/android/guide/markdown-s.md`, `docs/android/CHANGELOG.md`, `docs/markdown-s/ACCEPTANCE.zh-CN.md`: state the actual milestone and verification evidence.
- Add a runtime screenshot under `docs/android/guide/images/markdown-s/` only if the emulator renders the scene successfully.

## Tasks

### Task 1: Verify and add the native renderer dependency

- [ ] Add `io.github.sceneview:sceneview:2.3.0` to the Android app module.
- [ ] Resolve and compile against the current AGP/Kotlin/compileSdk without upgrading the Android toolchain.
- [ ] If dependency metadata requires an incompatible toolchain, stop retaining this artifact and switch to direct Filament only after proving a compatible setup.

### Task 2: Migrate the chromosome scene

- [ ] Add a SceneView host to chapter 08 and procedurally create two backbone strands, twelve gene base pairs, and shared materials from `MarkdownSState.genes`.
- [ ] Set scene rotation from `angle`; support drag-to-rotate and tap-to-toggle a named gene node; respect `paused` for idle rotation.
- [ ] Feed gene and angle changes through the existing `onStateChange` callback; do not alter the fence protocol or renderer source semantics.
- [ ] Release scene resources and animation work on detach/dispose. If the scene cannot initialize, show an in-card fallback while retaining all Markdown and other experiment controls.

### Task 3: Verify interaction, persistence, and evidence

- [ ] Run the targeted Markdown S notes and Android instrumentation tests; confirm source changes are limited to the matching fence.
- [ ] Build the Android debug app and run the renderer instrumentation on the available emulator/device.
- [ ] Capture a real screenshot of the visible 3D scene and record exact environment/provenance if rendering succeeds.
- [ ] Update the Android guide, Markdown S acceptance record, and Android Unreleased changelog with verified behavior and explicit unverified items.

## Review Focus

- A View rebuild or navigation must not leave a Filament render loop or animator alive.
- Tap selection must distinguish the twelve gene nodes; drag must rotate without accidentally changing a gene.
- `angle` and `paused` must round-trip through the original fence and must not alter unknown JSON fields or surrounding Markdown.
- Missing GPU/backend initialization must leave source, controls, and save flow usable.
