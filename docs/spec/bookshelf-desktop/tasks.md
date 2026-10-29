# Desktop Bookshelf Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` to implement this plan task-by-task. Keep checkbox status current. The user has selected work in the current workspace without additional subagents.

**Goal:** 将已确认的 Android 书架功能及 UI 同步到 Windows / Mac。

**Architecture:** 在现有项目存储和阅读器外增加独立书架模块，目录内容是书籍列表的真实来源。
共享 Android 封面 sidecar，但本机目录和阅读位置独立保存；不重写阅读器。

**Tech Stack:** Electron、Node.js / Express、原生 DOM / CSS、既有 PDF.js / Sharp、Playwright。

**Spec:** [requirements.md](requirements.md)；技术边界见 [design.md](design.md)。

状态：实现已完成；定向验收与实际操作见 [validation.md](validation.md)。未勾选的扩展测试和提交步骤不冒充已执行。

## Global Constraints

- Windows 和 Mac 共用 UI；Mac 只构建 arm64，固定验证 `release/mac-arm64/pagenote.app`。
- 复用 `Note`、144:192 封面比例及 sage / rose / sky / graphite，不改变 Android 行为。
- 扫描深度 8、目录 2,000、单目录条目 10,000；越界或失败明确报告。
- 同名或同 ID 项目不合并；不自动搬移旧项目，不扫描项目内部目录或跟随目录符号链接。
- 不发布公共版本或改下载链接；每次 push 必须有对应 Unreleased 条目。
- 继续当前工作区，不启动子代理；不覆盖已有 Android / 雨课堂修改和无关未跟踪文件。

## Review Focus

1. 两个路径相同 manifest ID：分别显示，交替打开/保存不覆盖另一份内容（任务 1、3）。
2. 目录切换时旧扫描/封面仍在返回：不得污染新目录或继续操作旧 key（任务 1、2）。
3. 项目链接或 assets 被替换为符号链接：不得读写项目之外的数据（任务 1、2）。
4. 返回书架时笔记/笔迹保存失败：保持阅读器、草稿和重试入口（任务 3）。
5. 正在打开旧书时书架导入或雨课堂首张建项目：不得替换旧书原文或丢失旧书笔记（任务 4）。

---

## Task 1: 目录存储与项目发现

**Files:** 新建 `desktop/bookshelf.cjs`、`tests/bookshelf-store.mjs`；修改 `server.mjs`。

**Interfaces:**
- `createBookshelfStore({data})` 返回异步 `config()`、`bind(root)`、`setView({view,sort})`、
  `scan()`、`getBook(key)`；`scan()` 返回 `{config,books,warnings}`。
- `Book` 字段固定为 `{key,id,name,title,projectFile,ext,updated,revision,appearance,warning}`。
  key 是规范项目路径 SHA-256；revision 由源文件元数据及 sidecar 内容决定。
- 路由 `GET /api/bookshelf` 扫描；`GET/PUT /api/bookshelf/config` 读取/更新目录和视图配置。

- [x] 写 `bookshelf-store.mjs`：临时目录中的嵌套 PDF/MD、旧格式、重复 ID、损坏项目、
  失效根目录、扫描上限、项目内嵌目录、符号链接以及切换目录后旧 key 失效。
- [x] 运行 `node tests/bookshelf-store.mjs`，记录新增契约尚不存在导致的失败。
- [x] 实现存储/发现及路由；原子配置写入，串行写入保护，失败绑定不替换旧目录；
  测试可注入更小扫描上限，但生产固定使用全局约束。
- [x] 重跑该测试至通过，核对没有调用项目打开、文档复制或 Office 转换。
- [ ] 审核并只提交本任务拥有的差异；遇到同文件既有改动时保留，不整文件盲目覆盖。

## Task 2: 可移植封面与缩略图

**Files:** 扩展 `desktop/bookshelf.cjs`；新建 `src/book-covers.js`、`tests/bookshelf-covers.mjs`；
修改 `server.mjs`。

