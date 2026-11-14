# Note save conflicts / 笔记保存冲突

The editor sends the SHA-256 `expectedRevision` of its last acknowledged note to
`PUT /api/documents/:id/notes/:page`. The server compares it inside the write queue,
immediately before changing the note. Concurrent writes with the same base cannot
silently overwrite each other. Retrying identical saved content is idempotent.

On a mismatch the server leaves the saved note unchanged, archives the attempted
draft in that page's history, and returns HTTP 409 with `code: NOTE_CONFLICT`,
`currentMarkdown`, `revision`, and `draftVersion`. If history storage fails, the
request fails without acknowledging a conflict backup or replacing the saved note.

The persistent footer offers retry for ordinary failures and a comparison dialog
for conflicts. Reloading the saved version leaves the draft available in history;
the dialog also offers a Markdown download. There is no automatic force-overwrite.

Recovery storage retains both draft text and its original base. Reloading a draft
does not adopt a newer disk revision as its base. Legacy drafts without a known
base require review when they differ from the saved note.

If the recovery cache is full, the editor keeps the newest text in memory and
warns that an export is needed after a failed save. It does not restore an older
cached draft over that text. Recovery-cache cleanup failures do not turn an
acknowledged server save into a failed save.

AI updates forward the caller's expected revision, or the revision read by the
controller, to the atomic server check. Callers should supply the revision returned
by their original read to protect edits prepared from an older read. For backward
compatibility, REST clients omitting `expectedRevision` remain unconditional; this
change does not make those legacy writes conditional.

编辑内容与磁盘版本冲突时，不会自动覆盖任何一方。当前草稿保留在编辑区，
并备份到“文件 → 版本历史”。可在冲突对照窗口下载草稿，或重新载入已保存
内容，再从历史版本取回草稿继续整理。普通保存失败可点击底栏“重试保存”。

Run `npm run test:note-conflicts` for independent-writer races, in-flight edits,
queued saves, failed-save retry, recovered and legacy drafts, and resolution flow.
