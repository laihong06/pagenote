# Android Page Tutor Preview and Append

## Scope

Add the first user-facing Android Agent workflow: explain one open PDF page with the configured OpenAI-compatible model, preview the Markdown response, and append it to that page's note only after explicit user confirmation. Keep the feature Android-only and keep all Android SPEC artifacts under `android/docs/spec/`.

## User Flow

1. With a PDF project open, the user opens the page tutor and may edit a default explanation instruction.
2. Before sending a request, the app saves a dirty current-page note. A failed save aborts the request.
3. For a normal page request, the app reads PDF text for the current page and its immediate neighbors. It prefers platform text extraction when available, then uses the existing PDFBox text layer on Android 14 before considering OCR. It runs bundled offline OCR when neither text layer provides usable current-page text, or when sparse selectable text occurs on a page with raster images. Mixed-page requests retain both sources. When the request is opened from a successful region OCR result, the app reuses that region's text, confidence, and warnings without running full-page or neighbor OCR again. It never sends an empty source page.
4. The app sends one page-scoped request through the configured OpenAI-compatible chat client. The current page is the primary material; available previous/next pages are context only. Each page is capped as specified below. Project material is untrusted study content, not tool instructions. No local Agent tools are exposed to this request.
5. The model response is shown as a preview and does not modify project files automatically.
6. On explicit confirmation, the app appends the explanation to the current page using the page revision captured before the request. Other pages are preserved. A stale revision rejects the append and leaves the latest note untouched.

## Contracts

- The page tutor uses `ChatClient.complete` directly; it must not parse ordinary Markdown as `DONE` or as an Agent tool call.
- Requests carry document ID, one-based target page, total page count, user instruction, current source text, current note, note revision, and at most the immediately adjacent previous and next source pages. Region-focused requests use only the selected OCR region as source text and carry no neighbor material.
- Each PDF page's text sent to the model is capped at 20,000 Kotlin string characters, matching desktop `prepare_task`'s default `maxChars`. Truncation is explicit in the preview warnings; it is never silent.
- A missing API key opens the existing model settings flow without sending a request. Model, PDF, OCR, timeout, HTTP, and empty-response failures are surfaced without changing notes.
- OCR-derived input retains its warnings. The preview tells the user to verify OCR-based explanations against the original PDF when the OCR result is incomplete, warned, or low-confidence.
- Android 14 PDFBox text is searchable-PDF text, not OCR; its successful use must not initialize or invoke the OCR engine. Neighbor context uses the same platform-then-PDFBox text fallback but does not trigger OCR in the non-agent page-tutor path.
- When a PDFBox page has both a sparse text layer and raster image resources, Page Tutor runs OCR and includes both text sources with a verification warning; image-free searchable pages do not run OCR merely because platform extraction is unavailable.
- If the user edits the explanation instruction after a response arrives, the old preview remains readable but cannot be appended until a new response is generated.
- Apply is disabled for read-only notes. The append operation checks `expectedRevision` against the target page immediately before writing.
- Before overwriting the project note, the Android app rechecks the full note snapshot used to prepare the edit. It creates a same-folder recovery copy when the SAF provider supports creating and deleting documents, restores the original content after a failed write when possible, and retains/reports the recovery copy if rollback also fails.
- If the SAF provider advertises document creation but creating or writing the recovery copy fails, the app shall abort before modifying the target note. Providers that do not support recovery-copy creation may use best-effort target rollback.
- If the app cannot resolve the note's parent or query SAF capability flags, it shall abort before modifying the target note rather than treating the unknown capability as unsupported.
- If deleting a recovery copy fails after a successful update, conflict, or successful rollback, the app shall surface the retained filename so the user can find it.
- Repeated explanations append as separate localized `AI explanation` sections; they never replace existing page content.

## Acceptance (EARS)

