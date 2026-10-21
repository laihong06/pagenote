# Android Reader Viewport

## Scope

Add predictable zoom and pan controls to the native PDF viewport. The PDF and its pen overlay share one transform so annotation coordinates remain aligned with rendered content. This feature does not change the normalized annotation or PDF text coordinate contracts.

## Interaction

- A page opens fit-to-viewport at 100% zoom.
- Two-finger gestures zoom and translate the page around the gesture centroid. Gesture movement is ignored unless at least two pointers are down, leaving single-pointer contact available to the native pen canvas.
- Toolbar actions zoom in/out, fit the page width, and reset to fit-to-viewport.
- Zoom is clamped to 1x..6x. Translation is clamped to the scaled page bounds so no blank area is exposed beyond the page edge.
- Changing PDF pages resets the viewport to fit-to-viewport.
- The bitmap and native ink overlay receive the same transform and clipping viewport.

## Acceptance

- Pure transform tests verify centroid-preserving scale, pan, scale limits, translation limits, fit-width scale, and reset behavior.
- Android compilation verifies the pointer, clipping, and graphics-layer integration.
- Phone and tablet acceptance checks two-finger transform behavior and confirms a stylus stroke remains aligned at each zoom level.

## Verification

- JVM unit tests cover the pure transform function without Android view mocks.
- The debug APK builds for each configured ABI.
- Physical stylus and multitouch behavior remains device acceptance work where no compatible device is attached.
