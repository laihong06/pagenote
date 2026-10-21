# Android Markdown Note Font Size

## Scope

Implement the Android `Paragraph > Font size` command for the current Markdown note editor. The selectable values match the desktop control: 12, 15, 18, 20, 24, 28, and 32. The choice is an app-level reader preference, not project content, and applies to note editing and rendered note preview.

This does not resize the source-document pane, Android system UI, pen strokes, or project data. It adds no dependency and does not change the project format or note text.

## Behavior

- Enable the command only while a writable Markdown note editor is active and the reader is not busy. Keep it disabled without a project, for read-only projects, and while viewing preview.
- Open a selection dialog showing the current value. Cancel closes without changing or saving the preference.
- Apply the selected value to the Markdown editor and rendered note preview, including the renderer's base LaTeX size. Existing note content, selection, draft, page revision, and save state remain unchanged.
- Persist the value in the existing reader-settings DataStore so it survives Activity recreation and app restart. The preference is global to this app installation and is not written to a `.pagenote` project.
- If persistence fails, keep the dialog open, keep the previous applied value, and show a localized error.
- Unknown persisted values recover to the default of 15; new values outside the desktop option set are rejected by the settings policy.

## Acceptance Criteria (EARS)

- While a writable Markdown editor is active, when the user opens `Paragraph > Font size`, Android shall show the seven desktop-compatible values with the saved value selected.
- When the user applies a value, Android shall persist it as a reader preference and apply it to the current note editor and rendered preview without changing Markdown text or page revisions.
- When the user cancels the font-size dialog, Android shall leave the saved and applied value unchanged.
- When the reader has no project, is read-only, is busy, or is in preview mode, Android shall disable the font-size command.
- When an invalid stored size is read, Android shall use 15 without preventing other valid reader settings from loading.
- When the app is restarted after a successful change, Android shall restore the selected size and apply it to the note editor and preview.

## Verification

- Focused JVM tests cover the supported values, validation, fallback, and DataStore round-trip across store instances.
- One focused Android 14/API 34 tablet AVD Activity test covers no-project/read-only/preview menu gating, cancel/apply, editor and preview sizes, unchanged note content, and preference restoration after Activity recreation.
- Build only `:app:assembleDebug` for this slice. Xiaomi Pad 5 Pro acceptance remains part of Android task 12; the AVD is not hardware evidence.
