# Offline OCR Native Runtime Compatibility Plan

**Goal:** Make bundled OCR run on Android 14+ and the official Android 15 16 KB ARM64 AVD without changing OCR results, project format, or network policy.

**SPEC:** `android/docs/spec/features/offline-ocr.md`.

## Historical Failure (Resolved)

- The seven-test OCR instrumentation suite starts on the official Android 15 16 KB ARM64 AVD (`getconf PAGESIZE = 16384`) and its first `OfflineOcrEngine.create()` call terminates the process with `SIGILL`.
- Tombstone: `libopencv_java4.so`, Build ID `5429c4ab527a597e1faf1cfee1673954863e2021`, relative PC `0x168e258`. NDK `llvm-objdump` decodes this address as `rdvl x0, #1`.
- The caller reads `AT_HWCAP2` (`getauxval(26)`), tests bit 1, then calls `RDVL`. The AVD's aux vector contains `AT_HWCAP2 = 0x1a03f105383` and `/proc/cpuinfo` lists SVE/SVE2, yet the advertised `RDVL` traps. The same native build crashes on the Android 15 4 KB ARM64 AVD.
- Official OpenCV 4.11.0 also contains `RDVL`. Official 4.10.0 does not, but its ARM64 OpenCV and C++ runtime `LOAD` segments are 4 KB aligned. No physical ARM64 claim is made.

## Decision

Keep the existing PaddleOCR models, ONNX Runtime, Kotlin pipeline, and public `OfflineOcrEngine` boundary. Build a project-owned OpenCV Android AAR from OpenCV 4.10.0 source commit `71d3237a093b60a27601c20e9ee6c3e52154e8b1` using NDK r28 or newer, JDK 17 for its upstream Gradle 7.6.3 wrapper, Android compile/target SDK 34 for the pinned AGP 7.3.1 packagers, NEON as the ARM64 baseline, and no TBB runtime CPU probe. Disable IPP: OpenCV 4.10.0's bundled 32-bit x86 IPPICV archives fail to link with the pinned NDK r28 `ld.lld` (`.note.gnu.property` data is too short), and IPP is not required by the OCR pipeline. Statically link the selected OpenCV modules into the JNI library so the AAR does not depend on separately packaged OpenCV module `.so` files. Preserve the Java/JNI APIs used by the pipeline, but exclude OpenCV's `NativeCameraView.java`: it is the only generated Java source that requires the omitted `videoio` module and is unrelated to OCR. Package the four ABIs currently emitted by the app. Do not patch machine code or catch/translate `SIGILL`.

The pinned upstream build compiles `NativeCameraView.java` even when the `videoio` module is excluded; this produces 18 Java compile errors for `VideoCapture`, `VideoWriter`, and `Videoio`. The source-set exclusion must be applied to both OpenCV's SDK-internal Android library template and its final Maven AAR template because both Gradle projects compile the copied Java sources. Its AGP 7.3.1 AAPT2 also fails to parse the installed Android 35 `android.jar`, so both packagers must compile against SDK 34. The 32-bit x86 JNI link also fails with the bundled IPPICV archive's malformed `.note.gnu.property`, so IPP must be disabled. An initial ARM64-only shared-module build produced a JNI wrapper with `DT_NEEDED` references to three unbundled OpenCV module libraries, so the final configuration must use static OpenCV modules and verify that the AAR's JNI wrapper has no OpenCV `DT_NEEDED` entries.

The supported target AVD must not require the SVE path. If a source build still contains an SVE `RDVL` path, treat that as a failed build configuration rather than relaxing the runtime test. The tradeoff is that SVE-capable physical devices use the portable NEON path for this OCR dependency.

## Tasks

1. [x] Build the native AAR from the pinned OpenCV source with reproducible script/configuration, SDK 34 packager settings, IPP disabled, the narrow camera-example exclusion in both Gradle templates, statically linked OCR modules, and retained upstream license notices.
2. [x] Add a native-artifact check that rejects `RDVL` in the ARM64 OpenCV library and verifies every ELF `LOAD` alignment is at least 16 KB.
3. [x] Replace the Maven AAR with the project-built artifact without changing PaddleOCR or ONNX model assets; retain upstream license notices and record dependency provenance.
4. [x] Run the seven real-model OCR instrumentation tests on API 34, API 35 4 KB, and API 35 16 KB ARM64 AVDs. Bundled Chinese/English recognition, region coordinate mapping, bitmap ownership, and explicit failure behavior pass on all three.
5. [x] Run JVM tests, all app/module compilation and debug assembly, then all four ABI APK 16 KB checks. Runtime execution and static alignment are recorded separately below.
6. [x] Run strict offline OCR acceptance on an API 35 / 16 KB AVD with airplane mode on, Wi-Fi and mobile data disabled, and no active default network.

## Acceptance

- When an API 34+ ARM64 AVD initializes the OCR engine, the process shall not crash and both bundled PP-OCRv6 models shall initialize offline.
- When given the bundled Chinese/English fixture, OCR shall recognize the expected key text and preserve confidence/coordinates and the caller-owned bitmap.
- Region OCR shall only analyze the selected crop and map boxes back into page coordinates.
- `feature-ocr` instrumentation shall report and execute a nonzero number of tests on 4 KB and 16 KB ARM64 AVDs.
- All packaged 64-bit native `LOAD` segments and APK native-library entries shall pass the existing 16 KB checker; all four configured ABI APKs shall build.
- No test or implementation may treat process `SIGILL` as a recoverable empty OCR result.

## Current Status

The native runtime remediation is implemented and emulator-validated. `android/scripts/build_opencv_android.py` builds OpenCV commit `71d3237a093b60a27601c20e9ee6c3e52154e8b1` with NDK `28.2.13676358`; the committed AAR is 33 MB with SHA-256 `abbe71f57e31364ae9cb04d907ff50b4dc0a91306c5e35cfaf175cc79a51b060`. It contains all four configured ABIs. The ARM64 `libopencv_java4.so` and `libc++_shared.so` LOAD segments are all 16 KB aligned; JNI has no OpenCV-module `DT_NEEDED` entries; ARM64 disassembly has no `RDVL`.

The seven OCR instrumentation tests pass on API 34 / 4 KB, API 35 / 4 KB, and API 35 / 16 KB ARM64 AVDs. The complete app suite passes 71/71 on API 34 and 71/71 on API 35 / 16 KB. JVM tests, `assembleDebug`, `assembleDebugAndroidTest`, and all four APK 16 KB checks pass. A LaTeX drawable test's async wait was increased from 2 to 10 seconds after it timed out once under the full 16 KB suite; the isolated retry and subsequent complete suite pass. `android/scripts/test_offline_ocr_avd.sh` additionally ran all seven OCR tests on the 16 KB AVD after confirming `Active default network: none`, then restored the original emulator radio settings.

Not yet verified: physical ARM device behavior. The larger Android migration goal remains in progress.
