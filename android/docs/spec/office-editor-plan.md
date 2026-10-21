# Android Local Office Editor Plan

**Spec:** `features/desktop-ui-and-office-parity.md`

**Status:** Active; Android 14/API 34 is the minimum supported runtime.

## Goal

Move the desktop Word and PowerPoint workflows to Android without Windows COM, macOS Office automation, external Office apps, or a network conversion service. Keep the original `.doc`, `.docx`, `.ppt`, or `.pptx` file authoritative inside the pagenote project. Generated PDF pages are disposable previews, never replacements for the source.

## Engine Direction

Evaluate the Android LibreOfficeKit implementation from Collabora/LibreOffice as the primary local engine. Upstream Android bundles LibreOffice core into the application as `liblo-native-code.so` and uses its own editor/rendering stack; there is no assumed lightweight Maven/AAR dependency. Integration must include the editor surface and lifecycle needed to run in the pagenote process, not launch the separately installed Collabora app.

The upstream build guidance calls for a Linux native build environment. The current development host is Apple Silicon macOS with Android SDK/NDK installed; Docker Desktop is installed but its daemon was not running during this inspection. First establish a reproducible Linux builder, then pin upstream source revisions and record license notices, build flags, ABI, NDK and generated artifact hashes. Do not check opaque generated libraries into the app without a source/build recipe.

ONLYOFFICE mobile integration is not selected because its documented Android integration requires a Document Server URL, which violates the offline/local requirement.

## Implementation Slices

1. **Engine build and embedding proof**
   - Pin compatible LibreOffice core and Collabora Android revisions.
   - Build the arm64 Android core and Android editor components from source using a reproducible Linux container/toolchain.
   - Prove a pagenote-owned Activity/View can load, render, accept edits, and release the engine without network access.
   - Record native package size, startup behavior, API 34 runtime, licensing, and required upstream patches. Keep the desktop reader usable while this slice is isolated.

2. **Project source and preview**
   - Extend project source validation to `.doc/.docx/.ppt/.pptx` only after the engine can open them.
   - Copy the SAF source into an app-owned working directory; calculate SHA-256 and preserve the original SAF revision.
   - Export a local PDF preview from the engine. Key the disposable preview by source hash, source format, engine revision, and export settings.
   - Feed preview pages into the existing PDF reader/notes alignment; never treat the preview PDF as the editable source.

3. **Edit and Save & Return**
   - Open the same pinned working copy in the in-process editor surface.
   - On Save & Return, save to the working copy, compare the current SAF source revision with the opening revision, preserve a recoverable copy, and write the original Office format back.
   - Refresh the PDF preview and page count only after successful write-back. Preserve notes, pen strokes, annotations, and AI review files; retain out-of-range page data if pagination changes.
   - On conflict, provider error, permission loss, or engine crash, keep the last valid source and offer recovery; never report success early.

4. **Text selection and acceptance**
   - Expose AI selection actions only when LibreOfficeKit provides a real text selection and stable page mapping; do not synthesize character anchors from OCR or preview text.
   - Use source fixtures for all four formats and verify offline open → edit → save → close → reopen on one API 34 tablet AVD.
   - Cover source revision conflicts and failed SAF writes with focused policy/unit tests; physical pen-only differences remain diagnostic, not a release matrix.

## EARS Acceptance

- When an Office project opens, the app shall render its source locally while preserving the original extension and bytes until the user saves an edit.
- When the user enters Office editing, the editor shall run inside the pagenote process and shall remain usable in airplane mode.
- When the user chooses Save & Return, the app shall verify the source revision, preserve recovery data, save in the same Office format, regenerate its preview, and return to the same project/page context.
- If an engine or SAF operation fails, the app shall retain the last valid source and clearly identify any recovery copy without claiming the change was saved.
- When editor selection cannot produce a reliable source/page anchor, the app shall not offer a misleading AI text-annotation action.

## Verification Records

- Official Android engine/build references: `features/desktop-ui-and-office-parity.md` §4 and §7.
- Current project opener rejects Office extensions; do not mark source import/editing complete until slices 1–4 pass.
- Targeted tests/builds run after each slice; Android 14/API 34 AVD is the only routine device acceptance target.
