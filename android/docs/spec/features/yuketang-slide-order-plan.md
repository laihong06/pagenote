# Rain Slide Order Implementation Plan

> Execute inline in the current feature workspace, without subagents.

**Goal:** Keep captured PPTs together in original page order and focus each received page.
**Architecture:** A validated page permutation drives PDF, notes and sidecars in one
rollback-protected write; the live coordinator returns both added-page and focus-page state.
**Tech Stack:** Kotlin, existing Android PDFBox, org.json, SAF, API 34 AVD.
**Spec:** [yuketang-slide-order.md](yuketang-slide-order.md).

## Constraints And Review Focus

- Android only; no desktop changes or public release. Keep unrelated files untouched.
- Revisit/duplicate: no extra page, navigate to the canonical page (task 2).
- Late A pages after B: preserve B notes/ink and all stored page references (task 1).
- Existing arbitrary PDF pages: retain them and reject invalid source mappings (task 1).
- Write failure: restore the full previous file set, not only the PDF (task 1).
- Pending pen/AI writes: do not commit stale page-keyed data after permutation (task 2).

## Task 1: Canonical Portable Writer

- [x] Add RainSlideOrderTest using real SAF/PDFBox; run it against append-only writer (RED).
- [x] Add RainPageOrder.kt: validated old-page permutation, group/index ordering and JSON remapping.
- [x] Update RainProjectWriter: insert(...) -> RainPageWrite(pageNumber, added), preserve append(...)
  nullable compatibility, canonical history creation, section outlines, snapshots and rollback.
- [x] Run the ordering and existing source/live SAF regressions (GREEN).

## Task 2: Reader Follow

- [x] Extend RainLiveProjectWrite with focusPage; coordinator resolves duplicates without rewriting.
- [x] Save draft, wait for pen/annotation persistence, reject busy AI writes into retry queue,
  reopen with the received page checkpoint and reset viewport. Preserve stable target identity.
- [x] Verify the coordinator focus result on API 34; real live flipping is not
  claimed because the named lesson ended. Checkpoint follow wiring was self-reviewed.

## Task 3: Handoff

- [x] Build/install using -r, run only focused tests and the explicitly named test course if available.
- [x] Update SPEC evidence, CHANGELOG and any actual limitations; no unperformed test claims.
- [x] Self-review changed Android files. Do not push or merge main or publish artifacts.

## Execution Notes

- Current feature workspace reused; no subagents or worktrees were created.
- Retain original non-Rain PDF outlines; generated PPT outlines are explicitly
  marked and regenerated rather than duplicated on every insertion.
- Include app-private version-history filenames and annotation undo page keys
  in the permutation; otherwise restoring history after insertion can target
  the wrong page. Failure injection at the final source write covers rollback.
- Read live draft state inside the long-lived socket callback, not the dirty
  boolean captured when capture began. Temporarily lock editing during a write.
- Actual ended test course: 78 pages / 2 PPT sections, read-only report probe passed.
- Final app JVM suite and three focused SAF instrumentation tests passed.
