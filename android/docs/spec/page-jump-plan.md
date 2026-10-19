# Android Reader Page Jump Plan

**Spec:** `android/docs/spec/features/page-jump.md`

## Task 1: Page-Number Policy

**Files:** `PageJumpPolicy.kt`, `PageJumpPolicyTest.kt`.

- [x] Add failing tests for one-based parsing and invalid/out-of-range values; the focused Gradle test failed because `PageJumpPolicy` did not yet exist.
- [x] Implement a pure parser returning a zero-based target only for valid page numbers.
- [x] Run `:app:testDebugUnitTest --tests io.pagenote.android.PageJumpPolicyTest --offline`; all three focused test cases pass.

## Task 2: Reader Control and Navigation State

**Files:** `MainActivity.kt`, new `PageJumpDialog.kt`, Android string resources.

- [x] Add Activity instrumentation for page-status activation, dirty-note save before a jump, and target-page note/revision loading. RED was observed on the 16 KB tablet AVD: tapping the existing read-only page status did not open the required dialog.
- [x] Implement the accessible page-status button and localized numeric dialog with invalid confirmation disabled.
- [x] Reuse `changePage`; preserve the old page and draft on save failure, and set the target note revision from the target page content.
- [x] Run all three Activity tests on API 34 4 KB, API 35 4 KB, and API 35 16 KB AVDs; each device passes 3/3 with zero skips.

## Task 3: Integration Verification and Fork Sync

**Files:** `android/docs/spec/features/page-jump.md`, `android/docs/spec/tasks.md`, `CHANGELOG.md`.

- [x] Freshly run app/Agent JVM suites (178/178 and 56/56), instrumentation Kotlin compilation, and `assembleDebug`; all pass with zero failures, errors, or skips.
- [x] Run `python3 android/scripts/check_16kb_apks.py` on all four debug ABI APKs; ELF LOAD and ZIP alignment pass, and ARM64 OpenCV has no `RDVL`.
- [x] Record exact AVD API levels/page sizes and test counts in `features/page-jump.md`; no physical-device claim is made.
- [x] Obtain independent review; no actionable issues remain. Dedicated same-page and ordinary I/O-failure Activity tests were judged useful but optional and are not claimed by this slice.
- [x] Commit the SPEC/source/tests/changelog slice as `8cd47e1` and push to the personal fork's `codex/android-port` branch. No public binary release or PR was created.
