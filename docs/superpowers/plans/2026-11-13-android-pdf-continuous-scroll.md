# Android PDF continuous scrolling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add default-on vertical continuous PDF scrolling in Android while retaining a user-selectable single-page mode and keeping page-scoped notes and annotations aligned.

**Architecture:** Persist a global `continuousPdfScrolling` preference in the existing Reader Settings store. Use a native Compose lazy page list in continuous mode, report the page nearest the viewport center to the reader session, and render only visible/nearby PDF pages. Keep the current single-page `PdfPageViewport` and its `changePage` behavior intact when the setting is off.

**Tech Stack:** Kotlin, Jetpack Compose, Android `PdfRenderer`, Preferences DataStore, existing PageNote PDF/annotation stores.

**Spec:** [2026-11-13-android-pdf-continuous-scroll-design.md](../specs/2026-11-13-android-pdf-continuous-scroll-design.md)

## Global Constraints

- Continuous vertical scrolling is enabled by default; the user can switch to the existing single-page mode.
- Only Android PDF reading changes; Markdown reading, desktop source, and the project file format remain unchanged.
- Use Jetpack Compose and the existing `PdfDocumentSession`; add no PDF dependency.
- Continuous scrolling does not snap once per page, reset zoom, block on note saving, or show a save notice for every crossed page.
- Page bitmaps remain bounded; PDF rendering remains serialized through the existing synchronized renderer.
- UI evidence comes from the horizontal Android 14/API 34 tablet AVD and includes scrolling, page/note synchronization, and the single-page setting.

## Review Focus

- Variable page sizes: page aspect ratio stays stable before and after lazy rendering; cover with `continuousListKeepsMixedPageHeightsAndAspectRatios`.
- Fast fling across distant pages: current-page tracking settles on the centered visible page without a sequence of page-save transitions; cover with `continuousListFlingUpdatesCenteredPageWithoutPageSnapping`.
- Dirty note during scrolling: the outgoing page draft remains recoverable and the next page displays its own note; cover with `continuousScrollPreservesOutgoingDraftAndLoadsTargetNote`.
- Mode change and checkpoint restore: switching modes retains the active PDF page and reopening restores that page; cover with `continuousModeToggleAndCheckpointKeepActivePage`.
- Touch, stylus, and pinch overlap: touch scrolls, stylus ink stays on its page, and pinch zoom remains shared across adjacent pages; cover with `continuousReaderSeparatesTouchScrollFromStylusAndPinch`.

---

### Task 1: Persist the continuous-reading preference

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/ReaderSettings.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/ReaderSettingsStore.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/ReaderSettingsDialog.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Test: `android/app/src/test/java/io/pagenote/android/ReaderSettingsTest.kt`
- Test: `android/app/src/test/java/io/pagenote/android/ReaderSettingsStoreTest.kt`

**Interfaces:**
- Produce `ReaderSettings.continuousPdfScrolling: Boolean = true`.
- Persist it with `continuous_pdf_scrolling` in `ReaderSettingsStore`.
- Display an enabled switch and one-line explanation in the Reader section of `ReaderSettingsDialog`.

- [x] **Step 1: Add the failing defaults and persistence tests**
  - Assert `ReaderSettings().continuousPdfScrolling` is `true`.
  - Assert a saved `false` value survives opening a second store on the same DataStore file.
- [x] **Step 2: Run the focused tests and verify they fail**

  Run from `android/`: `ANDROID_HOME=/Users/broccoli/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests io.pagenote.android.ReaderSettingsTest --tests io.pagenote.android.ReaderSettingsStoreTest --console=plain`

  Expected: compilation/test failure because the setting is not defined or persisted.
- [x] **Step 3: Add the default-on setting, DataStore key, and localized Reader Settings switch**
  - Use `continuousPdfScrolling` and `continuous_pdf_scrolling` exactly.
  - Keep the switch in the existing app-wide Reader Settings store, matching the reference's user-level preference.
- [x] **Step 4: Run the focused settings tests and verify they pass**
- [x] **Step 5: Commit the setting change**

### Task 2: Build the lazy vertical page-list primitive

**Files:**
- Create: `android/app/src/main/java/io/pagenote/android/PdfContinuousPageList.kt`
- Create: `android/app/src/main/java/io/pagenote/android/PdfContinuousPagePolicy.kt`
- Test: `android/app/src/test/java/io/pagenote/android/PdfContinuousPagePolicyTest.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/PdfContinuousPageListTest.kt`

**Interfaces:**
- `internal data class PdfVisiblePage(val pageIndex: Int, val topPx: Int, val heightPx: Int)`.
- `internal object PdfContinuousPagePolicy { fun activePage(visiblePages: List<PdfVisiblePage>, viewportCenterPx: Int): Int? }`.
- `internal data class PdfPageNavigationRequest(val pageIndex: Int, val revision: Int)`; increment `revision` for explicit page-jump requests so scroll-driven active-page updates do not retrigger navigation.
- `@Composable internal fun PdfContinuousPageList(pageCount: Int, request: PdfPageNavigationRequest?, onActivePageChanged: (Int) -> Unit, pageContent: @Composable LazyItemScope.(Int) -> Unit, modifier: Modifier = Modifier)`.

- [x] **Step 1: Add policy tests**
  - `activePageUsesThePageCenterNearestViewportCenter` checks mixed heights and a viewport center between pages.
  - `activePageReturnsNullWhenThereAreNoVisiblePages` checks the empty layout state.
  - `activePageBreaksExactTiesTowardTheLowerPageIndex` keeps page tracking deterministic.
