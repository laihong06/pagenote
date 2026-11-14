# Android Reference Action Stack Design

## Intent

Replace the Android reader's layer-label chronology with a page-scoped list of edit action objects, following the recovered reference `ActionStack` structure. This should keep mixed pen and native annotation edits in one chronological undo/redo timeline while preserving PageNote's current file format and async persistence behavior.

## Evidence and current state

- The supplied recovered DEX (`business-recovered.dex`, SHA-256 `846b574215037f6fe747779677fb848121ddf37aad465fd48f97e77fbc4d6018`) was wrapped as a temporary APK for focused JADX inspection.
- Reference `ActionStack.add(Action)` inserts at `current`, advances the cursor, and truncates the redo suffix. `undo()` and `redo()` move that cursor; `PanelManagerExtend` then invokes the action object's corresponding method.
- Android currently records only `PEN`, `ANNOTATION`, or a grouped combination in `ReaderEditHistoryOrder`, then routes to independent `PenPageStore` and `PdfAnnotationEditHistory` snapshots. Existing `PenActionStack` is not connected to the reader.

## Goals and limits

- Store typed edit action objects in one chronological per-project, per-page history.
- Keep the current pen and annotation snapshot stores as the mutation adapters during this migration milestone.
- Reserve async gesture transactions before their saves finish; a later edit must remain newer in history.
- Keep failures retryable by moving an action's cursor only after its undo/redo operation is accepted, and retain explicit rollback for delayed persistence failures.
- Preserve page remapping, branch truncation, maximum history depth, file format, and platform boundaries.
- Do not claim the full reference handwriting kernel or direct reference material model has been ported. Process-restart history persistence and cross-platform round-trip are out of scope.

## Approaches

1. Keep layer queues and improve their special cases. This is the current design and does not match the reference action list.
2. Store typed action objects in a cursor-based reader stack and let each action dispatch to the existing snapshot adapters. This matches the reference history structure while containing the first migration to Android reader behavior. **Chosen** because persistence remains asynchronous and platform-specific.
3. Replace both snapshot stores with direct reference-style material actions in one pass. This would couple the Android page-file writer to every edit type and risks overwriting newer ink during async saves, a failure already found in the prior integration experiment.

## Acceptance

- `REF-ACTION-01`: interleaved pen/annotation actions undo and redo in exact chronological order; a new edit after undo discards the redo branch.
- `REF-ACTION-02`: a pending grouped gesture keeps its reserved position ahead of later edits and an empty group leaves no history item.
- `REF-ACTION-03`: failed or rejected undo/redo remains retryable; delayed save rollback restores the same action position.
- Existing `REF-HISTORY-01/02` and cross-layer erase cases continue to pass on `Pixel_Tablet_API34_Acceptance` only.

This milestone has no layout or copy change, so PR screenshots are exempt under `docs/PR-STANDARD.zh-CN.md`; the existing tablet runtime evidence remains linked for the preserved user-visible history behavior.
