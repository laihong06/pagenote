# Android Startup and 16 KB Compatibility Plan

**Goal:** Remove the reader's reproducible launch crash and package 16 KB-compatible 64-bit native libraries.

> Device-scope update: completed 16 KB/API 35 work below is historical evidence. It is not a current Xiaomi Pad 5 Pro / Android 14 acceptance gate; repeat it only if a future store release policy requires it. Real-device workflow acceptance remains tracked in tasks.md.

**SPEC:** `android/docs/spec/features/startup-and-16kb-compatibility.md`.

**Constraints:** Keep all Android-only planning under `android/docs/spec/`; preserve SAF picker behavior and the existing OpenCV API surface; verify actual ELF and APK alignment; include a matching Unreleased changelog entry in every pushed implementation commit.

## Task 1: Reproduce the Reader Launch Regression

- [x] Add an app instrumentation test that launches `MainActivity` and waits for the localized home screen.
- [x] Reproduce the current APK crash from device logcat: `No ActivityResultRegistryOwner` from `ReaderHome` during settings-backed composition.
- [x] Run `MainActivityLaunchTest#localizedReaderHome_remainsAvailableAfterSettingsLoad` on `Pixel_Tablet_API34`; 1/1 passes and the localized home is displayed. This API 34 AVD result is a platform proxy, not Xiaomi hardware acceptance.
- [x] Commit the test and test-dependency change with an Unreleased changelog entry.

## Task 2: Preserve the Activity Result Owner

- [x] Capture the hosting `ActivityResultRegistryOwner` before constructing the locale-specific configuration context.
- [x] Provide that owner alongside the localized `LocalContext` at the reader composition root.
- [x] Run app JVM tests, compile the instrumentation test, and build all configured APKs.
- [x] Install the ARM64 debug APK and launch `MainActivity` on `Pixel_Tablet_API34`; the home remains visible and no app fatal exception is present. Xiaomi Pad 5 Pro hardware remains unverified.
- [x] Commit the implementation and its changelog entry separately from the regression-test commit.

## Task 3: Replace Misaligned OpenCV Binaries

- [x] Add `android/scripts/check_16kb_apks.py` to inspect every packaged 64-bit ELF `LOAD` segment and APK ZIP alignment with the Android SDK/NDK tools.
- [x] Replace the legacy OpenCV dependency with the project-built, pinned OpenCV 4.10.0 AAR; verify the packaged C++ runtime and all 64-bit ELF segments are 16 KB aligned.
- [x] Build all configured ABI APKs and run all JVM unit-test tasks.
- [x] Inspect every resulting `arm64-v8a` and `x86_64` library with NDK `llvm-objdump -p`; all `LOAD` alignments are `>= 2**14`.
- [x] Run `python3 android/scripts/check_16kb_apks.py <apk>...` on all four APKs. ELF and SDK `zipalign -c -P 16` checks pass.
- [x] Commit the dependency and verified build change with its own Unreleased changelog entry.

## Task 4: Device Smoke Check and Sync

- [x] On `Pixel_Tablet_API34`, install and launch the ARM64 debug APK; confirm the home screen and launch the SAF tree picker from `Open project`. The picker was closed by force-stopping DocumentsUI after Back did not exit its root view; Pagenote returned to foreground. This does not verify normal picker cancellation callback behavior.
- [ ] On Xiaomi Pad 5 Pro / Android 14, verify the app returns normally after dismissing the SAF picker; this remains part of task 12 hardware acceptance.
- [x] Record that the connected device uses 4 KB pages; it does not validate 16 KB runtime behavior.
- [x] Run `testDebugUnitTest`, `assembleDebug`, and `compileDebugAndroidTestKotlin`; `connectedDebugAndroidTest` stopped before executing tests because device installation was cancelled.
- [x] Push reviewed feature commits to `fork/codex/android-port` only after all relevant checks and ensure the pushed range includes the Unreleased changelog updates. Verified 2026-10-19: remote and local both point to `b875b72`; the pushed commits include their Unreleased records.

## Task 5: Android 15 16 KB Runtime Verification

- [x] Install the official Android 15 `google_apis_playstore_ps16k` ARM64 system image and create the dedicated `Pixel_Tablet_16KB` AVD without replacing the existing 4 KB AVDs.
- [x] Confirm `getconf PAGESIZE` reports `16384`; record the Google Play ARM64 image and API 35.
- [x] Build/install the current ARM64 debug APK and launch `MainActivity`; no startup fatal crash was observed. OCR's lazily loaded native runtime is validated separately below.
- [x] Run the full app instrumentation suite on the 16 KB AVD; the latest recorded run passes all 71 tests, including reader/PDF instrumentation.
- [x] Run `feature-ocr` instrumentation with a nonzero test count and require successful bundled Chinese/English recognition. Seven tests pass on API 34 / 4 KB, API 35 / 4 KB, and API 35 / 16 KB; strict offline execution on the 16 KB AVD also passes with no active default network.
- Deferred: opening the SAF picker and rendering a project page on the 16 KB AVD is outside the current device target; revisit only for a release gate requiring 16 KB support.
- [x] Record runtime results separately from ELF/ZIP alignment checks and physical-device validation; root Unreleased changelog is updated before pushing.

**Outcome:** The Android 15 16 KB AVD reports a 16,384-byte page size; app startup and the full 71-test app suite pass. Seven OCR model-integration tests pass, including a strict offline run with no active default network. Real SAF picker interaction and representative user-project rendering remain separate task 12 acceptance items. A zero-test Gradle task is not acceptance evidence.