**Interfaces:**
- 存储增加 `saveAppearance(key,appearance)`、`saveCover(key,bytes)`、`coverSource(key)`。
  前两者返回更新后的 Book；后者仅内部返回 `{file,type,revision}`，资源经实际路径校验。
- `PUT /api/bookshelf/:key/appearance` 保存/重置 v1 设置；
  `POST /api/bookshelf/:key/cover` 上传图片；`GET /api/bookshelf/:key/cover-source` 提供安全源资源。
- `createBookCovers({pdfjs})` 返回 `observe(element,book)`、`clear()`、`dispose()`，内部负责
  视口加载、并发 2、128 项 / 48 MiB LRU 和销毁；不向全局暴露文件系统路径操作。

- [ ] 写封面契约测试：直接读写 Android v1 样本，`Note`/四种颜色、页码从零开始、
  标题 200 字符、安全文件名、未知版本回退；指定 PDF 页、缺失 Office 预览和图片失败。
- [ ] 加入 20 MiB / 20,000,000 像素输入上限、非法图片、路径穿越、assets 符号链接、
  复制后原图删除及重置不删除资产的回归断言，运行 `node tests/bookshelf-covers.mjs` 确认失败。
- [x] 实现安全元数据与图片路由，沿用 Sharp 和本机同源保护；前端用既有 PDF.js，
  PDF 完整适应封面。离开书架或重绑时释放任务和缓存，不把 PDF 失败冒充纸本成功。
- [ ] 重跑封面/存储测试，并在后续 UI 测试中验证 PDF canvas 非空及并发/取消契约。
- [ ] 审核并提交本任务拥有的差异。

## Task 3: 共享书架 UI 与安全导航

**Files:** 新建 `src/bookshelf.js`、`src/bookshelf.css`、`tests/bookshelf-ui.mjs`；
修改 `src/main.js`、`src/i18n.js`、`locales/catalog.json`、`index.html`、`package.json` / lock。

**Interfaces:**
- `createBookshelf({request,openBook,onNew,onImport,onSettings,onError,pdfjs,loadRecent})` 返回
  `show()`、`hide()`、`refresh()`、`state()`；`state()` 返回当前 config / books / visible。
- `window.pageNotes.showBookshelf()` 在 `finishEditing()` 成功后显示书架；
  `window.pageNotes.getBookshelfState()` 提供只读视图快照，用于现有文档控制/测试。
- `openBook(book)` 使用现有 `openProject(book.projectFile)`；项目打开成功后统一隐藏书架。
  书架页码键 `bookshelf-position:<key>` 附 `{id,page}`，恢复时校验 ID 与页码范围。

- [ ] 写隔离 Electron UI 流程：首次无绑定入口、重启保留绑定但不自动进书、选书/返回、
  搜索/排序/列表、纸本 `Note`、PDF 封面、自定义/重置；运行后记录预期缺失入口失败。
- [ ] 加入保存请求失败后仍留阅读器、同 ID 不同目录交替读写、长标题、语言切换不翻译
  用户书名、暗色/浅色及捕获窗口不启动书架的断言。
- [x] 实现独立书架模块和稳定网格/层板布局，使用 Lucide 图标及可访问名称/提示；
  核对官方图标包 API 后仅导入所需图标，不引入 UI 框架。
- [x] 连接主入口：移除普通启动自动打开 last-document，显式打开入口保持；返回前等待
  所有现有保存；仅暂停隐藏阅读器渲染，雨课堂采集不静默停止。焦点刷新合并重复请求。
- [x] 运行 `node tests/bookshelf-ui.mjs`、`node tests/project-open.mjs`、`node tests/project-launch.mjs`；
  在 1440x960 / 900x620 截图确认完整封面、标题不重叠及工具栏不溢出。
- [ ] 审核并提交本任务拥有的差异。

## Task 4: 统一新建、导入和原生设置入口

**Files:** 修改 `projects.mjs`、`server.mjs`、`src/main.js`、`index.html`、`desktop/preload.cjs`、
`desktop/main.cjs`、`desktop/mac-settings.cjs`、`desktop/mac-settings.swift`、`locales/catalog.json`；
新建 `tests/bookshelf-projects.mjs`，扩展 `tests/bookshelf-ui.mjs`、`tests/native-settings.mjs`。

