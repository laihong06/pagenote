# OpenCV Android Runtime

The OCR module uses a locally published Android AAR instead of the official Maven binary because the latter's ARM64 library executes an SVE `RDVL` instruction that traps on the supported Android 15 AVD.

## Provenance

- Upstream: OpenCV `4.10.0`, commit `71d3237a093b60a27601c20e9ee6c3e52154e8b1`
- License: Apache-2.0; see [`LICENSE`](LICENSE)
- Local Maven coordinate: `io.pagenote.thirdparty:opencv-android:4.10.0-pagenote.1`
- AAR SHA-256: `abbe71f57e31364ae9cb04d907ff50b4dc0a91306c5e35cfaf175cc79a51b060`

## Build

Use JDK 17, Android SDK 34+ and NDK r28+. From the repository root:

```sh
JAVA_HOME=/path/to/jdk-17 \
ANDROID_SDK_ROOT=/path/to/Android/Sdk \
python3 android/scripts/build_opencv_android.py
```

The script fetches and verifies the pinned source commit, builds the `core`, `imgproc`, `imgcodecs`, and Java JNI modules for `armeabi-v7a`, `arm64-v8a`, `x86_64`, and `x86`, then writes the artifact to `android/third-party/opencv-maven`. It disables TBB, IPP, and OpenCL; sets an ARM64 NEON baseline without CPU dispatch; uses SDK 34 for the pinned AGP packagers; and excludes only the unrelated `NativeCameraView.java` camera example from both generated Gradle source sets.

## Verification

The checked-in AAR is approximately 33 MB. Its ARM64 JNI and C++ runtime ELF `LOAD` segments are 16 KB aligned, its JNI library has no OpenCV-module `DT_NEEDED` entries, and its disassembly contains no `RDVL`. The OCR instrumentation suite passes on API 34, API 35 4 KB, and API 35 16 KB ARM64 AVDs. Physical-device validation and an explicit airplane-mode run remain outstanding.
