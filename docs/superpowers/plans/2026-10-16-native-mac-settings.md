# Native Mac Settings Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan
> inline, with one independent final review. Preserve existing uncommitted work.

**Goal:** Replace the Mac web settings entry with a real AppKit settings window.

**Architecture:** Swift accessory helper with native controls; Electron owns its
lifecycle and serializes validated requests over stdio. Existing renderer
setters and prompt APIs remain the source of truth.

**Tech Stack:** Swift/AppKit (macOS 12+), Electron preload IPC, Node.js.

**Spec:** `docs/superpowers/specs/2026-10-16-native-mac-settings.md`

## Global constraints

- Apple Silicon application builds only; canonical app `release/mac-arm64/pagenote.app`.
- Keep existing Windows/browser settings and data compatible.
- Do not publish/reuse release versions; use 1.0.1-dev.3 and update Changelog.
- Keep existing uncommitted changes; do not commit partial prior work.

## Review focus

- Missing/crashed native helper must give a visible error with a web fallback.
- Unsaved AI drafts must survive pane changes and cancelled/failed quits.
- Helper EOF/parent exit must close native windows without leaving an orphan.
- New preference writes must use existing setters, including ink preservation.
- System appearance and changed language must update an already-open native UI.

### Task 1: Native window and process controller

Files: create `desktop/mac-settings.swift`, `desktop/mac-settings.cjs`,
`scripts/build-mac-settings.mjs`, `tests/native-settings.mjs`.
Interface: `createMacSettings({executable, readState, applySetting, readPrompt,
savePrompt, paneFile, onQuit, onClosed, onError})` exposes `open(pane)`,
`sync()`, `prepareQuit()`, `dispose()`.

- [x] Write/run the integration regression: real helper opens, reuses one PID,
      reports a native window, rejects unknown panes/requests and cleans up.
- [x] Implement AppKit panes, standard controls, draft handling and stdio protocol.
- [x] Compile arm64 helper with macOS 12 deployment target; rerun regression.

### Task 2: Existing app/settings integration

Files: modify `desktop/main.cjs`, `src/main.js`, `server.mjs` only if required;
create `desktop/preload.cjs`; adapt browser-fallback test setup.
Interface: preload exposes only `desktopSettings.open(pane)`; renderer exposes
`openSettings(pane)`, `getSettingsState()` and `applySetting(key,value)`.

- [x] Add failing real Electron test: clicking settings and Cmd-comma open a
      native helper, HTML dialog stays closed; setting changes persist live.
- [x] Route all menu/UI settings entries through the native controller and
      existing setters; validate keys/types; coordinate graceful quit.
- [x] Test language/theme/layout/prompt writes, failures, fallback and restart.

### Task 3: Packaging and handoff

Files: `package*.json`, `scripts/build-config.mjs`, `.gitignore`, `CHANGELOG.md`.

- [x] Bundle the compiled helper outside ASAR; build it for local Mac builds and CI.
- [x] Bump local version to 1.0.1-dev.3; keep public links unchanged.
- [x] Run format/version/build checks, native test, full npm test, MCP and OCR.
- [x] Review native UI with real interactions and capture light/dark previews.
- [x] Run independent review, fix important findings, package/test arm64 app.
- [x] Save-and-quit the user's older app before replacing the test bundle;
      reopen that exact path and verify the running version and native entry.

## Execution record

Implementation is authorized by the user's explicit request to make settings
native. Work continues in the existing development checkout to preserve the
ongoing Mac changes and the user's fixed testing path.

Completed: native helper and renderer integration passed real process/UI tests.
Final review identified and fixed close-to-quit escalation, failed prompt loading
and pipe/spawn failure handling. Regressions were observed failing before fixes;
manual AppKit checks covered Chinese/multiline paste, Cmd-S, retry, failed save,
cancelled close, pane changes and live language/appearance updates.

Final verification: npm test, format/version checks, arm64 local packaging and
packaged native settings/MCP/prepared OCR tests passed. Shipped ASAR controller,
preload and renderer match workspace files. The user's previous instance was
quit normally; the updated canonical app was reopened and its native settings
window visibly reports 1.0.1-dev.3. No public release was published.
