# Help And Diagnostics

## Scope

Implement the existing Help menu commands as local Android-native content. No help command opens a browser or sends data automatically. Diagnostics are generated only when the user opens the diagnostics command; copy/share each require a separate explicit action.

## Behavior

- About shows the installed app version and local project-format context.
- Keyboard shortcuts documents and implements external-keyboard page navigation (`Alt+Left` / `Alt+Right`) and current-note save (`Ctrl+S` / `Cmd+S`). Standard navigation keys remain available to the focused document.
- Privacy and licenses explains local project/OCR storage, the explicit OpenAI-compatible API boundary, encrypted model-key storage, and bundled open-source license notices.
- Diagnostics contains app version, Android API level, device manufacturer/model, whether a project is open, source kind, and page count. It must not include project names, paths, page text, notes, annotations, API keys, endpoint URLs, or model identifiers.
- Copy and share are visible only on the diagnostics page and only run after the corresponding user action.

## EARS Acceptance

- When a user selects About, Shortcuts, or Privacy and licenses, the app shall display the matching local content without network access.
- When a supported external keyboard sends `Alt+Left`, `Alt+Right`, `Ctrl+S`, or `Cmd+S` while the reader is active, the app shall run the matching page or save action through the normal reader persistence flow.
- When a user selects Diagnostics, the app shall show a local metadata snapshot excluding project content and model secrets.
- When the user taps Copy or Share on Diagnostics, the app shall transfer only the displayed diagnostic snapshot to the selected local system action.
- While any help page is open, dismissing it shall return to the reader without changing the project or note draft.

## Verification

- One focused Compose test verifies diagnostic content and explicit copy/share callbacks.
- Run app JVM tests, Android test compilation, `:app:assembleDebug`, and the single API 34 tablet AVD help interaction test.
