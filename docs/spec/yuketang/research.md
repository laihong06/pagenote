# 雨课堂全端课件导入调研

日期：2026-10-21（Asia/Shanghai）

范围更新：2026-10-22（Asia/Shanghai），由 Android 扩展为现有三端。

状态：Windows/macOS/Android 的应用内登录与课件导入实现中。Mac arm64 打包版已验证雨课堂面板和二维码图片展示；真实账号确认、课程发现及实时课堂仍待用户账号/课堂验证。早期网页会话可行性试验记录见 [Mac 试验](mac-feasibility-2026-10-22.md)。本文是 Windows、macOS、Android 的公共调研入口。

已明确的产品要求见 [全端接入需求](requirements.md)：登录必须在应用内完成，并使用应用自身会话支持课件获取与实时采集。

## 目标与范围

- 用户确认使用标准雨课堂 `https://www.yuketang.cn`。首个实现只验收该站点，不扩大学校和域名适配矩阵。
- 覆盖现有 Windows、macOS、Android 三端：获取本人账号可访问的课程课件，自动导入标准 `project.pagenote` 项目并接入各端现有 OCR、分页笔记和 AI 讲解。
- Windows 和 macOS 复用 Electron / Node 实现；macOS 按项目约定仅适配和构建 Apple Silicon。Android 继续使用原生 Kotlin，重点适配 Android 14 / 小米平板 5 Pro。
- 统一功能范围、来源数据与同步规则，不要求将 Kotlin 与 JavaScript 强行合并为单一运行时；本次“全端”指已有三端，不新增 iOS、浏览器产品或云服务。
- 历史课件导入与上课实时同步分开设计。前者先形成完整闭环，后者在实际课堂验证 WebSocket、认证和页码事件。
- 产品登录由 pagenote 内置，桌面使用应用自己的登录窗口 / 网页会话，Android 使用应用内登录页面；不依赖外部浏览器登录态、插件或手工 Cookie。此前 Edge 采集只保留为历史试验证据。
- 初始调研使用公开资料、源码和仓库元数据。后续 Mac 试验复用了用户已登录的网页会话，获取一份可见课后课件；实际操作和验证边界见独立记录，未执行签到、作答或实时课堂连接。

## 结论

有可参考的内部接口封装和课件下载实现，无需从屏幕截图自动化起步。但本次没有找到官方公开、稳定承诺的课件下载 SDK，也没有找到同时覆盖现有三端的完整标准雨课堂课件 SDK。

