# Reader Settings Persistence

## Scope

Persist the Android reader's `ReaderSettings` with Jetpack Preferences DataStore. The store is app-local and independent from project files and desktop settings. Expose a `Flow<ReaderSettings>` for observation and an atomic suspend save operation.

## Storage Contract

- DataStore file: `reader_settings.preferences_pb`.
- Keys and types:
  - `theme`: string; allowed values `system`, `light`, `dark`, `paper`.
  - `language`: string; allowed values `system`, `zh-CN`, `en-US`.
  - `left_percent`: integer; allowed range `20..80` inclusive.
  - `note_animation`: boolean.
- Defaults when a key is absent:
  - `theme = system`
  - `language = system`
  - `left_percent = 42`
  - `note_animation = true`
- A save validates the complete `ReaderSettings` before changing storage, then writes all four keys in one DataStore transaction.

## Invalid Data

- An unknown theme or language falls back to that field's default.
- A split percentage outside `20..80` falls back to `42`.
- A key stored with the wrong primitive type falls back to that field's default.
- Invalid fields do not discard other valid stored fields.
- A DataStore read or disk-corruption error is surfaced to the caller; it is not silently converted into defaults.
- A save containing an unsupported theme/language or out-of-range split percentage fails validation and leaves the stored snapshot unchanged.

## Acceptance

- A new store emits the defaults.
- Saving settings and opening a new store on the same file emits the saved values.
- Each malformed field falls back independently while valid sibling fields survive.
- Invalid saves fail without partially writing any key.
