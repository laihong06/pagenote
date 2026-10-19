# Android Office Save Policy

**Status:** Implemented as a pure project-core policy; SAF and editor integration remain open under task 36.

## Scope

This contract protects the original Office source during Save & Return. It does not enable Office file import, preview, or editing by itself. The LibreOffice engine and SAF transaction must be available before the UI can consume this policy.

## Requirements

- The app shall compute a source revision by streaming the complete source bytes through SHA-256 and recording the byte count; provider timestamps are not authoritative.
- When the editor has no unsaved changes, the save decision shall be `NO_CHANGES` and shall not require a writable destination or recovery copy.
- When a dirty editor's current source revision differs from the opening revision, the decision shall be `SOURCE_CONFLICT` and the app shall not overwrite the source.
- When the source is unchanged but the destination is not writable, the decision shall be `DESTINATION_UNAVAILABLE`.
- When the source is unchanged and writable but a recovery copy is not ready, the decision shall be `RECOVERY_REQUIRED`.
- The decision shall be `READY` only for a dirty document with an unchanged source, a writable destination, and a completed recovery copy.

## Verification

`OfficeSavePolicyTest` covers binary content hashing, changed-source conflict, read-only destination, missing recovery copy, clean editor, and the ready-to-write case. SAF writes, provider conflicts, and engine lifecycle are not claimed as verified by these unit tests.
