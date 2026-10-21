# Page Tutor Preview and Append Implementation Plan

> **For agentic workers:** Implement the tasks in order using test-first development. Keep the feature SPEC authoritative.

**Goal:** Explain the open PDF page through the configured OpenAI-compatible endpoint, preview the Markdown, then append it to that page only after revision validation.

**Architecture:** A pure Agent-module `PageTutor` builds one bounded page-scoped chat request and returns ordinary Markdown without invoking the tool loop. The Android reader prepares PDF/OCR context, presents a preview dialog, and persists an explicitly accepted append through the revision-aware notes editor.

**Tech Stack:** Kotlin, coroutines, existing `ChatClient`/OkHttp client, `PdfRenderer`, bundled offline OCR, Jetpack Compose, JUnit, Compose instrumentation tests.

**Spec:** `android/docs/spec/features/page-tutor-preview-and-append.md`.

## Global Constraints

- Keep Android-only SPECs and plans under `android/docs/spec/`.
- Use at most the current page and its immediate previous/next PDF pages; never assemble a chapter.
- Limit each PDF page's sent text to 20,000 Kotlin string characters and expose truncation in the preview.
- Never persist a model response without explicit user confirmation and a matching target-page revision.
- Keep the user's saved API key in Android Keystore-backed storage; never log or put it in project data.
- Include an Unreleased `CHANGELOG.md` entry in the feature commit pushed to `fork/codex/android-port`.

## Review Focus

- Ordinary Markdown must never be parsed as AgentRunner's `DONE`/`TOOL` protocol; test that PageTutor returns it verbatim.
- OCR failure or empty OCR must not make a model request; test the source policy and surface warnings.
- A delayed response must not overwrite a later edit; test stale `expectedRevision` rejection at append time.
- Long pages must be visibly marked as truncated; test the exact 20,000-character boundary and warning.
- A read-only project may preview but must not expose an enabled append action; cover the disabled state in Compose instrumentation.
- A changed instruction must invalidate append eligibility for the old preview; a failed SAF write must restore the original or leave a named recovery copy when supported. If parent/capability discovery fails, or a provider advertises recovery-copy creation but cannot create/write the copy, abort before touching the target. Only confirmed unsupported recovery-copy capability may use best-effort rollback. Surface filenames whenever cleanup leaves a recovery copy behind.
- Truncation warnings must describe only page text that is actually sent to the model.

---

### Task 1: Page-scoped tutor request and response

**Files:**
- Create: `android/agent/src/main/java/io/pagenote/agent/PageTutor.kt`
- Test: `android/agent/src/test/java/io/pagenote/agent/PageTutorTest.kt`
- Modify: `android/agent/src/main/java/io/pagenote/agent/PageRequest.kt`
- Test: `android/agent/src/test/java/io/pagenote/agent/PageRequestTest.kt`

**Interfaces:**
- `PageTutorRequest(documentId: String, page: Int, totalPages: Int, instruction: String, sourceText: String, noteMarkdown: String, noteRevision: String, sourceWarnings: List<String> = emptyList(), neighbors: List<PageRequest.PageMaterial> = emptyList())`.
- `PageTutorResult(page: Int, noteRevision: String, markdown: String, warnings: List<String>)`.
- `PageTutor(chat: ChatClient).explain(endpoint: ModelEndpoint, request: PageTutorRequest): PageTutorResult`.

- [x] **Step 1: Write failing tests** named `requestContainsOnlyCurrentAndAdjacentPageMaterial`, `capsEachPdfPageAtTwentyThousandCharactersWithWarnings`, `returnsOrdinaryMarkdownWithoutAgentProtocolParsing`, `rejectsBlankSourceBeforeCallingClient`, `rejectsBlankResponse`, `rejectsNonAdjacentNeighborPages`, `avoidsSendingEveryPageOfShortDocuments`, and `singlePageDocumentAllowsPageScopedRequest`.
- [x] **Step 2: Run `./gradlew :agent:testDebugUnitTest --offline`** and verify the new contract tests fail because PageTutor is not implemented.
- [x] **Step 3: Implement request validation and construction** using `PageRequest.build`, a page-tutor system instruction, and the existing `ChatClient`; return the response as Markdown and attach explicit truncation warnings.
- [x] **Step 4: Run `./gradlew :agent:testDebugUnitTest --offline`** and verify the tests pass.

### Task 2: Revision-safe page-note append

**Files:**
- Modify: `android/feature-notes/src/main/java/io/pagenote/feature/notes/PageNoteEditor.kt`
- Test: `android/feature-notes/src/test/java/io/pagenote/feature/notes/PageNoteEditorTest.kt`

**Interfaces:**
- Add `PageNoteEditor.appendPage(latestMarkdown: String, page: Int, addition: String, expectedRevision: String): PageNoteEditResult`.
- The method appends after the existing target-page content, inserts a blank-line separator only when needed, and delegates page/revision integrity to the same contract as `replacePage`.

- [x] **Step 1: Write failing tests** named `appendsToOnlySelectedPage`, `appendsToEmptyPageAndKeepsFollowingEmptyPages`, and `rejectsStaleRevisionWithoutChangingInput`.
- [x] **Step 2: Run `./gradlew :feature-notes:testDebugUnitTest --offline`** and verify the append tests fail before implementation.
- [x] **Step 3: Implement `appendPage`** without changing replacement semantics.
- [x] **Step 4: Run `./gradlew :feature-notes:testDebugUnitTest --offline`** and verify all note editor tests pass.

