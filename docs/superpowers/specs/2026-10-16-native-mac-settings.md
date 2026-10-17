# Native macOS settings

The user approved replacing the uncomfortable web settings dialog with genuine
macOS controls. The existing Electron reader remains in place. Mac builds target
Apple Silicon only, and the handoff app is `release/mac-arm64/pagenote.app`.

Use a small Swift/AppKit accessory application owned by the Electron process.
It has no Dock icon. A native toolbar selects General, Appearance, Reading and
AI panes. Use system fonts, semantic system colors, native pop-up buttons,
switches and a native text editor. The settings window has a standard titlebar,
close button, no maximize/minimize, and keyboard editing commands.

Electron launches/reuses the helper through a fixed preload IPC channel. JSON
over private standard streams carries state and validated setting requests.
The helper receives no server credentials or arbitrary JavaScript. The parent
applies settings through existing renderer setters and prompt APIs, preserving
annotation anchoring, existing data and live menu/theme updates. Windows and
browser clients retain the existing web interface.

General contains language. Appearance contains app appearance and Markdown
theme. Reading contains page transitions and editor expansion. AI contains the
existing six prompt modules, current-module save and restore-default controls.
Ordinary preferences save immediately; errors restore the last confirmed value.
AI drafts survive pane/module switches. Closing or quitting with drafts offers
Save, Discard and Cancel. Save failures keep drafts and the window open.

The helper remembers the last pane, follows the selected/system appearance,
exits when its parent exits, and never prevents ordinary text copy/paste or
undo. Cmd-comma and all visible settings entries open the native window.
Closing settings leaves the reader open. Quit waits for settings draft handling
and the existing note flush.

Validation: real AppKit helper launch/reuse/close and parent cleanup; settings
and prompt persistence through existing APIs; invalid request rejection; reader
state/annotation continuity; full regression; actual native control interactions
and light/dark screenshots; arm64 local packaging and packaged smoke tests.
Public release downloads remain on v1.0.0; this is 1.0.1-dev.3 local development.
