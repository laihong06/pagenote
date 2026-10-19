# Android SAF Image Resolution Integration Plan

## Goal

Close task 5.2's integration-evidence gap by verifying that Markdown image loading reaches a SAF-shaped document tree through `ContentResolver`, not only the bitmap decoder.

## Scope

- Exercise attachment lookup through `NoteMarkdownRenderer` using an instrumentation-only exported `ContentProvider` fixture that emulates `DocumentsContract` tree/document URI behavior; use the target app's real `ContentResolver`.
- Verify successful root/assets child queries and stream opening render a decoded local image.
- Verify provider query/authorization failures and `openDocument` denial become the existing visible, localized placeholder without exposing provider details, document URIs, query values, or local paths.
- Preserve the offline-only image policy and existing production API unless a failing integration test demonstrates a product defect.
- Keep read-time `InputStream` decode failures covered by `image-stream-failure-plan.md`.

## Acceptance (EARS)

- When the test document-tree provider exposes the referenced `assets/<name>` image, Markdown preview shall discover and decode that image through the target app's `ContentResolver`.
- When child lookup or opening the referenced document fails, Markdown preview shall display a visible missing-image warning and shall not propagate provider exceptions or reveal private provider details.

## Test Design

- Register a test-only provider in the Android instrumentation manifest; it returns realistic document and child-document cursors and a small valid PNG for the success case.
- Build a tree URI and the `src` document URI (the same directory boundary used by opened projects), then render through the target application's real `ContentResolver` and normal renderer context.
- Give the fixture explicit modes for child-query denial and `openFile` denial. Assert rendered drawable outcome and exact safe warning text, not only fixture call counts.
- Do not request storage permissions, use device files, add network access, or alter the user's SAF grants.
- This verifies resolver dispatch and the app's document-tree query/open behavior; it does not claim to test the system picker, `DocumentsProvider` implementation callbacks, or actual persisted-grant enforcement.

## Tasks

- [x] Add the test-only `ContentProvider` fixture serving `DocumentsContract`-shaped URIs and manifest registration.
- [x] Add renderer integration tests for successful SAF image rendering, child-query denial, and document-open denial.
- [x] Confirm no production change is needed; existing resolver handling satisfies the tested contract.
- [x] Run focused API 35 instrumentation and full API 34/API 35 instrumentation, forced full JVM tests, `assembleDebug`, and four-ABI 16 KB checks.
- [x] Update `tasks.md` and `CHANGELOG.md`, then commit and push this slice.
