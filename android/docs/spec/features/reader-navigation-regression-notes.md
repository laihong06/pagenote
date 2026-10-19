# Reader Navigation Regression Notes

Record-only note for PDF reader page navigation. No new verification was run for this note; it summarizes the existing spec, policy, and prior test evidence for regression triage.

## Sources audited (read-only)

- Spec / EARS: `android/docs/spec/features/reader-page-navigation.md`
- Instrumented gesture test (read-only, not modified): `android/app/src/androidTest/java/io/pagenote/android/PdfPageViewportNavigationTest.kt`
- Pure swipe policy under test: `android/app/src/main/java/io/pagenote/android/PdfPageSwipePolicy.kt`
- JVM policy tests: `android/app/src/test/java/io/pagenote/android/PdfPageSwipePolicyTest.kt`
- Unified page-change path: `ReaderHome.changePage` in `android/app/src/main/java/io/pagenote/android/MainActivity.kt` (`changePage`, `changePageFromSwipe`, header prev/next call sites)
- Tracking entry: `android/docs/spec/tasks.md` item 39.

## Regression checklist

1. Single-finger vertical swipe threshold
   - Accept only vertical movement >= 72 dp and >= 1.35x horizontal movement (`MIN_VERTICAL_DISTANCE_DP`, `VERTICAL_DOMINANCE`).
   - Up swipe advances exactly one page; down swipe returns exactly one page.
   - Short swipes, diagonal/horizontal drags, NaN/non-finite deltas, and non-positive density must not navigate.
   - Regression signal: page changes on a short or diagonal drag, or threshold no longer scales with display density.
2. Stylus / multi-finger / selection modes must not mis-trigger
   - Stylus input, multi-pointer gestures, note-pane scrolling, OCR/text selection, annotation drawing, and active modal flows do not navigate pages.
   - The AVD viewport test asserts a horizontal drag produces no swipe direction; JVM tests assert short/diagonal inputs return null.
   - Regression signal: pen, pinch, selection drag, or modal interaction flips pages.
3. First/last page does not wrap
   - `targetPage` returns null at the matching boundary; swipe and prev/next controls are no-ops there.
   - Header prev/next buttons are disabled at the respective boundary or while a page transition is blocked.
   - Regression signal: first-page previous or last-page next wraps or changes state.
4. Unified `changePage` and draft save
   - Swipes (`changePageFromSwipe`), header prev/next buttons, menu commands, and page-jump dialog all route through the same `changePage` path.
   - Accepted page change saves the current note first, preserves page-scoped state behavior, then resets the viewport to fit-to-page (`PdfViewportBookmark.DEFAULT`).
   - Blocked transitions (input locked, selection mode, import/export busy, out-of-range target, save failure) must not switch pages or lose the current draft.
   - Regression signal: swipe path bypasses note save, leaves stale viewport/OCR state, or diverges from button/menu behavior.

## Prior evidence cited (not re-run here)

- JVM: `PdfPageSwipePolicyTest` 4/4 covering direction mapping, short/diagonal rejection, density-scaled threshold, and boundary policy.
- AVD: API 34 tablet reader swipe plus adjacent toolbar-button flow 5/5 referenced by `android/docs/spec/tasks.md` item 39.
- Companion build/test-source compilation and `:app:assembleDebug` evidence is recorded in the tracking entry; this note adds no new build, test run, or verification claim.

## What to re-check when this area changes

- Re-read `reader-page-navigation.md` EARS before touching swipe gating, boundary handling, or header buttons.
- Keep `PdfPageViewportNavigationTest.kt` read-only intent intact: vertical swipe navigates one page, horizontal drag does not.
- Any change to `changePage` gating, draft save ordering, or viewport reset needs the focused JVM plus the named API 34 AVD test, not a spec-only update.
