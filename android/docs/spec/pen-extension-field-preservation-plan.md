# Android Pen Extension Field Preservation Plan

> **For agentic workers:** Implement this task with test-first development. Keep the feature SPEC authoritative.

**Goal:** Preserve unknown JSON fields on retained freehand strokes when Android edits and saves a page.

**Architecture:** `PenStroke` carries extension fields parsed from `annotations.json`; its serializer writes them back without allowing them to replace canonical stroke properties. Existing page merging remains responsible for removing fields together with a stroke that the user erases or clears.

**Tech Stack:** Kotlin, existing `PenPageStore`, `AnnotationPageMerger`, JUnit.

**Spec:** `android/docs/spec/features/pen-project-integration.md`.

## Constraints

- Keep the existing pagenote `annotations.json` shape; do not discard unknown project fields.
- Retained stroke extension values may be nested JSON objects, arrays, scalars, or null.
- New strokes have no extension fields by default.
- Canonical fields are `type`, `color`, `width`, `points`, `pressures`, `text`, `html`, and `url`.
- Test only the affected Android JVM test and build the Debug APK; this model change does not need an additional device matrix.

## Review Focus

- Editing a page retains nested extension metadata on strokes that remain.
- Erasing or clearing a stroke removes its extension fields with it.
- Extension data cannot override canonical stroke values.

### Task 1: Preserve freehand item extension fields

**Files:**

- Modify: `android/feature-pen/src/main/java/io/pagenote/feature/pen/PenStroke.kt`
- Modify: `android/feature-pen/src/main/java/io/pagenote/feature/pen/PenPageStore.kt`
- Test: `android/app/src/test/java/io/pagenote/android/AnnotationPageMergerTest.kt`

**Interface:**

- `PenStroke.extraFields: Map<String, Any?> = emptyMap()` stores only unknown item-level JSON fields.
- `PenStroke.toAnnotationsItem()` emits canonical fields and then non-conflicting extension fields.
- `PenPageStore.loadAnnotationsMap()` separates canonical keys from extension keys and carries the latter through returned strokes.

- [x] Write `replacingChangedPagePreservesNestedFieldsOnRetainedFreehandStrokes`; load a raw stroke with nested object/array metadata into `PenPageStore`, add a new stroke, merge the page, and assert the retained item has identical metadata and all canonical fields remain unchanged.
- [x] Extend `emptyReplacementRemovesOnlyPenAndHighlighterItems` with a nested extension field on a freehand item and verify removing the item also removes its fields.
- [x] Run the focused test and confirm it fails because the loaded stroke loses the extension field.
- [x] Add extension-field preservation to `PenStroke` parsing and serialization without changing erase/clear behavior.
- [x] Run the focused `AnnotationPageMergerTest` and `:feature-pen:testDebugUnitTest`.
- [x] Build `:app:assembleDebug` and run `git diff --check`.
- [x] Update `android/docs/spec/tasks.md` and `CHANGELOG.md`, then commit and push the feature to `fork/codex/android-port`.
