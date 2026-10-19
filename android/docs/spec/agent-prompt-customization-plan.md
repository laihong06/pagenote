# AI Prompt Customization Implementation Plan

> Android-only implementation plan for task 47. Implement test-first in small commits; update root `CHANGELOG.md` in every pushed commit.

## Steps

1. Add pure catalog/policy tests for the six desktop IDs, per-module defaults, length/blank/unknown validation, and route-specific composition with an immutable safety prefix.
2. Add app-private prompt override storage with atomic per-module save/reset. Verify reopen, isolation, and rejected writes preserving the previous value.
3. Add a localized Compose prompt editor reachable from AI settings. Cover module switching with unsaved drafts, save, reset, and visible failure state on API 34 tablet instrumentation.
4. Load only relevant prompt IDs at request assembly. Page tutor uses system/learning/page-tutor/output; general Agent uses system/output and adds tag-edit only for an AI-tag edit. Verify with fake chat clients that the next request sees new text and always starts with the safety prefix.
5. Run focused JVM tests, relevant API 34 AVD instrumentation, AndroidTest compilation, and `:app:assembleDebug`; record exact results and mark task 47 complete.

## Constraints

- Keep prompt overrides in app-private storage, separate from `agent-model-settings.enc` and project files.
- Never edit bundled defaults at runtime, never fetch prompt content remotely, and never let a user prompt change tool permissions.
- Do not expand beyond the API 34 tablet verification target.
