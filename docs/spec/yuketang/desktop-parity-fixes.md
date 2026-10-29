# Desktop Rain Classroom Follow-Up

The Android real-account checks exposed protocol assumptions still present in
the shared Windows/macOS implementation. This follow-up stays in the existing
`codex/android-rainclassroom-fixes` feature branch and preserves unrelated local
work, including the separately approved Android bookshelf.

- [x] Treat history activity type 3/14 `courseware_id` as a lesson ID and load
  visible student-report slides, retaining each deck's actual presentation ID.
- [x] Normalize and retain rotated bearer authorization inside encrypted local
  sessions; never forward it to image-resource hosts.
- [x] Replay the complete initial timeline, resolve stable `sid` references and
  retain both index/page cursors for reconnect and retry.
- [x] Run focused protocol/service/import-route regressions, then rebuild and
  reopen the exact arm64 Mac app. Record Windows/runtime and real-login limits.
- [ ] Commit the approved local work and update upstream PR #11 without
  rewriting shared history or publishing a binary tag. The latest handoff
  authorizes a feature-branch push only, not promotion to the fork's main branch.

Scope update: the user subsequently approved synchronizing the Android bookshelf
and its UI to Windows/macOS. That separate approved scope is implemented and
recorded in `../bookshelf-desktop/`; it is included in this PR handoff together
with the Rain Classroom follow-up on which its creation flows depend.

Ruling: match a current lesson by its unique classroom ID first, falling back to
the unique course ID only when no classroom ID is present. This matches the
Android behavior already used for the real classroom check; endpoint-specific
course aliases must not reject a matching classroom or select a different one.
An unknown/ambiguous classroom is still rejected, never rescued by another
course's ID.