当前插件目录按“雨课堂 / Rain Classroom / yuketang”检索无结果；该目录并非穷尽，仍可通过[插件目录](https://chatgpt.com/plugins)进一步查找。浏览器用户脚本与此类连接器是两种集成方式。

推荐参考 `thuhollow2/yuketang` 的认证、课件获取和课堂事件协议，定义公共数据契约，再分别接入桌面公共模块和 Android 原生适配器；用 `ZaytsevZY/yuketang-helper-auto` 交叉核对标准站点和课后报告结构。不能将源码证据等同于当前账号上的接口验收。

## 跨端组织与契约

公共调研与后续公共 requirements / design / tasks 放在 `docs/spec/yuketang/`；Android 平台约束与验收留在 [Android 补充文档](../../../android/docs/spec/yuketang-integration-research.md)，避免维护三份相互偏离的接口说明。本文尚不是已批准的实现设计或已完成任务清单。

| 层次 | Windows / macOS | Android | 需要保持一致的内容 |
| --- | --- | --- | --- |
| 协议与归一化 | 共用 Electron / Node 模块 | Kotlin 适配器 | 请求含义、字段映射、错误分类、脱敏响应样本 |
| 登录与任务执行 | 应用内 Electron 官方登录窗口、独立会话、本地任务与平台安全存储 | 应用内官方登录页面、原生任务与 Keystore | 登录失效、取消、退出、重试、进度与进入课堂的副作用 |
| 项目导入 | 复用 `projects.mjs` / `server.mjs` 的现有入口 | 复用 `ProjectCreator` | 标准项目布局、来源标识、失败回滚、保留用户编辑 |
| OCR 与 AI | 桌面既有页面准备与模型连接 | Android 既有离线 OCR 与 Agent | 页码与来源对应、缓存更新语义、失败状态 |

- 各类远端 ID 在公共契约中保存为十进制字符串，避免 JavaScript 精度损失，也避免依赖各端整数解析差异。
- 来源清单统一记录站点、课程 / 课堂 / 课件 / 页面 ID、页序、原始页面内容散列及来源版本。公共契约和脱敏样本在同一目录维护，桌面与 JVM 都验证这些样本；不是只靠两套实现各自通过自己的测试。
- 内容变化以原始页面资源和规范化元数据判断，不依赖两端合成 PDF 的字节完全一致。PDF 需呈现同样的页面与顺序。
- 页面增删、重排和换版遵守同一规则；保留已有笔记、批注、笔迹和未保存草稿，不能用重新生成 PDF 覆盖旧项目的方式实现更新。
- 各端独立登录；Cookie、课堂 Token 和模型 Key 不随项目转移。来源文件只保存可移植的非敏感元数据，各端缓存不作为另一端可用的前提。
- “同步”指从雨课堂更新本地课件与可选课堂跟页；项目文件可在三端互开。跨设备实时同步笔记、账号漫游和云端冲突合并尚未包含在用户本次要求中，不自动引入网盘或自建服务。

## 候选项目

以下日期为调研时默认分支最新提交日期，按 Asia/Shanghai 转换，不代表实际使用测试结果。

| 项目 | 已核实内容 | 对 pagenote 的用途与限制 |
| --- | --- | --- |
| [thuhollow2/yuketang](https://github.com/thuhollow2/yuketang/tree/b5da7837dc187c9b7a960623e4d196727637aa3d) | MIT；2026-10-18 有提交；Python 实现扫码登录、课程列表、课件页面获取、图片合成 PDF 和课堂翻页事件；README 列出标准站点 | 主要协议参考。它的 Android 包使用 Chaquopy 执行 Python，不能当作现成原生 Kotlin SDK。入口还会执行签到及其他课堂操作，不能原样接入 |
| [ZaytsevZY/yuketang-helper-auto](https://github.com/ZaytsevZY/yuketang-helper-auto/tree/9224c5c120bde9bf29090fb6b1ed084661b5d3c4) | 2026-10-20 有提交；TypeScript 有标准站点适配器、课件归一化、课后报告采集、会话和 WebSocket 分层 | 适合交叉核对数据结构。部分采集依赖 Electron 浏览器上下文；LICENSE 虽写可按 MIT 使用，又含额外声明和版权归属文字，复用源文件前需核清，调研不引入代码 |
| [hotwords123/yuketang-helper](https://github.com/hotwords123/yuketang-helper/tree/bad15949a165b0d66fb66c3a9eb052d7ecd5fc8b) | 2026-06-18 默认分支提交；Vue / Tampermonkey 用户脚本；利用已登录网页上下文，并支持将课件页面打包成 PDF | 可参考浏览器辅助导出的交互；依赖网页和脚本管理器，不是 Android SDK。调研时根目录树未见独立 LICENSE 文件 |
| [Auto-CQUPT-Plan/RainClassSDK](https://github.com/Auto-CQUPT-Plan/RainClassSDK/tree/438f46c1605e7bf0368da544e999d6910d02fb88) | MIT；2026-06-08 有提交；Go SDK，集中封装登录、课程和作业等接口 | 是接口 SDK 的实例，但常量绑定长江站点；已查导出方法没有完整课堂课件下载链路，不宜作为标准站点主依赖 |
| [PillarsZhang/Rain-Classroom-PDF-Direct-Download](https://github.com/PillarsZhang/Rain-Classroom-PDF-Direct-Download/tree/737c8e5c3b752c838d049f77ce1fa75fb780d9f7) | MIT；默认分支最后提交为 2022 年；用户脚本提取网页幻灯片图片并生成 PDF | 简单 PDF 导出参考；依赖 DOM，维护时间较早，不能当作接口兼容性保证 |

## 已查到的协议线索

以下路径来自开源源码，不是官方接口承诺。Mac 试验仅确认网页采集链路；直接导航课程列表接口未返回预期结构，其余路径未逐项验收。标准站点的适配配置见 [host-adapter.ts](https://github.com/ZaytsevZY/yuketang-helper-auto/blob/9224c5c120bde9bf29090fb6b1ed084661b5d3c4/ykt-helper-win/packages/routing/src/active/host-adapter.ts)。

| 能力 | 源码中的接口或事件 | 需要进一步确认 |
| --- | --- | --- |
| 用户身份校验 | `GET /api/v3/user/basic-info` | 标准站点登录态、失效响应与 Cookie 要求 |
| 课程列表 | `GET /v2/api/web/courses/list?identity=2` | 分页、学期和历史课程过滤 |
| 正在上课 | 标准站点 `GET /api/v3/classroom/on-lesson`；公开逆向实现解析 `data.onLessonClassrooms` 并以 `status == 1` 标记进行中 | 不是官方稳定 API；字段与真实账号响应仍待验证。本实现只请求标准站点该 endpoint，不轮试学校专用路径。参考：[RainClassroomAssitant-standalone README](https://github.com/chlchi/RainClassroomAssitant-standalone) |
| 历史课堂记录线索 | `/v2/api/web/logs/learn/{classroom_id}` | 课件类型、分页、课堂 ID 与课件 ID 的对应关系 |
| 课后报告线索 | `/api/v3/classroom-report/student/lesson-info`、`student/review`，带 `lesson_id` | 标准站点当前结构、可见范围、图片清单，及是否依赖浏览器上下文 |
| 课件页面 | `GET /api/v3/lesson/presentation/fetch?presentation_id=...` | `slides` 页面、图片地址、课件权限与课堂级认证 |
| 扫码登录参考 | `wss://www.yuketang.cn/wsapp/` 的 `requestlogin`，消息的 `ticket` 是短时二维码图片地址；扫码授权后 `POST /pc/web_login` | 图片端点和扫码确认来自公开源码参考，账号确认、过期与取消仍需真实账号验证；不把 ticket 图片端点当作二维码内容重新编码 |
| 课堂实时消息 | `/wsapp/`；`hello`、`slidenav`、`presentationupdated` 等事件 | 心跳、重连、事件页号起点、课件切换与课堂结束 |

认证和实时协议证据见 [yuketang.py](https://github.com/thuhollow2/yuketang/blob/b5da7837dc187c9b7a960623e4d196727637aa3d/yuketang.py)。历史报告的独立读取路径见 [client.ts](https://github.com/ZaytsevZY/yuketang-helper-auto/blob/9224c5c120bde9bf29090fb6b1ed084661b5d3c4/ykt-helper-win/packages/routing/src/active/client.ts)。

二维码协议交叉核对：[thuhollow2/yuketang](https://github.com/thuhollow2/yuketang/blob/main/ykt.py) 将 WebSocket `ticket` 作为图片 URL 下载；雨课堂官方 [Xuetangx-Openplatform 文档仓库](https://github.com/yuketang/Xuetangx-Openplatform) 也把 `ticket` 描述为 `mp.weixin.qq.com/cgi-bin/showqrcode` 图片地址，并另列可扫码 URL。此证据解释了应用内需要显示服务端返回的图片，而不能把 `ticket` URL 本身编码成新的二维码；接口仍属逆向兼容，不代表标准站点对外承诺稳定性。

### 会影响产品设计的实际差异

1. 实时连接可能产生课堂操作：已查 Python 实现先调用 `POST /api/v3/lesson/checkin`，获取 `Set-Auth` / `lessonToken` / `identityId` 后再建立课堂连接。不能把整条链路表述为无副作用的课件读取；实时模式需要清楚呈现进入课堂可能记录签到。
2. 下载不等于获得原始 PPTX：已查 Python 下载器取每页 `cover` 图片，再合成为 PDF，见 [util.py](https://github.com/thuhollow2/yuketang/blob/b5da7837dc187c9b7a960623e4d196727637aa3d/util.py)。这类 PDF 没有原生文字层，需要 OCR。其他适配器还接受多种图片字段，图像清晰度和完整性需实测。
3. 获取范围取决于账号与教师发布权限。缺页、权限拒绝、需要重新登录、字段变化和网络失败必须分别报告，不能将空课件当作成功导入。
4. 课堂中课件可能换版、追加页或换顺序。仅用 PDF 页号去更新文件会错配旧笔记和笔迹，应先按稳定课件 ID / 页面 ID 建立映射，内容改变时保存版本并保留已有笔记。

## 推荐接入方式

### 首选：公共契约与平台适配器

适配器负责会话、课程发现、课件清单、图片下载和事件归一化；项目导入、PDF 阅读、OCR 和 AI 使用各端现有模块。Windows 与 macOS 共享协议实现和业务逻辑；Android 保留 Android 14 / Kotlin 的现有技术路线。

建议将外部协议收敛为 `listCourses`、`listLessons`、`fetchPresentation`、`downloadSlides`、`observeLesson` 等小型接口，避免把雨课堂 JSON 或网络调用散落在阅读器中。实时连接单独暴露进入课堂这一动作。

数据流：登录 -> 选择课程及导入目标目录 -> 选择课件或启用指定课程同步 -> 下载可见页面 -> 校验完整性 -> 合成 PDF -> 创建标准项目 -> 逐页 OCR / 页面准备 -> AI 讲解。

- 桌面复用 `projects.mjs` / `server.mjs` 的现有项目创建与文档导入入口；Android 复用 `ProjectCreator` 的 PDF 暂存、有效性校验、复制、失败回滚与最后写 manifest 的行为。两边都保持 `src/document.pdf`、`src/right.md`、`src/assets/` 的兼容格式。
- 在项目内保存不含凭证的来源清单，记录站点、课程 / 课堂 / 课件 / 页面 ID、页面顺序、内容散列和同步时间。优先使用独立来源文件，不为单一来源改动项目格式版本。
- 登录凭据仅保存在应用本机安全存储；桌面使用系统支持的加密存储，Android 使用 Keystore。凭据不写入项目、导出包、AI 提示词或日志；下载跨站图片时不向资源域名盲目附带站点 Cookie。
- 下载任务支持进度、取消、重试和缓存复用；完整下载及 PDF 校验通过后才发布为可用项目。
- 只重做内容改变页面的 OCR；下载和 OCR 使用受限并发，各端可采用不同并发配置，Android 重点适配小米平板 5 Pro 的内存与离线复用需求。
- 已存在项目按来源标识识别重复导入；更新不得直接覆盖用户笔记、批注和笔迹。课件结构变动时先保留新旧来源版本，页码映射不明确时不自动迁移笔迹。
- 实时跟页可开关；用户自行翻页或书写时避免被老师翻页打断。按已验证的事件更新位置，不通过高频重复下载整个课件同步。
- 桌面新增运行模块必须纳入 Electron 的打包文件清单，在安装后的实际应用入口验证，不能只验证开发服务器。Android 验证实际 APK 中包含全部所需资源。

### 应用内登录与网页兼容路径

应用内登录是必备能力：桌面由 pagenote 自身的 Electron 登录窗口、Android 由应用内官方登录页面承接认证，使用应用独立会话；二维码和结果状态在应用内展示，微信扫码确认遵循官方流程。登录后由同一应用会话访问课程接口和实时连接，不导入外部浏览器状态。

如果标准站点的课件数据需要网页环境才能可靠取得，可使用上述应用自身的受控网页上下文获取已授权清单，再交给各端下载器。该兼容路径仍在应用内运行；外部浏览器采集不作为产品登录或验收路径。阅读器、手写、PDF 和 OCR 使用各端现有模块。

这需要额外验证 Cookie 作用域、页面跳转、JavaScript 桥接范围和 WebSocket 采集机制；不能假设 WebView 的普通请求拦截就能覆盖全部实时消息。

### 不优先：整包嵌入 Python 工具

虽然有现成 Python 工具及其 Android 包，但其运行时、依赖和课堂自动化超出课件导入所需。优先借鉴协议并适配现有 Node / Kotlin 运行架构。

## 实现前需补齐的证据

- 使用用户本人登录的标准站点，验证课程列表、一个历史课堂和一个可见课件。凭据留在本机。
- 对比原网页页数、图片分辨率、内容顺序与导入 PDF，确认缺页与权限边界。
- 单独验证实时课堂认证、进入课堂的副作用和翻页事件，不能用历史课件测试替代实时验证。

## 建议的精简验收

- Node / JVM：共同消费同一组脱敏响应样本，验证字段归一化、超大数字 ID、页序、重复导入、换版和权限 / 登录失效分类；避免将两套桌面平台重复计为两套协议测试。
- Windows 实际应用、macOS arm64 `release/mac-arm64/pagenote.app`、API 34 平板 AVD：各验证一次下载 -> 标准项目 -> PDF -> OCR -> AI 的闭环，以及中断后重试、已有笔记保留。macOS 运行变更按项目要求重新构建打包、正常退出并重新打开固定入口后验证。
- 用同一个脱敏示例项目验证桌面与 Android 互开后的页面、来源标识和笔记对应关系。互开验收不等同于跨设备自动同步。
- 真实标准站点会话：验证登录与课件获取；实时功能只需一次实际课堂验证。其他站点和设备不纳入本次适配矩阵。
- 以上是产品接入的待实现验收。已完成的 Mac 网页采集 / 导入 / OCR 试验单独记录，不代表全端产品验收或内置登录已完成。
