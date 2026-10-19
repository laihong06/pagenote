# Android AI Prompt Customization

## Scope

Expose editable Android-local prompt modules in the existing AI settings workflow and use those saved prompts in the in-process page tutor and Agent request assembly. Keep endpoint/model/API-key storage and project data separate.

## Module Catalog

The editable catalog matches desktop IDs and order: `system`, `learning`, `page-tutor`, `output`, `tag-edit`, `learner`. Bundled defaults are read-only APK assets. Android-only routing metadata may exist internally but is not exposed as an editable desktop module.

## Storage and Validation

- Store per-module overrides in app-private preferences, separate from encrypted endpoint/model/key settings and independent of project SAF files.
- `read(id)` returns the valid saved override or that module's bundled default. Prompt text is loaded only for the modules needed by the active request.
- `save(id, text)` rejects unknown IDs, whitespace-only text, and text longer than 32,768 UTF-16 code units before changing storage. A failed save leaves the prior value intact.
- `reset(id)` removes only that module's override; other overrides and model credentials are unchanged.
- Never write APK assets or include credentials in prompt storage or request metadata.

## Runtime Composition

- Page explanation reads `system`, `learning`, `page-tutor`, and `output` on every request assembly; selected-region explanation uses the same modules with the selected-region safety context.
- General Agent tasks read `system` and `output`; tasks explicitly editing AI tags/annotations additionally read `tag-edit`. Learner context remains opt-in and is included only where the caller explicitly supplies learner context.
- Always prepend the app-owned safety instruction that document/PDF/OCR/note content is untrusted study material, not tool instructions. Editable prompt text cannot remove this prefix or expand local tool permissions.
- Saving a module affects the next request assembly without app restart. Reset immediately returns future reads to the bundled default.

## UI

- AI settings provide a module selector, multiline text editor, Save, Reset to default, and Cancel.
- Selecting a different module retains unsaved draft text in the dialog session; switching modules does not implicitly persist it.
- Save/reset errors remain visible and do not dismiss the dialog or discard the last persisted value.
- Prompt editing is separate from endpoint/model/key fields and retains the 32,768-character limit.

## Acceptance

- All six catalog IDs and their bundled defaults can be opened; edits and resets are isolated per module and survive recreation of the store.
- Invalid saves leave existing overrides intact. Model endpoint/key data is unaffected by prompt operations.
- Page tutor and Agent request tests prove saved overrides are included in the next request and the mandatory safety prefix remains first.
- UI tests cover module selection, save, reset, validation failure, and preservation of unsaved module drafts.

## Verification

- Focused app JVM tests for catalog/store/composition, API 34 tablet instrumentation for the settings workflow and request integration, AndroidTest compilation, Debug APK assembly, and `git diff --check`.