- When the user requests an explanation without a valid model configuration, the Android app shall open model settings and shall not call the model.
- When platform text extraction is available, the Android app shall use it without invoking OCR.
- When platform text extraction is unavailable and the Android 14 PDFBox text layer contains usable text that does not meet the sparse-image OCR trigger, the Android app shall use that text without invoking OCR.
- When a PDFBox text layer is sparse and the same page contains raster image resources, the Android app shall run offline OCR, retain both text sources, and warn that the combined source should be checked against the page.
- When neither platform nor PDFBox text is usable, the Android app shall use bundled offline OCR before calling the model and shall retain any OCR warnings in the preview.
- When Page Tutor is opened from a successful region OCR result, the Android app shall reuse that result and shall not invoke full-page OCR or neighbor OCR for the same request.
- When a region-focused request uses low-confidence or partial OCR, the Android app shall retain the confidence warning in the preview and expose the selected excerpt in the dialog before model execution.
- When the user requests an explanation, the Android app shall send only the target page and available immediate neighbors, with the current page's note and revision, and shall not use the Agent tool loop.
- When the model returns Markdown, the Android app shall show it in a preview while leaving project notes unchanged.
- When the user confirms an append and the page revision is unchanged, the Android app shall append only to that page and preserve every other page.
- When the page revision changed after the request began, the Android app shall reject the append and preserve the latest note contents.
- When the instruction changes after a preview was generated, the Android app shall disable append until the user generates a matching preview.
- When the project note changes while a recovery copy is prepared, the Android app shall abort the write without replacing the newer contents.
- When the SAF provider advertises recovery-copy creation but cannot create or write the copy, the Android app shall abort without modifying the target note.
- When SAF parent or capability discovery fails, the Android app shall abort without modifying the target note; only a confirmed unsupported capability may use best-effort target rollback.
- When a note write fails after truncating the target, the Android app shall restore the original note or retain a recovery copy and report its filename if restoration fails.
- When cleanup of a recovery copy fails, the Android app shall report its filename and whether the note update or rollback succeeded.
- When input text exceeds 20,000 characters for a page, the Android app shall mark that page material as truncated and display the warning before apply.
- When whole-document protection removes a neighbor page from the request, the Android app shall not report truncation warnings for that excluded page.
- When the model call, OCR, source extraction, or write fails, the Android app shall show a clear error and shall not claim that the note was updated.

## Non-Goals

- Editing AI review records or marking reviews applied; that is a separate revision workflow.
- Automatic background generation across pages.
- Modifying PDF source content, annotations, or pen strokes.
- Replacing the existing AgentRunner protocol or adding model tools to the page tutor.

## Verification

- Agent unit tests prove request scope, page/revision material, raw Markdown response handling, explicit text truncation, and failure behavior with a fake `ChatClient`.
- Android JVM tests prove platform-text preference, Android 14 PDFBox-text fallback before OCR, OCR fallback when both text sources are unavailable, OCR failure stopping before the model call, and low-confidence warning retention.
- Note editor unit tests prove append semantics, preservation of other pages, empty-page behavior, and stale-revision rejection.
- Recoverable-write unit tests prove rollback after partial writes, recovery-copy retention if rollback fails, cleanup after success, fail-closed behavior when parent/capability discovery or an advertised recovery-copy create operation fails, best-effort writes when recovery copies are explicitly unsupported, residual filename reporting when cleanup fails, and conflict rejection if the source changes during backup preparation.
- One focused API 34 instrumentation flow uses a searchable/mixed PDF fixture through `PdfDocumentSession` and `PageTutorSourceLoader`, proving searchable text avoids OCR and sparse mixed content retains both sources after OCR. This is Android 14 platform evidence, not Xiaomi hardware acceptance.
- For this feature slice, run the affected JVM tests, compile the focused instrumentation test, execute that single API 34 flow when an API 34 tablet AVD is available, and build the Debug APK. Do not run other API/device or page-position matrices. Xiaomi Pad 5 Pro acceptance remains one representative real-device workflow under `tasks.md` task 12.
