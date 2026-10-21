# Android Reader Session Restoration Plan

> Execute in order: SPEC, failing tests, implementation, batched verification, small commits, and fork sync.

**Goal:** Preserve the live reader across Activity recreation and recover its project/page/viewport plus unsaved note draft after Android reclaims the process.

**Architecture:** `ReaderSessionViewModel` owns the live session and PDF renderer. `SavedStateHandle` stores only compact checkpoint metadata. An app-private `AtomicFile` journal stores note drafts by project target, project ID, and page, with the base revision. On restore, reopen SAF and re-read the page before applying the journal entry; retain conflicts and revoked-permission drafts.

**Spec:** `android/docs/spec/features/reader-session-restoration.md`

## Task 1: Normalize and Restore PDF Viewport

**Files:** `PdfViewportBookmark.kt`, `PdfViewportBookmarkTest.kt`, `ReaderSessionRestorationTest.kt`.

1. Add failing capture/restore tests for normalized focus, scale, dimension changes, invalid bounds, and clamping.
2. Implement the pure bookmark conversion beside the existing viewport policy.
3. Run `./gradlew :app:testDebugUnitTest --tests io.pagenote.android.PdfViewportBookmarkTest`.
4. Verify the bookmark remains part of the ViewModel checkpoint after rotation and window resizing.

**Expected:** finite translations are clamped to the new viewport while scale and normalized center remain stable.

## Task 2: Durable Recovery Journal and Checkpoint

**Files:** recovery-record model/store, `ReaderSessionViewModel.kt`, unit and instrumentation tests, `MainActivity.kt`.

1. Write failing tests for journal identity isolation, atomic write/read/clear, matching versus stale base revisions, and checkpoint encode/restore.
2. Implement a small checkpoint using `SavedStateHandle`; keep large note content out of saved-state Bundles.
3. Implement the app-private `AtomicFile` journal keyed by access URI, project ID, and page. Debounce writes on IO and flush the latest draft synchronously from `MainActivity.onStop()`.
4. Add fresh-ViewModel restoration tests with a real saved-state checkpoint and journal record. Test that changed revisions preserve the draft and block writes.
5. Run the focused JVM and instrumentation tests, then compile the debug app.

**Expected:** process restoration recovers the latest saved draft without modifying project notes; stale revisions and missing SAF grants preserve the local record.

## Task 3: Integrate Lifecycle and Explicit Draft Transitions

**Files:** `MainActivity.kt`, `ReaderSessionViewModel.kt`, restoration instrumentation tests.

1. Save the SAF target and current page/viewport/tool checkpoint after successful project open and each relevant state transition.
2. Reopen the saved target after fresh Activity launch only when a checkpoint exists; validate the persisted read grant and preserve an actionable error if opening fails.
3. Apply a matching journal draft only after project notes are read. Keep its original base revision on conflict.
4. Clear only the relevant journal entry after save, confirmed reload/discard, or draft equality. Do not overwrite restored drafts from composition startup.
5. Keep renderer cleanup in the ViewModel and async project/note/pen work in its session scope.
6. Run `ActivityScenario.recreate()` and fresh-ViewModel restoration tests, then the app JVM suite and debug compile.

**Expected:** Activity recreation keeps the same renderer and state; a newly created ViewModel restores checkpoint plus draft and remains protected by existing revision checks.

## Task 4: Batched Integration Verification and Record

**Files:** `android/docs/spec/tasks.md`, `CHANGELOG.md`.

1. Run the focused test set, full Android JVM tests, `:app:connectedDebugAndroidTest` on API 34 and API 35 AVDs, and `:app:assembleDebug`.
2. Run `python3 android/scripts/check_16kb_apks.py` against all four ABI APKs.
3. Perform one batched API 34/API 35 app-state/process-reclaim smoke where available; distinguish OS process-reclaim evidence from `ActivityScenario.recreate()`.
4. Record exact commands and outcomes, commit the completed slice, and push to `fork/codex/android-port` with the Unreleased changelog entry.

**Expected:** Tests/build/alignment checks pass or remaining environment blockers are recorded precisely; no physical-device claim is made from emulator evidence.
