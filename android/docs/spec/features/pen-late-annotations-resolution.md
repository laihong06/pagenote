# Pen Store Late Annotations Resolution

## Scope

Keep the native pen store attached to the project's canonical `src/annotations.json` when that file is created after the project is opened. This covers projects where a PDF text or shape annotation creates the file during the current reader session. It does not change the pagenote JSON contract, annotation merge behavior, or SAF write-recovery policy.

## Contract

- A `ProjectPenStore` constructed with `annotationsUri == null` shall look for the canonical `annotations.json` in the source directory before deciding that the file is absent.
- Pen reads, writes, and write-capability checks shall resolve the canonical file at the point of use and cache a discovered URI for subsequent operations.
- When the canonical file was created after the project was opened, pen writes shall update that file and preserve existing non-pen annotations, other pages, and unrelated JSON fields.
- A pen write shall create `annotations.json` only when no canonical file exists and the page has strokes to persist. It shall never create an auto-renamed duplicate such as `annotations (1).json`.
- If SAF cannot list the source directory, the operation shall fail visibly rather than assume the file is absent and create another one.
- Existing recovery-copy behavior for writes to an existing annotations file remains unchanged.

## Acceptance

- When an opened project has no `annotations.json`, and a `ProjectAnnotationStore` creates it, a previously constructed `ProjectPenStore` shall read and append a pen stroke in the same file.
- When a PDF annotation and a pen stroke are written in that order, `src/` shall contain exactly one `annotations.json`, and both records shall survive a fresh read.
- When the file remains absent and an empty pen page is written, no file shall be created.
- When the child-document query fails, the pen store shall not invoke `createDocument`.
- Existing annotation merge and write-recovery tests shall continue to pass.

## Verification

- Add a focused API 34 tablet AVD SAF integration test using the existing test `DocumentsProvider`, initialized without `annotations.json`.
- Run that test before implementation and confirm it fails because the old pen store attempts a second document creation.
- Run the focused test, app JVM tests, Android app compilation, and Debug APK build after implementation.
- This is not physical stylus validation; Xiaomi Pad 5 Pro input behavior remains under the single-device acceptance task.
