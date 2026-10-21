# Note Text Selection to AI Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a revision-bound AI review from selected Markdown note source and expose it safely to the Android Agent.

> Device-scope update: current support is Xiaomi Pad 5 Pro on Android 14/API 34. Previously completed API 35/16 KB matrix results are historical evidence; future device reruns use the current API 34 target proxy only.

**Architecture:** Track the native note editor's UTF-16 selection, build a validated immutable review draft, and pass the existing shared `ReviewCreate` contract through the SAF store. Agent review reads expose bounded list summaries and one-record details; applied status is authorized only after a successful changed-page note write in the same dispatcher run.

**Tech Stack:** Kotlin, Jetpack Compose, Android `EditText`/`AndroidView`, kotlinx.serialization JSON, JUnit, AndroidX Compose instrumentation, Gradle.

**Spec:** `android/docs/spec/features/note-selection-ai-review.md`

## Global Constraints

- Android minimum API remains 34; do not rely on API-35-only PDF text extraction for note-editor selection.
- Preserve the existing `.pagenote` format and `src/ai-reviews.json` compatibility.
- Never create a PDF character anchor from OCR or block-level bounds.
- Every push includes an Unreleased `CHANGELOG.md` entry; no binary release is implied.
- User-facing strings are localized in English and Simplified Chinese.

## Review Focus

- Reversed, empty, whitespace-only, out-of-range, and supplementary-character selections must not produce invalid anchors.
- An external note edit between draft capture and review save must reject the stale review instead of associating it with newer text.
- Large quote/context/anchor data must fail visibly before any SAF write; no truncated quote may disagree with offsets.
- A failed or no-op note write, or a write to another page, must not authorize `applied`.
- Dismissing/deleting a review must leave the note content and sibling pages unchanged.

---

### Task 1: Selection Draft Contract

**Files:**

- Create: `android/app/src/main/java/io/pagenote/android/NoteSelectionReview.kt`
- Test: `android/app/src/test/java/io/pagenote/android/NoteSelectionReviewTest.kt`

**Interfaces:**

- Produces `NoteSelectionReviewDraft.capture(page: Int, context: String, start: Int, end: Int): NoteSelectionReviewDraft` with `quote`, `context`, `page`, `revision`, and JSON `anchor`.
- Produces `NoteSelectionReviewDraft.requireCurrentPage(currentPageText: String)` to reject stale context before persistence after the user edits the review draft.
- Throws `IllegalArgumentException` for invalid page/range, whitespace-only quote, quote over 100,000 UTF-16 code units, context over 1,000,000, or quote/offset mismatch.

- [x] Write failing tests for exact quote/offsets, reversed/empty/invalid ranges, whitespace, supplementary characters, limits, and stale-page rejection.
- [x] Run `./gradlew :app:testDebugUnitTest --offline --tests io.pagenote.android.NoteSelectionReviewTest` and observe the expected missing API failure.
- [x] Implement the pure draft contract using `PageNotes.revision(context)` and `coordinateSystem=markdown-source-utf16`.
- [x] Re-run the focused test and then all app JVM tests.

