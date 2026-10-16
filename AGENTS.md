# Project workflow

## Branch policy

`main` is the stable branch; `beta` is the integration and testing branch. Start all new features and routine fixes from `beta`; feature PRs must target `beta`. Do not push development changes directly to `main`. Promote tested changes through a PR to `main` only after real-use testing and explicit founder approval for that promotion. Passing CI alone does not authorize a stable release. Keep stable download links pinned to the public release during beta development.

Mac application packages target Apple Silicon (`arm64`) only. Do not build or test Intel (`x64`) Mac application packages unless the user explicitly requests them.

The user tests the local app at `release/mac-arm64/pagenote.app`. Refresh and verify that app before handing off runtime changes. A running instance can retain the previous code after its bundle is replaced; use normal save-and-quit before reopening this exact path when validating the user's active app.

Runtime changes are not handed off from source or `dist/` alone. Before reporting local verification, rebuild and repackage `release/mac-arm64/pagenote.app`, confirm its bundle version/timestamp is newer than all runtime inputs, then save-and-quit/reopen that exact double-click entry point and test it. A matching version number is not enough: an older bundle can share the current version while missing newer source changes.

Every push must include an accompanying `CHANGELOG.md` update describing the user-visible changes or fixes in that push. Keep unreleased work under the current Unreleased section and distinguish local builds from public releases. Do not push code or documentation changes without a matching log entry.

A source push is not a binary release. Before publishing a version, pass `npm run check:release`, build and test the matching artifacts, and publish installer/ZIP/source/checksums under the same tag. Update public README download links only with that release. Never reuse a published version for different binary contents.

After a release, advance package.json and package-lock.json to the next development version when changing runtime behavior. Keep README downloads pinned to the last published release until the next complete release is available.

Release decisions follow docs/RELEASE-POLICY.md. The founder-approved early-stage gates supersede historical exhaustive acceptance checklists. Windows v1.0 is approved based on LTH0001's Windows tests plus the founder's supplementary local tests; macOS stays experimental. Record non-blocking defects/unverified environments in KNOWN_ISSUES.md; do not add indefinite all-environment gates or claim unperformed tests.