### Task 3: Platform/PDFBox text and OCR source selection

**Files:**
- Create: `android/app/src/main/java/io/pagenote/android/PageTutorSourceLoader.kt`
- Test: `android/app/src/test/java/io/pagenote/android/PageTutorSourceLoaderTest.kt`

**Interfaces:**
- `PageTutorSourceLoader(nativeText: suspend (Int) -> PdfTextExtractionResult, searchablePdfText: suspend (Int) -> String? = { null }, ocrText: suspend (Int) -> OcrResult, hasBitmap: suspend (Int) -> Boolean = { false })` prefers platform text, then Android 14 PDFBox searchable text. It uses OCR for unavailable/suspicious text and sparse text on image pages; mixed-page output retains both sources and warnings.

- [x] **Step 1: Write failing JVM tests** for platform-text preference, searchable-PDF fallback before OCR, sparse mixed-page source retention, scanned-page OCR fallback, and OCR warning/failure handling.
- [x] **Step 2: Run only the affected `PageTutorSourceLoaderTest` cases** and verify the new PDFBox fallback assertion fails before implementation.
- [x] **Step 3: Implement the cached PDFBox text/image inspection and source selection**; verify the affected source-policy tests pass.
- [x] **Step 4: Add one API 34 instrumentation flow** with searchable and mixed-content PDF pages; assert searchable text skips OCR and mixed text/image content retains both sources.
- [x] **Step 5: Run the focused API 34 flow and build the Debug APK.** Do not run an API/device matrix for this slice; reserve Xiaomi Pad 5 Pro acceptance for the single representative real-device workflow in task 12.

### Task 4: Reader preview and explicit page-note append

**Files:**
- Create: `android/app/src/main/java/io/pagenote/android/PageTutorDialog.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Test: `android/app/src/androidTest/java/io/pagenote/android/PageTutorDialogTest.kt`

**Interfaces:**
- `PageTutorDialog(page: Int, instruction: String, previewInstruction: String?, isRunning: Boolean, isSaving: Boolean, errorMessage: String?, preview: PageTutorResult?, noteWritable: Boolean, onInstructionChange: (String) -> Unit, onExplain: (String) -> Unit, onAppend: () -> Unit, onDismiss: () -> Unit)` owns only presentation state and delegates all I/O.
- `ReaderHome` builds `PageTutorRequest` from `PageTutorSourceLoader`, current note/revision, and native text from available immediate neighbors.
- Resolve the endpoint before source preparation; missing configuration opens `AgentModelSettingsDialog` and makes no network request.
- Save a dirty page note before source collection. Use `PageTutor(OkHttpChatClient())` to produce a preview, then `PageNoteEditor.appendPage` plus `ProjectNoteStore` only after explicit confirmation.

- [x] **Step 1: Write failing Compose tests** named `showsInstructionAndGenerateAction`, `keepsModelOutputInPreviewUntilAppend`, `disablesAppendForReadOnlyNote`, `disablesAppendWhenInstructionNoLongerMatchesPreview`, `displaysRequestErrorAndKeepsGenerateAvailableForRetry`, `displaysOcrReviewWarningsWithThePreview`, `preventsDismissalWhileRequestIsRunning`, and `preventsDuplicateAppendWhileSaving`.
- [x] **Step 2: Compile instrumentation tests** with `./gradlew :app:compileDebugAndroidTestKotlin --offline` and verify the new dialog contract is missing.
- [x] **Step 3: Implement the localized page-tutor dialog and reader callbacks** with explicit loading, OCR warning, model error, preview, read-only, append and save states.
- [x] **Step 4: Run `./gradlew testDebugUnitTest :app:compileDebugAndroidTestKotlin --offline`** and verify app unit tests and instrumentation-test compilation pass.
- [x] **Step 5: Build debug APKs and run 16 KB checks** with `./gradlew :app:assembleDebug --offline` and `python3 android/scripts/check_16kb_apks.py <arm64-apk> <x86_64-apk>`.
### Task 5: Recoverable SAF note writes

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/ProjectNoteStore.kt`
- Create: `android/app/src/main/java/io/pagenote/android/RecoverableDocumentWrite.kt`
- Test: `android/app/src/test/java/io/pagenote/android/RecoverableDocumentWriteTest.kt`

- [x] Add tests first and confirm the missing recovery and snapshot-check behavior fails before implementation.
- [x] Snapshot-check the current note before and after recovery-copy preparation; abort if it changed.
- [x] Create and clean up a same-folder recovery copy when the SAF provider permits it.
- [x] Abort before modifying the target if recovery-copy capability discovery fails or a provider advertises recovery-copy creation but cannot create/write the copy; retain best-effort rollback only for confirmed unsupported providers, and report copies left behind by cleanup failure.
- [x] Restore the original note after a partial write; retain and report the backup if rollback fails.
- [x] Test rollback, backup retention, successful cleanup, and conflict behavior with deterministic write failures.

- [x] **Step 6: Review the diff, run `git diff --check`, update `android/docs/spec/tasks.md` and `CHANGELOG.md`, then commit and push this feature to `fork/codex/android-port`.**