### Task 2: SAF Review Persistence

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/ProjectReviewStore.kt`
- Modify: `android/app/src/androidTest/java/io/pagenote/android/ExistingProjectSafTestProvider.java`
- Modify: `android/app/src/androidTest/java/io/pagenote/android/ExistingProjectSafRoundTripTest.kt`

**Interfaces:**

- `ProjectReviewStore.create(input: ReviewCreate): JsonObject` persists all supported fields with desktop-compatible limits.
- `ProjectReviewStore.find(id: String): JsonObject?` returns a single record without altering unknown fields.

- [x] Add instrumentation assertions for selection record JSON round trip and quote/context/revision/anchor retention, including preservation of unknown fields.
- [x] Compile/run the focused test and observe failure before production edits.
- [x] Implement `ReviewCreate` persistence and exact-ID lookup in the SAF adapter.
- [x] Run the focused provider test and existing SAF round-trip tests.

### Task 3: Note Editor Selection and Review UI

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Create: `android/app/src/androidTest/java/io/pagenote/android/NoteSelectionReviewUiTest.kt`

**Interfaces:**

- `NotePanel` reports a nullable half-open selection range from its native EditText and clears it when the page/content surface changes.
- `ReviewListDialog` accepts an optional `NoteSelectionReviewDraft`; when present, it shows the quote and creates `ReviewCreate(side="note", kind="selection", scope="page", ...)` after a latest-note revision check.
- The dialog applies `NoteSelectionReviewDraft.requireCurrentPage` against the latest `ProjectNoteStore` content immediately before writing the review.
- The existing `selection_to_annotation` command is enabled only for a valid selected note range, a writable project, and an idle reader; it saves an unsaved note before opening the draft.

- [x] Add instrumentation tests for EditText selection callback, menu disabled/enabled behavior, quote rendering, cancel without review creation, and stale-note rejection before save.
- [x] Run the focused instrumentation test and observe the expected missing behavior.
- [x] Implement selection tracking, menu wiring, localized feedback, and selection-aware review dialog creation.
- [x] Run focused UI tests and all Android unit tests.

### Task 4: Agent Context and Apply Guard

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/ProjectAgentTools.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/ProjectReviewStore.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/test/java/io/pagenote/android/ProjectAgentToolsTest.kt`
- Create: `android/app/src/androidTest/java/io/pagenote/android/ReviewHandoffUiTest.kt`

**Interfaces:**

- `read_review` accepts optional `id`; list mode returns at most 20 summaries with 160-code-unit instruction and 320-code-unit quote previews, ID mode returns exact quote/context/revision/anchor.
- Run-local rewrite evidence is populated only by successful `write_note`/`patch_note` results whose page revision changed, retaining the write's expected revision.
- `write_review_status(status=applied)` verifies the target review page and captured review revision match that evidence; the model-provided `noteRewritten` flag is not trusted.

- [x] Add tests for ID detail formatting, bounded list output, and apply authorization for matching revision/page writes plus no-op, failed, stale-revision, and unrelated-page writes.
- [x] Run focused tests and observe expected failure.
- [x] Implement rich review reads, changed-page tracking, and same-run apply enforcement; remove the manual `applied` control.
- [x] Run focused tests and all app/Agent JVM tests.

### Task 5: Build and Device Verification

**Files:**

- Modify: `android/docs/spec/features/note-selection-ai-review.md`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

- [x] Run full JVM tests, `:app:compileDebugAndroidTestKotlin`, and `:app:assembleDebug`.
- [x] Historical focused selection/review matrix completed on API 34 and API 35 16 KB AVDs; this is evidence only, not a current testing gate.
- [x] Record physical-device and PDF/Office selection limitations without treating them as completed.
- [x] Update the Android SPEC checklist and root changelog, then commit and push the feature slice.
- [x] 2026-10-19 API 34 AVD focused rerun: `NoteSelectionReviewTest` and `ProjectAgentToolsTest` 21/21; `NoteSelectionReviewActivityTest` 2/2; selection-review SAF round trip 1/1; `:app:assembleDebug` succeeded.

### Task 6: Final Review Hardening

**Files:**

- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/NoteSelectionReview.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/ProjectReviewStore.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/ProjectAgentTools.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/RecoverableDocumentWrite.kt` only if needed to support accurate review-write errors
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `android/app/src/androidTest/java/io/pagenote/android/ExistingProjectSafTestProvider.java`
- Modify: `android/app/src/androidTest/java/io/pagenote/android/ExistingProjectSafRoundTripTest.kt`
- Modify: `android/app/src/androidTest/java/io/pagenote/android/NoteSelectionReviewUiTest.kt`
- Modify: `android/app/src/test/java/io/pagenote/android/ProjectAgentToolsTest.kt`
- Modify: `android/docs/spec/features/note-selection-ai-review.md`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

**Interfaces:**

- Selection readiness distinguishes invalid/missing selection, oversized quote/context, and availability so the menu can show a localized size-limit explanation while preserving project/read-only/busy reasons.
- Review store reads at most 64 MiB UTF-8; Agent detail output is at most 8 MiB UTF-8; list metadata is validated against the SPEC bounds before serialization.
- Every review mutation supplies its original JSON snapshot to `RecoverableDocumentWrite`; providers that support sibling creation get a populated recovery copy, original-content recheck, and rollback on failed overwrite. An explicitly unsupported create capability retains best-effort overwrite behavior; inability to determine capabilities fails closed. Strict UTF-8 decoding rejects malformed source bytes before mutation. Oversized selections are rejected before text materialization, first-time empty review files are initialized without an unnecessary backup, and cleanup failures are surfaced/localized without misreporting successful mutations.

- [x] Add tests first for size-specific menu readiness/reason, review read/detail/metadata limits, and provider-driven partial-write restoration/retained-copy behavior; expected failures were observed before implementation.
- [x] Implement localized selection-size feedback and bounded review data handling with tests passing.
- [x] Reuse the existing SAF recovery helper for review writes; the real test provider verifies restoration and backup cleanup/retention, strict UTF-8 rejection, and surfacing a backup retained after successful target write. Error messages identify review JSON rather than calling it a note write.
- [x] Run all app and Agent JVM tests (175/175 and 56/56), Android test Kotlin compilation, `assembleDebug`, and focused instrumentation tests: four classes 33/33 on API 34 4 KB, API 35 4 KB, and API 35 16 KB AVDs.
- [x] Update task tracker/SPEC verification and changelog with observed results.
- [x] Commit `cff73e4` and push to the personal `codex/android-port` branch after independent review; reviewer reported no remaining findings.

### Task 7: Activity Selection-Review Integration Gap

**Files:**

- Create: `android/app/src/androidTest/java/io/pagenote/android/NoteSelectionReviewActivityTest.kt`
- Modify: `android/docs/spec/features/note-selection-ai-review.md`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

**Acceptance:**

- When a user selects text in a dirty note and invokes the selection-review command in `MainActivity`, the Activity shall save the page against its captured revision before showing the review dialog; a successful path shall persist the updated note and exact selection review fields.
- When the SAF fixture changes the note after the Activity captures its base revision, the command shall leave the dialog closed, preserve the external note, and write no review record.
- Tests shall exercise the real Activity menu handler and native editor while explicitly not claiming system picker, persisted-grant, or cross-process CAS coverage.

**Steps:**

- [x] Add two Activity instrumentation tests first: dirty-save success creates an exact review record; stale-save conflict leaves the external note and review JSON unchanged.
- [x] Compile the tests; fix one test-only API mismatch (`PageNotes.pageAt` -> `NotePage.pageAt`) before runtime.
- [x] Run on API 34/API 35 4 KB and API 35 16 KB tablet AVDs. Initial success-path run reached the review dialog but Compose `performTextInput` could not request focus; switched to the existing instrumentation pattern `AccessibilityNodeInfo.ACTION_SET_TEXT`. Both tests then passed on all three AVDs.
- [x] Ruling: the existing production Activity handler already satisfied the new acceptance criteria, so no production code change was needed; the value of this slice is durable regression coverage. The fixture seeds the Activity-scoped reader state and therefore does not claim system picker, persisted grant, or cross-process CAS coverage.
- [x] Independent review tightened the conflict test: it now requires the exact `PageNoteConflictException` message and confirms the local dirty draft remains in memory, rather than treating any status as proof of a conflict.
- [x] Reran both tightened tests on API 34 4 KB, API 35 4 KB, and API 35 16 KB AVDs; all 2/2 tests pass on each device.
- [x] Freshly rerun app/Agent JVM suites (175/175 and 56/56), compile Android instrumentation sources, and build `assembleDebug`.
- [x] Run the four-ABI APK checker; arm64/x86_64 ELF LOAD segments and all APK ZIP alignments pass the 16 KB checks.
- [x] Update observed task/SPEC evidence and Unreleased changelog; independent final review found no remaining serious issues after tightening the conflict assertion.
- [x] Commit `2e80782` and push this test/spec slice to `fork/codex/android-port`; no public release or PR is part of this task.
