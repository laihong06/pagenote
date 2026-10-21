# Android Reader Session Restoration

> Status: implementation slice complete; automated API 34/35 verification and an API 34 process-death/task-restoration smoke are complete. Natural low-memory-killer behavior and physical-device verification remain open.

> Scope confirmation (2026-10-18): the user confirmed that restoring unsaved note drafts after Android reclaims the app process is required, not just Activity recreation. This is included in the acceptance and test scope below.

## Goal

Keep the active reader session intact across Activity configuration recreation, adaptive window changes, and Android system process reclamation. Recovery includes unsaved page-note drafts without changing the project format or silently writing a draft to the project.

## Session Checkpoint

`MainActivity` owns one `ReaderSessionViewModel`. The ViewModel retains the open project, PDF renderer or Markdown source, note state, pen state, viewport bookmark, and session jobs across configuration recreation. A small process-restoration checkpoint is written through `SavedStateHandle`: SAF access URI and root document ID, project ID, page index, normalized PDF viewport bookmark, selected pen tool, and the identity of the active draft. Large note text and stroke payloads do not go in the saved-state Bundle.

The viewport bookmark stores scale and the normalized page point centered in the viewport, not pixel translations. After a new composition measures its viewport, it reconstructs and clamps a transform for the new bounds. Measured bounds, rendered page bitmaps, and in-progress gestures remain composition-owned and are recalculated after recreation.

## Durable Draft Recovery

- Store unsaved page-note drafts in an app-private, `AtomicFile`-backed recovery journal, separate from the project. Key each record by the SAF access URI, project ID, and page so opening another project cannot overwrite a previous recoverable draft.
- Each journal record contains the page draft and its base page revision. Write edits off the UI thread with a short debounce; synchronously flush the newest value when the Activity stops so Android can reclaim the process without losing the last editor state.
- On a restored launch, verify the persisted SAF read grant before reopening the project. Re-read the project and page note before applying the draft. If the disk revision still matches, restore the dirty draft. If it changed, restore the draft with the original base revision and show a conflict status; the existing revision guard must refuse an overwrite.
- If the user switches projects while a draft is dirty, flush it to its project/page-keyed journal before replacing the reader. Explicitly reopening that project can recover its newest local draft even when it is no longer the current saved checkpoint.
- Clear only the matching journal record after a successful save, a confirmed discard/reload, or when the draft equals the saved page. Keep the record when SAF permission is missing, project opening fails, or a revision conflict occurs.
- Pen strokes already committed to `annotations.json` are reloaded from the project. A not-yet-committed pen write is not part of this note-draft journal; the UI must continue to report pending/failed pen writes during a live session.

## Ownership and Lifecycle

- The Activity-scoped ViewModel owns `PdfDocumentSession`. Replacing a project closes the prior renderer once; clearing the ViewModel closes the active renderer. Compose disposes only rendered bitmaps and transient UI resources.
- Project open, note save, and pen writes use a session coroutine scope that survives Activity recreation. Cancellation is propagated when the ViewModel is actually cleared.
- Page changes and explicit reload/history/AI replacement update the note draft through explicit transitions. Composition startup must not copy disk notes over a retained dirty draft.
- Dialogs, active OCR selection/jobs, AI task progress, and gesture-in-progress state are transient and are not restored.

## Boundaries

- Supported: Activity configuration recreation, same-Activity window-size changes, and Android system process reclamation while the task and app data remain available.
- Not promised: force-stop, app-data clearing/uninstall, device reboot restoration, recovery after the user explicitly discards a draft, or recovery of a pen write that had not reached the project file.
- A revoked SAF grant must not delete the local draft journal. The app reports that the project must be reopened or permission restored before the draft can be applied.
- No file format or project schema changes.

## Acceptance

- After `ActivityScenario.recreate()`, the same project and renderer remain active, the selected page is unchanged, and the renderer remains usable.
- A dirty note draft and its base revision survive configuration recreation and a fresh ViewModel created from saved checkpoint state plus the app-private journal.
- On process restoration, the project target, page, normalized viewport, and pen tool are restored; committed pen strokes are loaded from the project.
- A matching disk revision permits the recovered draft to remain editable. A changed revision preserves the draft and prevents save from overwriting newer project content.
- Missing SAF permission or a failed project reopen leaves the recovery journal intact and produces a visible recovery error.
- Successful save, explicit reload/discard, or draft equality removes only the matching recovery record.
- The PDF zoom scale and normalized center point remain stable after viewport dimensions change; translations are recalculated and clamped to new bounds.
- Replacing a project and clearing the Activity ViewModel close renderer resources without Compose recreation closing the retained renderer.
- Existing phone drawer/tablet sidebar behavior and Android 14 minimum remain unchanged.

## Tests

- JVM tests cover bookmark capture/restore across portrait/tablet bounds, scale preservation, malformed values, and translation clamping; recovery-record identity and revision decisions are tested as pure behavior.
- Android instrumentation tests cover `AtomicFile` journal write/read/clear, a real two-page PDF renderer across `ActivityScenario.recreate()`, checkpoint restoration into a fresh ViewModel, and revision-conflict retention.
- Android 14 / API 34 AVD process-death smoke: grant the fixture folder through DocumentsUI, edit a note without saving, background the app, SIGKILL its process through the app UID while retaining the task, then bring the existing task forward. The project reopened through the persisted SAF grant, the recovery status appeared, and the exact unsaved draft remained in the editor. This is distinct from `ActivityScenario.recreate()` and does not simulate natural low-memory-killer pressure or physical-device behavior.
- Run the Android JVM suite, `assembleDebug`, instrumentation on API 34 and API 35 AVDs, and the four-ABI 16 KB APK checker. Emulator results are not represented as physical-device verification.

## Related

- Integrated acceptance ledger: `../tasks.md`, task 15; broader device acceptance remains tracked in task 12.
