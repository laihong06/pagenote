# Android Reader Menu Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to execute this plan task-by-task.

**Goal:** Reorganize the reader menu using the observed TouchNotes 7.0.3 tablet UI as a reference, while preserving PageNote commands, enabled states, and note/ink behavior.

**Architecture:** Keep the existing command model and callbacks. Present commands in a right-side overlay with Tools, File/Export, and Settings tabs; group existing commands inside each tab, provide icons, and avoid repeating disabled explanations visually while retaining them in accessibility semantics. Do not fabricate reference-only favorites or toolbar customization.

**Evidence:** 2026-11-10, root34 Android 14 emulator, 2560×1600 / 240 dpi, TouchNotes `com.newskyer.draw` 7.0.3, user authorized accepting first-run terms. Observed bookshelf, new note, canvas, right-side Tools/Export/Settings tabs, grouped rows, and toolbar-management mode. Images in `/tmp/ref-menu.png`, `/tmp/ref-export.png`, `/tmp/ref-settings.png`, `/tmp/ref-manage.png` are reference-app captures, not PageNote screenshots.

**Files:** `ReaderAdaptiveMenu.kt` presentation, `ReaderMenuModel.kt` tab mapping, resource strings, existing menu tests and targeted activity tests; changelogs and Android screenshot documentation.

## Tasks

- [x] Add tests for tab reachability, right-side panel, closing and accessibility; check failures against current menu.
- [x] Implement adaptive panel and tab mapping while preserving command IDs and enabled callbacks.
- [x] Verify phone and tablet interactions, take PageNote runtime screenshots, and update local APK in the main project's fixed folder.
- [x] Record results and limitations; reference-app screenshots are not PageNote runtime evidence.

## Progress

- Observed the running reference app's right-side tabs, grouped tool rows and toolbar-management mode. PageNote's File tab includes project operations in addition to export; unsupported reference favorites are not presented as working features.
- Added compact Dialog and expanded right-side panel, retained command IDs and availability, added selected-tab semantics, expandable unavailable reasons and back handling. Review was completed; actual TalkBack focus and announcements have not been tested.
- Phone menu and representative reader checks passed in earlier iterations. Tablet annotation drag verification is being rerun using a tolerance derived from two injected screen pixels after the overlay layout changed the canvas scale.
- Tablet final menu, settings, page navigation, ink and annotation regression: 16/16 passed on Android 14 with the 2560×1600 / 240 dpi override. Earlier complete phone menu/reader run: 21/21; final phone screenshots and local APK update follow. The tolerance adjustment applies only to injected touch coordinates, not storage or undo assertions.
- Final phone menu, screenshot and ink checks: 7/7 passed on the Android 14 1080×2400 / 420 dpi emulator; APK rebuilt to the main project's `android/build/apk-latest/` with signature and SHA-256 verified. Runtime screenshot provenance is recorded in the screenshot README. No physical-device or actual TalkBack run is claimed.