**Interfaces:**
- 项目存储新增 `create({name,parent})`、`createFromDocument({name,parent,documentId})`；
  既有 `POST /api/projects` 复用 create，并从 `defaultParent()` 注入绑定目录或旧默认目录。
- `POST /api/bookshelf/projects/import` 使用独立文档缓存 ID 创建标准项目；所有创建函数
  仅清理本次创建目录，失败不改已有项目。不复用当前 doc.id 作为替换目标。
- preload 增加 `chooseBookshelfDirectory()`，主进程只允许受信窗口调用并返回选中目录或 null。
- 原生设置增加受控 `choose-bookshelf-directory` 动作及回调，返回刷新后的设置状态；
  不开放任意命令或改变原有 AI 提示词保存协议。

- [x] 写定向路由测试：新建/导入默认落在绑定目录、重名不覆盖、失败不留半项目、取消
  不改绑定、换目录不搬移旧书、阅读器左侧导入仍保留 ID/笔记、书架导入创建新 ID。
- [x] 写雨课堂模拟首张/后续页测试：绑定目录默认可用、首张前无占位书、建成自动入架、
  已打开旧书的文稿与笔记不变、显式更换目标目录有效；不进入真实课堂。
- [x] 运行 `node tests/bookshelf-projects.mjs` 确认契约缺失后，实现项目存储与前端创建/导入。
- [x] 实现原生“书架”菜单、读者返回按钮、网页设置和 Mac 通用设置的目录路径/更改按钮；
  新建及雨课堂面板展示默认目标目录并可显式修改，避免每次强制弹出选择器。
- [x] 重跑新测试及 `npm run test:yuketang`、`node tests/menus.mjs`、`node tests/native-settings.mjs`；
  在 UI 流程验证成功创建后返回书架自动出现新书。
- [ ] 审核并提交本任务拥有的差异。

## Task 5: 本地交付与证据

**Files:** `package.json` / lock、`CHANGELOG.md`、`KNOWN_ISSUES.md`、本目录需求/设计/任务状态，
新建 `docs/spec/bookshelf-desktop/validation.md`。

- [x] 添加 `test:bookshelf` 聚合上述定向脚本；按开发版本规则处理版本号，不复用已发布
  二进制版本，不覆盖已有 dev.6 改动或修改公开 README 下载链接。
- [x] 运行 `npm run test:bookshelf`、`npm run format:check`、`npm run test:version` 和
  `git diff --check`；只记录实际执行结果，不将旧结果当作当前验证。
- [x] 正常保存退出用户正在运行的 Mac 应用，然后运行 `npm run package:mac:local`。
  核对 asar、dist 及原生 settings helper 的构建时间晚于各自运行时输入。
- [x] 从固定 `release/mac-arm64/pagenote.app` 入口重开，使用隔离测试库完成绑定、PDF/MD
  选书、新建、封面修改、返回保存和目录设置入口验证；保留用户原项目，记录操作。
- [x] 记录共享逻辑通过范围、截图路径及 Windows 尚未运行的限制；不将交叉构建当作
  Windows 实测，不要求额外设备矩阵或真实雨课堂考勤测试。
- [x] 更新 Unreleased 和 SPEC 状态；若执行提交/push，仅纳入已核对的任务相关差异并
  包含对应 CHANGELOG，公共发布另行处理。

## 自检记录

- R1 对应任务 1、4；R2 对应任务 3、4；R3 对应任务 2、3；R4 对应任务 4；R5 对应任务 5。
- 已显式覆盖五项 Review Focus；所有新增接口名称在生产步骤与验证步骤中保持一致。
- 实现阶段暂缓提交；2026-10-23 用户授权定向复验后，将已核对的 Android / 雨课堂及书架改动
  一并提交到现有功能 PR，不直接合并 main。推送验证记录见 validation.md。
- 原计划中组合描述的扩展测试未全部执行者保留未勾选；不影响已确认的精简定向验收，具体边界见验证记录。
