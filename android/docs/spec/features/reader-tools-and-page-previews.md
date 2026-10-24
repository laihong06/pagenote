# Reader Pen Rail and Page Previews

## Approved Scope

Port the approved `docs/ui-reference/` reader prototype to native Android 14 for
the Xiaomi Pad 5 Pro / Snapdragon 870. The founder requested a narrower pen box,
approximately 7 mm. Use a stable **44dp visual rail** (6.99 mm at Android's
160dpi reference density), not the prototype's previous 60px rail. Physical
millimetres depend on the device density calibration and are not claimed measured.

- Keep the existing pressure-aware pen, stylus-side eraser, selection, shape
  annotation, undo/redo, page stores and save-conflict protection.
- Add recognizable fountain pen, ballpoint, highlighter, laser and eraser choices
  to the narrow left rail. Ballpoint uses constant pressure and the portable
  `pen` item type. Laser is a temporary pointer, never a saved annotation.
- Reuse existing validated color swatches/custom color and width choices
  (1.5/3/6/10 document units). These are not physical nib millimetres.
- Expose line/arrow/rectangle/ellipse using the existing vector-annotation flow;
  do not add an incompatible triangle item to the portable format.
- The page-counter button opens a right-side preview drawer, not a numeric
  dialog. Render PDF thumbnails lazily at bounded resolution; Markdown pages use
  text previews. Show original page numbers and current-page selection.
- Selecting a page reuses `changePage`, saving the draft first. Save failure
  keeps the original page and the drawer open. Outside tap, Back or close dismisses
  the drawer when no navigation save is in progress.
- No per-Android-version feature branches, new account services or desktop UI
  changes. Update the HTML baseline and keep private screenshots out of Git.

## Implementation Plan

1. Narrow the HTML rail and its popover; add this SPEC and focused tests.
2. Add the native rail as a separate composable, reuse the existing canvas and
   annotation handlers, implement ballpoint and temporary laser behavior.
3. Add bounded PDF thumbnail rendering and a lazy right drawer, replacing the
   numeric page-jump entry. Update the existing navigation tests for the new UI.
4. Build the APK/test APK and run only relevant JVM/Android 14 tablet checks.
   Install with `adb install -r`, never uninstall or clear logged-in app data.
5. Update CHANGELOG and submit to upstream `beta` through the existing open #11
   feature PR. No main promotion, binary publication or README download changes.

## Progress

- [x] Design approved in chat; working in the existing feature checkout as requested.
- [x] HTML rail narrowed and Android tool behavior verified.
- [x] Native pen rail and page-preview drawer implemented.
- [x] Focused verification and APK build recorded.
- [x] Source prepared for the existing upstream `beta` PR #11; no separate duplicate PR.

## Verification Boundary

One existing API 34 tablet AVD is sufficient. No connected physical device was
reported by ADB when this task started. No broad device matrix, Office or real
Rain Classroom account tests are required for this UI slice.

## Verified Results

- `:feature-pen:testDebugUnitTest`: 50 tests pass, including constant-pressure
  ballpoint and non-persistent laser policy. The new policy tests first failed
  against the old tool enum, then passed after implementation.
- Focused app JVM tests: 17 tests pass (page-number validation and annotation
  clipboard/page remapping).
- `:app:assembleDebug :app:assembleDebugAndroidTest --offline` succeeds.
- Seven selected API 34 tablet instrumentation tests pass in the final run:
  rail width/tool/style/shape controls; localized preview and outside dismissal;
  bounded PDF thumbnail rendering; actual PDF reader rail/thumbnail navigation;
  dirty-note save; revision-conflict preservation; ordered Rain SAF insertion.
- Test fixture opening now explicitly acquires and restores its own SAF grant.
  Recovery cleanup is limited to the synthetic fixture. Native thumbnail checks
  use the unmerged semantics tree because the clickable page item merges image
  semantics. No app uninstall or data clear was used.
- Native screenshots show the narrow rail and populated Chinese preview drawer.
  Screenshots remain in ignored local `release/`; private reference images are
  not committed. The HTML remains a local-only design reference, not a new client.
- Self-review only, per the request not to create more agents. No physical width,
  new hardware pen behavior, public release or real Rain account acceptance is claimed.
