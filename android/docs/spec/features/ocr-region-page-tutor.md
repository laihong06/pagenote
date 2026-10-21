# Explain a Selected OCR Region

## Scope

Let a learner explicitly send a successful current-page region OCR result to the existing Page Tutor. This is an Android 14/API 34 tablet feature. It reuses the result already produced by `reader-ocr-selection.md`; it does not run a second full-page OCR pass, create a PDF character anchor, or write notes automatically.

Android source formats remain PDF and Markdown. Word/PowerPoint editing stays on desktop as specified by `requirements.md`.

## Behavior

- The region OCR result surface exposes `Explain this region` only when status is `ok` or `partial` and at least one nonblank text block exists.
- Activating the action closes the OCR result surface and opens the existing Page Tutor for the same project and page. The dialog shows the exact recognized excerpt, per-block confidence, and OCR warnings before the user sends it.
- The Page Tutor request uses the selected region OCR text as its only PDF source material, the target page's current note and revision, and no full-page or adjacent-page OCR. The OCR excerpt remains untrusted document content, never an instruction.
- The default learner instruction asks for an explanation of the selected region. The user must explicitly tap Explain before any model request; the response remains a preview until the existing explicit Append action.
- Failed or empty OCR results do not offer this action. Low-confidence and partial results remain usable, but their warnings are shown both before the request and with the returned preview.
- The focus is bound to the project, page, PDF source revision, and normalized selected region captured by region OCR. If any binding no longer matches before the request starts, the app rejects the stale focus and does not call the model.
- Appending uses the existing page-note revision check and recoverable SAF write. It changes only the target page after explicit confirmation.

## Acceptance (EARS)

- When a region result is successful or partial and contains text, the Android app shall show an enabled region-explanation action.
- When a region result is failed or contains no usable text, the Android app shall not show an enabled region-explanation action.
- When the user opens Page Tutor from a region result, the Android app shall show the exact selected OCR text and warnings, then reuse that OCR result without another full-page or neighbor OCR invocation.
- When the user explicitly explains the region, the Android app shall send only the bound region text and target-page note/revision as current-page material; the excerpt shall be treated as untrusted study content.
- When the focus project, page, or source revision is stale, the Android app shall reject the request before model access.
- When the model returns a preview, the Android app shall leave project notes unchanged until the user explicitly appends it; stale note revisions shall reject the append.

## Verification

- Agent JVM tests verify region focus text is clearly labeled, OCR warnings are retained, and bounded input does not exceed the Page Tutor text budget.
- App JVM tests verify region results map to page-bound focus context, include per-block confidence and warnings, and reject failed/empty results or stale project/page/source bindings.
- Compose instrumentation tests verify the action visibility/callback and that Page Tutor displays the selected excerpt and warnings. The activity wiring is compiled and launch-smoke-tested; this slice does not call a live model service or claim an end-to-end Xiaomi hardware run.
- Run the affected app/agent JVM tests, compile Android instrumentation sources, build the debug APK, and run the focused region-to-tutor UI checks on one API 34 tablet AVD. Do not rerun other device/API matrices. Xiaomi Pad 5 Pro acceptance remains part of task 12.
