# Project workflow

## Branch policy

`main` is the stable branch; `beta` is the integration and testing branch. Start all new features and routine fixes from `beta`; feature PRs must target `beta`. Do not push development changes directly to `main`. Promote tested changes through a PR to `main` only after real-use testing and explicit founder approval for that promotion. Passing CI alone does not authorize a stable release. Each branch has its own README and matching downloads: main links stable packages, beta links beta prereleases. Never use stable downloads as the beta download entry. Unavailable platform builds must be marked unavailable, not substituted with older builds. Beta packages use a distinct installation identity and user data directory and do not claim stable file associations. Preserve the appropriate branch README when promoting code.

Every push must include an accompanying `CHANGELOG.md` update describing the user-visible changes or fixes in that push. Keep unreleased work under the current Unreleased section and distinguish local builds from public releases. Do not push code or documentation changes without a matching log entry.

A source push is not a binary release. Before publishing a version, pass `npm run check:release`, build and test the matching artifacts, and publish installer/ZIP/source/checksums under the same tag. Update public README download links only with that release. Never reuse a published version for different binary contents.

After a release, advance package.json and package-lock.json to the next development version when changing runtime behavior. Keep README downloads pinned to the last published release until the next complete release is available.

Release decisions follow docs/RELEASE-POLICY.md. The founder-approved early-stage gates supersede historical exhaustive acceptance checklists. Windows v1.0 is approved based on LTH0001's Windows tests plus the founder's supplementary local tests; macOS stays experimental. Record non-blocking defects/unverified environments in KNOWN_ISSUES.md; do not add indefinite all-environment gates or claim unperformed tests.
