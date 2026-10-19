# Reader Settings UI

## Scope

Expose the persisted Android reader preferences through a native settings surface and apply them to the running reader. This specification is Android-only and belongs under `android/docs/spec/`.

## Behavior

- The reader toolbar exposes a settings action on phone and tablet layouts.
- The settings surface edits a draft containing theme, language, side-by-side split percentage, and note transition animation.
- Theme choices are `system`, `light`, `dark`, and `paper`. Language choices are `system`, `zh-CN`, and `en-US`. Split percentage remains within 20..80 inclusive.
- Save validates and writes the complete draft through `ReaderSettingsStore.save`; the store's atomic DataStore transaction remains the persistence boundary. Cancel discards the draft.
- A save error remains visible in the settings surface and does not silently dismiss it.
- The selected theme is applied to the Material reader immediately after a successful save.
- The split percentage controls the PDF/note ratio only in the wide side-by-side layout; narrow stacked layout remains unchanged.
- The language selection localizes reader and settings controls. For strings already represented by `locales/catalog.json`, Android uses the same Chinese source wording and meaning.
- `noteAnimation` controls the transition used when the displayed page note changes. Disabling it removes that transition without affecting note content or save behavior.

## Acceptance

- Opening settings shows the current persisted values; canceling edits leaves persisted values unchanged.
- Saving a valid draft persists all fields together and the running reader reflects theme, language, split ratio, and animation preference.
- Invalid or failed saves leave the settings surface open and expose an actionable error.
- Phone and tablet layouts both expose the settings action; split ratio affects only side-by-side layout.
- English and Chinese reader controls use localized Android resources without changing desktop locale files.

## Verification

- Existing DataStore and settings policy tests remain green; add focused tests for any new settings mapping or validation behavior.
- Android unit tests and debug APK assembly validate the reader integration.
- Device acceptance checks dialog usability, locale changes, theme contrast, slider interaction, and animation toggle.
