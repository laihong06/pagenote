# Markdown Rich Clipboard Paste

## Scope

Extend the existing explicit `Paste as Markdown` reader command to convert clipboard HTML into safe Markdown. This is an Android-only editor workflow; it does not change project format or add automatic clipboard monitoring.

## Behavior

- Read only the first clipboard item after the user selects `Paste as Markdown`.
- Prefer `ClipData.Item.htmlText` when present. If absent, use the item's plain `text`. If HTML conversion yields no non-whitespace Markdown, fall back to plain text.
- Convert semantic paragraphs, headings, emphasis, strong, strike, underline, links, ordered/unordered lists, blockquotes, code, fenced code blocks, line breaks, horizontal rules, and basic tables to readable Markdown/GFM.
- Convert images to their escaped alt text only. Never fetch or retain remote image URLs.
- Parse locally using Jsoup with a narrow allowlist. Drop scripts, styles, embedded objects, forms, event attributes, unsafe URL protocols, and unsupported active subtrees. Do not load linked resources.
- Insert or replace the current editor selection using the existing note-edit mutation path. Preserve the resulting caret at the end of inserted text, keep the edit undoable, and leave project persistence to normal Save.
- Empty clipboard behavior remains the existing localized status message; plain-text-only clipboard behavior remains byte-for-character equivalent to the existing command.

## Acceptance

- HTML with nested formatting and lists converts deterministically to Markdown; clipboard HTML never executes or triggers network access.
- Script/event-handler content is absent, unsafe links do not survive as links, and image alt text can remain without an image URL.
- Both selected-text replacement and collapsed-caret insertion produce the exact expected note draft and caret offset.
- HTML absent uses plain text. HTML with no useful output also uses plain text.
- Save changes only the active note page; the adjacent page remains unchanged. Read-only and preview states keep the command disabled.

## Verification

- JVM tests cover conversion rules, malformed HTML, unsafe elements/attributes/URLs, images, and fallback behavior.
- API 34 tablet instrumentation covers explicit clipboard access, selection replacement, caret insertion, page-scoped save, and editor undo behavior.
- Run the focused app unit test, the focused instrumentation class on the sole API 34 tablet AVD, AndroidTest compilation, `:app:assembleDebug`, and `git diff --check`.