- [x] **Step 2: Run the policy test and verify it fails**

  Run from `android/`: `ANDROID_HOME=/Users/broccoli/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests io.pagenote.android.PdfContinuousPagePolicyTest --console=plain`

  Expected: compilation/test failure because the policy does not exist.
- [x] **Step 3: Implement center-page selection in `PdfContinuousPagePolicy`**
  - Compare visible page centers to the viewport center; use the lower page index for exact ties.
- [x] **Step 4: Run the policy test and verify it passes**
- [x] **Step 5: Add Compose tests before implementing the list**
  - `continuousListFlingUpdatesCenteredPageWithoutPageSnapping` scrolls past at least three tagged page items and checks the active index.
  - `continuousListKeepsMixedPageHeightsAndAspectRatios` verifies items with different page heights keep their individual measured sizes.
  - Verify a changed `PdfPageNavigationRequest` brings its target page into view.
- [x] **Step 6: Run the Compose tests and verify they fail because the list is missing**
- [x] **Step 7: Implement the stable-key `LazyColumn`**
  - Use `items(pageCount, key = { it })`.
  - Derive active page with `PdfContinuousPagePolicy.activePage` from visible item centers and the viewport center.
  - Animate to `request.pageIndex` only when `request.revision` changes.
  - Use the reference's default continuous-mode page margin (2.2 dp) with no extra list-edge padding.
- [x] **Step 8: Run focused policy and Compose tests and verify they pass**
- [x] **Step 9: Commit the list primitive**

### Task 3: Connect continuous pages to the Android reader

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/PdfDocumentSession.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/ReaderSessionViewModel.kt` only if the page/scroll checkpoint needs a small scoped extension.
- Test: `android/app/src/androidTest/java/io/pagenote/android/PdfContinuousScrollingActivityTest.kt`

**Interfaces:**
- `PdfContinuousPageList` owns scroll position and emits the active page; `MainActivity` owns project-scoped loading and the page-specific callbacks.
- `PdfDocumentSession.pageSize(pageIndex: Int): PdfPageSize` supplies stable page geometry before the bitmap is loaded; `PdfPageSize` contains `widthPx` and `heightPx` from `PdfRenderer.Page` dimensions.
- `MainActivity` creates a new `PdfPageNavigationRequest` for explicit previous/next, page-jump, and keyboard navigation. Scrolling updates `pageIndex` without issuing another request.

- [x] **Step 1: Add the failing reader-flow cases**
  - `continuousScrollPreservesOutgoingDraftAndLoadsTargetNote` scrolls between two note-backed PDF pages and checks the active page and its note.
  - `continuousModeToggleAndCheckpointKeepActivePage` toggles single/continuous mode and restores the active page from the reader checkpoint.
  - `continuousReaderSeparatesTouchScrollFromStylusAndPinch` checks scroll, page-scoped ink, and shared pinch behavior.
- [x] **Step 2: Run the new focused API 34 tablet instrumentation class and verify it fails for the missing feature**

  Run from `android/`: `ANDROID_HOME=/Users/broccoli/Library/Android/sdk ANDROID_SERIAL=emulator-5558 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.pagenote.android.PdfContinuousScrollingActivityTest --console=plain`

  Expected: the cases fail because the reader still displays one page at a time.
- [x] **Step 3: Expose stable page dimensions and render visible/nearby page items lazily**
  - Serialize page rendering through `PdfDocumentSession`.
  - Keep bitmap ownership scoped to composed page items or a bounded cache; recycle images when evicted/disposed.
  - Feed each page's own annotation overlay and ink strokes into its `PdfPageViewport` instance.
- [x] **Step 4: Wire reader page focus and explicit navigation**
  - In both wide and stacked reader layouts, choose `PdfContinuousPageList` only for PDF projects when `settings.continuousPdfScrolling` is true.
  - Keep the existing single-page viewport path untouched when the preference is false.
  - Update toolbar page number, note page, and page-scoped reader state from the centered visible page without resetting list position or waiting for the renderer to switch pages.
  - Route previous/next, page-jump, and keyboard navigation through `PdfPageNavigationRequest` in continuous mode.
  - Preserve dirty note drafts and queue outgoing saves without blocking scroll or emitting a save message on every crossed page.
  - Preserve the current pinch scale across page items; touch scrolling and stylus ink must be handled by their respective existing paths.
- [x] **Step 5: Run the focused API 34 tablet instrumentation class and verify all cases pass**
- [x] **Step 6: Commit the reader integration**

### Task 4: Record and submit the UI change

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `docs/android/CHANGELOG.md`
- Modify: `android/docs/spec/features/reader-page-navigation.md`
- Create/modify: Android horizontal-tablet before/after screenshots and their README index.

**Interfaces:**
- Acceptance IDs: `PDF-CONT-01` through `PDF-CONT-06` from the design spec.
- PR target: existing root `beta` PR #36; no release or merge.

- [x] **Step 1: Capture real reader screenshots** on `Pixel_Tablet_API34_Acceptance`, Android 14/API 34 ARM64, landscape 2560 × 1600 at 320 dpi. Include continuous scrolling across pages, active page/note synchronization, and the single-page setting; record runtime commit.
- [x] **Step 2: Update the root and Android changelogs and the Android navigation guide** with mode behavior and any remaining limits.
- [x] **Step 3: Run Android unit tests, the focused horizontal-tablet instrumentation class, and `git diff --check`**
- [x] **Step 4: Build the latest local debug APKs into `/Users/broccoli/Project/pagenote/android/build/apk-latest`** and record checksums/build info.
- [x] **Step 5: Update PR #36 with acceptance IDs, screenshots, test results, APK details, and unverified limitations; push commits to the existing PR branch.**
