# Android Startup and 16 KB Page-Size Compatibility

## Scope

> Current device scope is Xiaomi Pad 5 Pro (Snapdragon 870, Android 14/API 34). The startup-owner fix remains applicable; 16 KB alignment/runtime records below are historical and are not a current debug or device-acceptance gate. Re-enable them only when a future store target/policy requires 16 KB support.

Keep the Android reader launchable when it applies a user-selected locale through a configuration context on the supported Xiaomi Pad 5 Pro / Android 14 target. The historical 16 KB native-library contract below is conditional on a future store policy or target that requires it. This Android-only SPEC belongs under `android/docs/spec/`. Desktop builds and data formats remain unchanged.

## Startup Owner Contract

- `MainActivity` owns the AndroidX activity-result registry used by the SAF project picker.
- When the reader replaces `LocalContext` with a localized configuration context, it must explicitly preserve the original `ActivityResultRegistryOwner` in the Compose tree.
- The project picker remains registered unconditionally during Compose initialization and can still launch after the app has started.
- A missing activity-result owner is a startup failure and must be covered by an app-launch regression test.

## Native Library Contract

- Android Gradle Plugin remains at least 8.5.1 so uncompressed native libraries are ZIP-aligned for 16 KB devices.
- For a future store submission whose target/policy requires 16 KB support, every `arm64-v8a` and `x86_64` library must have ELF `LOAD` segments aligned to at least `2**14` bytes and the APK must pass 16 KB ZIP alignment.
- For that same release gate, validate OpenCV and `libc++_shared.so` from the resolved AAR and final APK; a version number alone is not evidence of alignment.
- Android 32-bit ABI libraries are retained only as build targets; page-size acceptance is based on the 64-bit ABIs supported by Android 16 KB kernels.

## Acceptance (EARS)

- When the app loads saved reader settings and applies a localized configuration context, the Android reader shall retain the hosting activity's result registry and render its home screen without an uncaught exception.
- When the user selects a project, the reader shall continue to launch the SAF tree picker through its registered activity-result launcher.
- When a future public artifact is subject to a 16 KB store policy, the Android build shall package compatible OpenCV/C++ libraries and pass ELF/ZIP alignment checks.
- When the app runs on the supported Xiaomi Pad 5 Pro Android 14 device, reader startup and the SAF picker shall remain functional.

## Verification

- Instrumentation coverage launches the actual `MainActivity`, waits for the localized reader home screen, and fails on the previous `ActivityResultRegistryOwner` crash.
- JVM tests and Android app compilation pass after the context-owner change and OpenCV dependency update.
- Current device smoke testing is limited to launch and project-picker interaction on Xiaomi Pad 5 Pro / Android 14; an API 34 AVD is only a platform proxy.
- ELF/ZIP inspection and Android 15+ 16 KB runtime/OCR suites are future release gates, not routine development tests for the current device target.

## Runtime Evidence (2026-10-18)

- The official Android 15 Google Play ARM64 16 KB tablet AVD reports a 16,384-byte page size. The debug app launches and the latest recorded full app instrumentation suite passes 71/71.
- All seven real-model OCR instrumentation tests pass on API 34 / 4 KB, API 35 / 4 KB, and API 35 / 16 KB ARM64 AVDs. A strict API 35 / 16 KB offline run verified airplane mode, Wi-Fi disabled, mobile data disabled, and `Active default network: none` before the tests started.
- The earlier OpenCV 4.14.0 `RDVL`/`SIGILL` failure described below was resolved by replacing it with the project-built, pinned OpenCV 4.10.0 four-ABI AAR; the replacement passes native execution, OCR recognition, and 16 KB ELF/ZIP checks. Physical ARM64 runtime and real DocumentsUI/project interaction remain unverified.

### Historical Failure (Resolved)

The initial official OpenCV 4.14.0 artifact passed static alignment checks but terminated OCR instrumentation with `SIGILL` at SVE `RDVL` on API 35 4 KB and 16 KB AVDs. The project-built OpenCV 4.10.0 replacement and its provenance are documented in `../offline-ocr-native-runtime-plan.md`; the failure is retained here as historical diagnostic evidence, not current status.
