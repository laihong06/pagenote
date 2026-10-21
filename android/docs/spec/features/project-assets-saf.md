# 项目资源 SAF 直读写

## Scope

补 Task 3 最后一块。安卓 SAF 项目即源目录本身（直读直写），无需桌面式 uploads 中转；本片只做引用校验 + 缺失报告 + 预览侧已落地的本地加载，不做搬运。

## Semantics

- 打开项目时：扫描 `right.md` 的 `assets/<name>` 引用，经 `DocumentsContract` 查 `src/assets/<name>` 是否存在；缺失列表记入 `noteStatus` 级 warning（页面照常打开，不阻断）。
- 保存笔记时：文本中的 `assets/<name>` 引用随 `right.md` 直写，不搬运；新增附件由用户经系统文件管理器放入 `src/assets/`，应用只读。
- 预览时：标准项目链接 `assets/<uuid>.<ext>` 与桌面旧链接 `/attachments/<uuid>.<ext>` 均改写为 `pagenote-asset://...`，由 `NoteAssetSchemeHandler` 从 SAF 项目 `src/assets/` 读取；存在时显示，缺失时显示占位并给出 warning。
- `ProjectAssets` File 版 helper 保留给 shared-tests 契约回归，不删。

## Acceptance (EARS)

- When 打开含缺失资源引用的项目时，阅读器 shall 照常打开并提示缺失文件名。
- When 保存含资源引用的笔记时，shall 直写引用文本，不搬运文件。
- When 预览标准项目中的 `assets/<uuid>.<ext>` 图片时，shall 通过持久 SAF tree URI 从 `src/assets/` 本地读取，不发起网络请求。
- When 预览缺失资源时，shall 显示占位并记 warning。

## Verification

- JVM：引用扫描（存在/缺失/无引用三态）与项目相对图片 scheme 改写测试。
- `cd android && ./gradlew :app:testDebugUnitTest --offline` 通过；真机待 task 12。

## Real Project Verification (2026-10-18)

- A private test copy of the user's 69-page completed course PDF and page notes was generated outside the repository in the standard v1 project layout. The original desktop PDF, notes, and attachments were read only.
- The app opened the project through the real DocumentsUI folder picker and a persistable tree grant on API 35 4 KB and 16 KB tablet AVDs. Both reported `1 / 69`; the 16 KB AVD reports `getconf PAGESIZE = 16384`.
- On the API 35 4 KB tablet AVD, page 31 was opened in the reader. The PDF page, Markdown note, LaTeX, and the note's project-relative PNG all rendered in the actual preview. This found and fixed the missing rewrite for standard `assets/<uuid>.<ext>` links.
- The full app JVM unit suite and `:app:assembleDebug` pass. The 16 KB AVD verified real project open at page 1; the visual page-31 assertion was performed on the 4 KB tablet AVD.
- This was an import/render check only. Saving notes, all-page visual review, handwriting, OCR on this file, and the Agent workflow remain separate acceptance work.
