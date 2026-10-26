# Rain Classroom Canonical Slide Order

Approved on 2026-10-24: group PPTs by first classroom appearance; switching back
to A after B must insert into A, not create another A section.

## Contract

- Android first, API 34 tablet only. Keep the current Kotlin/PDFBox/SAF stack.
- Reorder the actual PDF, not just a view. Within a classroom/PPT group use the
  original zero-based sourceIndex; missing/unpublished pages get no placeholders.
- Insert late pages into their group. Existing non-Rain pages retain their order
  ahead of the captured sections. Previously captured out-of-order Rain pages are
  normalized on the next successful write, using their original projectPage.
- Preserve same-page notes, document/note annotations and ink, review records and
  handoffs through the old-to-new page mapping. Move local note history and
  annotation undo history as well. Keep unknown metadata fields.
- Identical identity and content is a no-op, but still focuses its existing page.
  Changed slide content remains a distinct captured version beside that index.
- Add PDF outline sections and portable source sections for each classroom/PPT.
  Without a trustworthy PPT name use PPT 1, PPT 2, etc.; do not invent names.
- Save current draft and pending ink before insertion. Refresh the reader from
  the committed project and jump to the received slide with a reset viewport.
  Busy AI/annotation writes defer the slide to the existing retry queue.
- Snapshot affected files before writing; reject malformed/out-of-range page
  mappings before modifying files. Roll back on write failure. SAF is not a
  multi-file atomic filesystem; process interruption remains a recorded limit.
- Test only the named test course with the existing account. Never auto-select
  another real class. Preserve login data and existing user projects.

## Approach

Use a canonical page permutation plus one inserted PDF page. This preserves the
original PDF pages/resources and works for portable export. UI-only sorting does
not satisfy export or sidecar alignment; one file per PPT breaks the single
notebook workflow. Reuse the existing append API as a compatibility wrapper.

## Focused Checks

One SAF regression exercises A[5], B[3], A[1], B[0], A[3], duplicate A[1], with
literal expected groups/indexes, PDF page pixels, notes, ink, reviews, handoffs,
section outlines, and rollback. Keep the earlier first-page/replay/source tests.
Use the API 34 AVD and, when available, the named test course; no desktop matrix.

## Evidence (2026-10-24)

- RED: the old writer placed late A[1] at project page 3, expected page 1.
- GREEN: canonical order A[1,3,5], B[0,3], PDF center pixels, notes, both annotation
  sides, review/anchor pages, handoffs, outlines and duplicate focus all match.
- Injecting a failure at source-manifest write restores PDF, notes, sidecars and
  local history; replay of the same slide then succeeds.
- Local note-history mismatch was separately reproduced, then fixed by moving
  its page-suffixed version files with rollback. Annotation undo uses the same map.
- The existing first-page/live coordinator and multi-deck source tests pass.
- Actual signed-in API 34 AVD, exact `test` course: read the most recent ended
  lesson report, import 78 actual pages from two PPTs, feed late/interleaved
  presentation order, and verify canonical PDF/source/sections. No live class
  was joined; the temporary SAF project was removed. No credentials are logged.
- Real-time follow navigation is wired to the committed page checkpoint with
  default viewport, including duplicate revisits. The real lesson had ended;
  a new live flip and concurrent real stylus input were not physically observed.
- Author self-review only, following the user's no-subagent preference. No main
  merge, remote push, public release, or desktop runtime change in this slice.

## Limits

- Existing projects normalize on the next successful new-slide write, not on
  ordinary opening. Unpublished slides remain absent; sections have no blanks.
- Cross-app concurrent SAF edits and process death halfway through a multi-file
  provider write are not atomic. Normal reported failures roll back; suppressed
  rollback failures remain errors rather than being reported as successful capture.
