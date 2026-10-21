# Android Pen Style Controls

## Scope

Enable the existing handwriting-menu color and width commands using the desktop annotation defaults and width choices. The selected style applies to new freehand pen/highlighter strokes and newly created PDF vector, text, and region annotations. If the user has selected one existing annotation or stroke, applying a changed color or width also updates that item as specified in `desktop-semantic-parity.md` §44. Without a selection, saved items remain unchanged.

## Interaction

- The color command opens a native dialog with color swatches and a custom `#RRGGBB` field. The default is `#dc3545`; accepting a valid custom color applies it to future annotations. Invalid input disables Apply, and dismissing the dialog leaves the current style unchanged.
- The quick swatches are red `#dc3545`, blue `#185abd`, green `#2e7d32`, orange `#ef6c00`, purple `#7b1fa2`, dark `#212121`, amber `#f9a825`, and teal `#00838f`.
- The width command opens a native single-choice list for the desktop values `1.5`, `3`, `6`, and `10`. The default is `3`.
- Both choices are available only when the current PDF project is writable, matching the handwriting tools. The chosen style remains active across page navigation and Activity recreation.
- The menu labels and existing annotation JSON schema remain unchanged.

## Data and Validation

- A style is a six-digit RGB hex color and one of the four desktop width options. Colors are normalized to lowercase before use.
- Use the style for new pen/highlighter strokes and new `line`, `arrow`, `rect`, `ellipse`, `text`, and `region` annotations.
- With a selected item, update only that item through its normal page store and undo history; without a selection, update only the active drawing style.

## Acceptance (EARS)

- When the user chooses a color from a swatch or enters a valid RGB hex value, the reader shall apply that color to subsequently created annotations.
- When the user enters an invalid color, the reader shall prevent applying it and preserve the previously active style.
- When the user chooses one of the four width options, the reader shall apply that width to subsequently created annotations.
- When the user navigates to another page or recreates the Activity, the reader shall keep the selected color and width.
- When an item is selected and its style changes, the reader shall persist the selected item's new style and make the change undoable without altering other page items.
- When no item is selected and the active style changes, the reader shall leave all existing items unchanged.

## Verification

- JVM: verify default values, color normalization/validation, and the exact desktop width choices.
- Android 14/API 34 tablet AVD: one focused Compose test covers swatch/custom color and width selection, then draws through the real `PdfPageViewport` and verifies the emitted `PenStroke` keeps the selected color and width. The Xiaomi Pad 5 Pro remains the only hardware target; no other device/API/ABI matrix is required.
- Build: compile app and instrumentation sources and assemble Debug app/test APKs. Do not run other connected devices.
