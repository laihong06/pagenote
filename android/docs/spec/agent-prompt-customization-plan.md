# AI Prompt Customization Implementation Plan

> Android-only implementation plan for task 47. Implement test-first in small commits; update root `CHANGELOG.md` in every pushed commit.

## Steps

1. [x] Add pure catalog/policy tests for the six desktop IDs, per-module defaults, length/blank/unknown validation, and route-specific composition with an immutable safety prefix.
2. [x] Add app-private prompt override storage with atomic per-module save/reset. Verify reopen, isolation, and rejected writes preserving the previous value.
3. [x] Add a localized Compose prompt editor reachable from AI settings. Cover module switching with unsaved drafts, save, reset, blank validation, and all packaged defaults on API 34 tablet instrumentation.
4. [x] Load only relevant prompt IDs at request assembly. Page tutor uses system/learning/page-tutor/output; general Agent uses system/output and adds tag-edit only for an AI-tag edit. Verify new text composition and immutable safety prefix.
5. [x] Run focused JVM tests, API 34 AVD instrumentation, AndroidTest compilation, Debug APK assembly, and 16 KB alignment check.

## Verification Record

- `AgentPromptCustomizationTest`: 6/6; `PageTutorTest`: 13/13.
- `AgentPromptSettingsDialogTest`: 3/3 on `Pixel_Tablet_API34(AVD) - 14`; covers module draft retention, save/reset isolation, blank rejection, and all six APK prompt assets.
- Final packaging and 16 KB alignment results are recorded in task 47 and the matching changelog entry.

## Constraints

- Keep prompt overrides in app-private storage, separate from `agent-model-settings.enc` and project files.
- Never edit bundled defaults at runtime, never fetch prompt content remotely, and never let a user prompt change tool permissions.
- Do not expand beyond the API 34 tablet verification target.
