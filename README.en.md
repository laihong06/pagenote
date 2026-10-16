# pagenote · Stable

**[⬇ Windows stable installer](https://github.com/laihong06/pagenote/releases/download/v1.0.0/pagenote-1.0.0-windows-x64-setup.exe)** · [Stable portable ZIP](https://github.com/laihong06/pagenote/releases/download/v1.0.0/pagenote-1.0.0-windows-x64.zip) · [Stable source](https://github.com/laihong06/pagenote/releases/download/v1.0.0/pagenote-1.0.0-source.zip)

This is the **main stable branch**, documenting Windows **v1.0.0**. For new features and beta installers, use the [beta branch](https://github.com/laihong06/pagenote/tree/beta). No stable macOS installer has been released.

<img src="build/icon.png" alt="pagenote paired-page icon" width="88">

English · [简体中文](README.md)

pagenote puts a document on the left and notes for the current page on the right.

## Where pagenote started

As AI becomes part of everyday life, using it to study is a familiar choice. We give it slides and papers, hoping it will build on what we already know and guide us, step by step, toward an understanding of difficult material.

Yet this way of learning has practical shortcomings. The source and explanation sit in separate windows, and switching between them makes it easy to lose our place. AI sometimes rearranges the material, disrupting the order of the lesson. Asking it to explain an entire document at once can run into context and output limits, producing a summary that skips derivations, unfamiliar notation and examples. We may feel we have understood it, only to struggle when solving a problem or putting it into practice.

These frustrations led to Pagenote. The idea is to keep each source page beside its explanation and give it the attention of a separate request: start from familiar knowledge, fill in the intermediate steps and continue from the preceding page. When something is unclear, select a passage or a figure and leave a request for another example or a different explanation. Notes can be revised as we learn, without repeatedly copying text and explaining the background. Illustrations, runnable code, mathematical notation and flowcharts give AI more ways to make abstract ideas understandable. That was the original purpose of Pagenote.

## Features in pictures

These are real screenshots of the current development build, using original demonstration content. The interface shown is in Chinese.

### Read the source beside its explanation

The left pane shows the document; the right holds Markdown notes for the active page. Notes follow page changes, and each pane scrolls independently. Read PDF and Markdown directly; Word and PowerPoint conversion uses locally installed Microsoft Office.

![Source document and notes for the current page](docs/images/paired-reading.png)

### Formulas, code and diagrams

Notes support LaTeX, syntax-highlighted code, Mermaid diagrams, tables and pasted images. Keep derivations and verification code on the same page, with the results rendered for reading.

Code highlighting runs locally, without downloading grammars. If rendering fails, include the app version, a minimal code block and a screenshot in your report; see the [rendering notes](docs/CODE-RENDERING.md).

If formulas appear as backslash commands, copied TeX delimiters may be double-escaped. Beta.5 now handles these forms; see the [formula compatibility notes](docs/FORMULA-RENDERING.md).

![Rendered formulas, Python highlighting and a Mermaid flowchart](docs/images/markdown-rendering.png)

### Expand the editor when needed

Choose the notes pane from the editing menu to expand it and reveal its tools. Edit rendered content directly, with code highlighting updating as you type. Source edits for Word, PDF and PowerPoint open in the system default app; save and return to refresh the preview.

![Expanded note editor with its contextual tools](docs/images/note-editing.png)

### Leave feedback where it belongs

Select text or an area and add an AI annotation, such as “Add an example here” or “Explain this in words before introducing the formula.” A movable panel opens in the same pane.

![Writing a revision request beside selected text](docs/images/ai-annotation.png)

Annotations collect in a workbench for the corresponding pane. You can also record requests about the whole document. Double-click a marker to open its workbench, or right-click to delete it. A connected AI client can read the requests through MCP and revise the notes.

![AI annotation workbench with a local revision request](docs/images/ai-workbench.png)

Pagenote currently supports two-pane reading, page-linked notes and AI annotations. Its MCP interface lets AI read and edit page content. The app does not include a model; detailed page-by-page explanations and image generation require an AI client with the relevant capabilities.

Follow the [page-by-page teaching workflow](docs/MCP.en-US.md#page-by-page-teaching): make a separate generation request for each page, give it sufficient current-page context and include the previous and next source pages without waiting for earlier explanations. Do not generate a whole chapter and split it into pages afterward.

Beta.5 includes a [page tutor skill](skills/pagenote-page-tutor/SKILL.md). Use **AI Annotations → Copy page-by-page teaching prompt**, or select the MCP prompt `pagenote-page-tutor` and supply a learner background, page range and goal. AI clients can also read `page-notes://skills/page-tutor`. The template guides a connected client; it does not call a model by itself.

Use **Settings → AI settings** to edit or restore the default system prompt. General document tasks keep their scope; the page tutor applies only to relevant learning requests. The prompt is saved locally. Reconnect the AI client after saving. MCP supplies the instructions; the client controls its actual system-prompt layer.

Beta.5 reads source and notes separately, fetches only the needed note passages, and saves concise page handoffs inside the project. Handoffs are marked stale when source or notes change, leaving more context for the current explanation. See the [MCP read options](docs/MCP.en-US.md#token-efficient-reads).

It also bundles offline Chinese/English OCR and prepares reusable positioned text, confidence, page/region images and native Markdown table/Mermaid assets. Sources and visuals remain available for checking uncertain formulas and charts. See [local page preparation](docs/LOCAL-PREPARATION.md).

**Current Windows version: 1.0.0 stable**; macOS remains experimental.

## Download and install

[Windows x64 installer](https://github.com/laihong06/pagenote/releases/download/v1.0.0/pagenote-1.0.0-windows-x64-setup.exe) · [Complete Windows ZIP](https://github.com/laihong06/pagenote/releases/download/v1.0.0/pagenote-1.0.0-windows-x64.zip) · [Release notes and checksums](https://github.com/laihong06/pagenote/releases/tag/v1.0.0)

1. Download `pagenote-VERSION-windows-x64-setup.exe` from this repository's **Releases**. **Code → Download ZIP** contains source code, not an installer.
2. Run the installer and choose a location. Installation is per user and normally does not require administrator privileges.
3. Launch **pagenote** from the desktop or Start menu.
4. Choose Simplified Chinese or English under **Settings → Language / 语言**.

Alternatively, extract the complete Windows ZIP distribution and run `pagenote.exe`. Keep its accompanying resources together.

Releases should include `SHA256SUMS.txt` and pass security review. The current local build has no publisher signature. If security software blocks it, stop and report the detection; **do not disable protection or add an exclusion**.

### macOS status

macOS remains experimental; it is not offered as a stable download here. Check the [beta branch](https://github.com/laihong06/pagenote/tree/beta) for matching test builds and availability. Contributor and Office documentation remain in [Mac setup](docs/MAC-OFFICE.md).

## Other controls

- **Hand** pans the page. **Pen** provides ink, highlighting and erasing.
- Ctrl + mouse wheel zooms the current pane. Notes zoom changes the displayed font size.
- Notes follow the active source page as page boundaries cross the viewport midpoint. Page-transition animation can be disabled in settings.
- Automatic note history and restore. Projects, ink and annotations stay on the local machine.
- Data shared with an external AI client is governed by that client's privacy policy.

## Portable projects

Create a project using **File → New project** and choose its name and folder:

```text
My project/
  project.pagenote          # JSON manifest with relative links
  src/
    left.md                # Source: may become left.pdf, left.docx, etc.
    right.md               # Page notes separated by <!-- pagebreak -->
    assets/                # Images and attachments
    annotations.json       # Ink, created when used
    ai-reviews.json         # AI annotations, created when used
```

With the installed app, double-click `project.pagenote`, or choose it under **File → Open project**. For a ZIP distribution without an association, run `register-project-files.cmd` in the newer distribution folder once. Register again after moving the app. If Windows already has another default, choose pagenote under **Open with**.

Legacy `project.page-notes.json` files remain supported through the app. The `left` and `right` links are read on opening. Move the complete folder, not just its manifest.

The brand is now pagenote. When the legacy document store exists and the new document store does not, data is copied while preserving the legacy folder. Existing projects do not need to be renamed or rebuilt.

Beta.3 fixes migration, staged file replacement and cross-volume copy failures in some encrypted or redirected Windows data folders. Interrupted copies retain recovery files; these fallback paths cannot offer the same power-loss guarantees as atomic renames.

On Mac, move pagenote to Applications and open projects through Finder or **File → Open project**. If needed, select pagenote under **Open With**; Windows `.cmd` registration scripts do not apply to Mac.

## Requirements

- Windows 10/11, x64: v1.0 stable, approved by the founder after accepting LTH0001's tests together with their own supplementary local tests.
- macOS 12 or later: experimental Apple Silicon and Intel builds; Windows stable acceptance does not apply to macOS.
- ARM64 Windows is not yet verified.
- PDF and Markdown do not require Office. Word/PPT import, project preview and post-save refresh need locally licensed **Microsoft Word / PowerPoint** on Windows and macOS.
- On first Mac integration, allow pagenote to control Word / PowerPoint; if denied, enable it under **System Settings → Privacy & Security → Automation**. Mac Word editing uses a separate window with MCP read, selection, revision-checked replacement and PDF-rendered screenshots. See [Mac Office notes](docs/MAC-OFFICE.md).
- The distribution includes the Node.js runtime used by MCP. End users do not need a Node.js installation.

## Build from source

Install Node.js 22.13 or newer, then run:

```bash
npm ci
npm run build
npm run desktop
```

Use `npm run dev` for browser development, `npm run package:desktop` for the Windows installer and ZIP, or `npm run package:dir` for a folder build. Use `npm run package:mac` for macOS DMG and ZIP (Apple Silicon and Intel), or `npm run package:mac:local` for a local-arch folder build. The test suite runs on Windows and macOS.

See the [release guide](docs/RELEASING.en-US.md), [terminology specification](docs/TERMINOLOGY.md) and [release-readiness report](docs/RELEASE-READINESS.md). Bug reports should include the version, Windows version, reproduction steps and redacted logs. Do not upload private projects or MCP connection keys.

## Feedback and contact

Please [open an issue](https://github.com/laihong06/pagenote/issues/new) to report a bug, with the app version, reproduction steps and a screenshot so I can investigate.

For suggestions or a conversation about the project, email me at [chennanfeng310@gmail.com](mailto:chennanfeng310@gmail.com).

## License

[MIT License](LICENSE), including commercial use. Dependencies have separate licenses; retain `THIRD-PARTY-NOTICES.txt`, the Node.js license and Electron/Chromium license files when distributing. Microsoft Office licensing is the user's responsibility.

MCP integration: [English](docs/MCP.en-US.md) · [中文](MCP接口说明.md)

Editor and icon changes: [reference](docs/EDITOR-REFERENCE.md).
Standalone imports edit the managed local copy; projects edit the linked file in `src`. Save, then return to pagenote.

Beta.5 routes tasks through `page-notes://prompts/index`. Read only relevant, changed modules via `page_notes_read_prompt`. AI settings can customize each module. Page requests may run concurrently: recommend `max(1, floor(total pages / 10))`, capped by remaining pages, client/service limits and available resources. `page_notes_validate_note` checks formatting and local image assets before publication; it is not a guarantee of factual correctness or cross-device rendering.

## Contributors

Thanks to [obroccolio](https://github.com/obroccolio) for Mac support and downloadable builds, and [LTH0001](https://github.com/LTH0001) for formula regression coverage and acceptance-test improvements. Windows v1.0 was approved from LTH0001’s tests plus the founder’s supplementary local tests. Forks and PRs are welcome; include a changelog entry and describe the platforms and scope tested.

[Release policy](docs/RELEASE-POLICY.md) · [Known issues](KNOWN_ISSUES.md)
