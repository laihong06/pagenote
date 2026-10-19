# Android Pen Canvas Input Implementation Plan

## Goal

Cover native stylus event handling with deterministic Android instrumentation
tests and fix the stylus side-button route without changing the project stroke
format or the existing page-store contract.

## Verified Root Cause

Android documents button press and release as generic motion events delivered
to View.onGenericMotionEvent, not touch events delivered to View.onTouchEvent.
PenCanvasView currently handles those actions only in onTouchEvent, so normal
View dispatch does not reach that logic.

The Android Open Source Project also maps the common BTN_STYLUS input to
MotionEvent.BUTTON_SECONDARY. The current implementation only checks
BUTTON_STYLUS_SECONDARY, so it misses that platform mapping.

References:

- https://developer.android.com/reference/android/view/MotionEvent#ACTION_BUTTON_PRESS
- https://developer.android.com/reference/android/view/View#onGenericMotionEvent(android.view.MotionEvent)
- https://source.android.com/docs/core/interaction/input/touch-devices

## Scope

- Add Android instrumentation configuration to feature-pen.
- Verify stylus down/move/up pressure and coordinate mapping through the native
  View.
- Verify cancellation and disabled input do not persist a stroke.
- Verify generic-motion side-button events enter eraser mode and erase the
  topmost existing stroke.
- Recognize both Android stylus-secondary and BTN_STYLUS secondary button
  state while preserving the current gesture's eraser latch.
- Keep physical-device latency, pressure fidelity, palm rejection, and
  hardware-specific reports as separate task 12 acceptance items.

## Implementation Sequence

1. Update this contract and plan before changing code.
2. Add focused instrumentation tests and observe the side-button regression
   fail through normal generic-motion dispatch.
3. Route button events through onGenericMotionEvent and accept both supported
   secondary-button mappings.
4. Run focused pen instrumentation tests on API 34 4 KB, API 35 4 KB, and API
   35 16 KB AVDs. Re-run Android JVM tests, app instrumentation, assembleDebug,
   and the four-ABI 16 KB APK checker.
5. Record the results in the Android task ledger and root changelog, then
   commit and push the feature slice to codex/android-port.

## Implementation Status

- The focused test was first run before the fix and reproduced the side-button
  failure through `dispatchGenericMotionEvent` on all three AVDs.
- `PenCanvasView` now handles stylus button press/release in
  `onGenericMotionEvent` and recognizes both Android button mappings while
  preserving the gesture's eraser latch.
- The focused instrumentation suite passes 3/3 on API 34 4 KB, API 35 4 KB,
  and API 35 16 KB AVDs. The full app suite passes 107/107 on each AVD, the
  Android JVM suite passes 370/370, `assembleDebug` succeeds, and all four ABI
  APKs pass ELF LOAD and 16 KB ZIP alignment checks.
- Synthetic events validate the app logic only. Physical stylus pressure,
  latency, palm rejection, and device-specific button reporting remain open.
- Task ledger and changelog contain the verification result. This slice does
  not claim physical stylus acceptance.

## Acceptance

- A synthetic stylus gesture emits one validated stroke with expected
  normalized coordinates and pressure values.
- ACTION_CANCEL and disabled input emit no stroke.
- ACTION_BUTTON_PRESS delivered with dispatchGenericMotionEvent erases the
  intersecting topmost stroke for both BUTTON_SECONDARY and
  BUTTON_STYLUS_SECONDARY.
- The event fix does not create an eraser annotation or alter stroke/page
  persistence.
- Physical stylus performance and hardware behavior remain explicitly
  unverified until device testing.
