# Markdown S 实施指导

2026-10-30 原生要求优先：按 [原生实现与同文件保存](NATIVE-MARKDOWN.zh-CN.md)构建可信 PageNote 渲染器。组件数据围栏是事实源，原生 DOM／SVG／Canvas／WebGL 是派生视图；交互走宿主状态更新和既有文件保存接口。禁止用整页 HTML／iframe 替代这一主路径。以下历史沙盒步骤仅适用于兼容模式。

本文件描述具体怎么做；“计划”“原型”“验证通过”“已发布”必须分别记录。配合[架构](ARCHITECTURE.zh-CN.md)和[验收](ACCEPTANCE.zh-CN.md)使用。

## 1. 技术调研与选型

HTML 现行规范是 [WHATWG HTML Living Standard](https://html.spec.whatwg.org/)。Markdown S 是本项目名称；“HTML S”不是这里采用的标准名称。内嵌 HTML 不等于允许脚本：例如 [Typora](https://support.typora.io/HTML/) 明确不执行普通 HTML 中的脚本，[Obsidian](https://obsidian.md/help/html) 会净化 HTML。不能承诺将组件放进任意 Markdown 编辑器都能运行。

下表覆盖本项目主要技术路线，不宣称穷尽 Web 技术。浏览器版本号不能代替实际设备能力检测；Electron、Android WebView、驱动、权限及 GPU 状态都影响结果。

| 技术 | 作用与可做效果 | PageNote 接入方式 | 计划与限制 |
| --- | --- | --- | --- |
| HTML / CSS | 排版、按钮、渐变、卡片、响应式布局 | `mds-html` 隔离页面 | 首期；不能污染正文样式 |
| CSS transition / animation / 3D transform | 翻转、呼吸、轨道、文字和状态动画 | 同上，局部 CSS | 首期；CSS 3D 不等于真实 3D 引擎 |
| SVG / SMIL / 滤镜 | 描边、路径、形变、矢量示意、局部滤镜 | `mds-svg` | 首期；复杂滤镜仍需性能测试 |
| Canvas 2D | 粒子、函数、算法步骤、像素绘图 | `mds-canvas` | 首期；不简单归类为“只能 CPU”，实现可有加速 |
| Web Animations API | 暂停、倒放、时间线与参数交互 | `mds-html` 内调用 `animate()` | 原生能力检测；不保证所有属性都由 GPU 合成 |
| CSS 滚动驱动动画 | 滚动进度、视差、卷轴讲解 | 独立预览内部滚动 | 渐进增强；首期不读取宿主笔记滚动状态 |
| View Transition API | 状态切换、卡片展开、页面过渡 | 演示内部状态变化 | 检测 API；不替换整个 PageNote 导航 |
| Web Components / Shadow DOM | 可复用交互标签 | 在组件沙盒内定义 | 浏览器机制，不单独视作绘图引擎 |
| WebGL / GLSL | 自定义着色器、空间图形、流体视觉效果 | 隔离 Canvas | 依赖 GPU；着色器耗时、上下文丢失必须处理 |
| Three.js | 模型、灯光、材质、粒子、后期效果 | `mds-three` 注入固定版本库 | 首期基础场景；模型加载与后期插件后续独立接入 |
| WebGPU / WGSL | 计算着色器、大规模模拟、现代 GPU 管线 | 后续适配器 | 实验阶段；安全上下文、适配器和设备能力检查，不保证总比 WebGL 快 |
| CSS Houdini / Worklet | 自定义绘制等高级扩展 | 后续受控能力 | Paint/Layout/Animation 并非同等成熟；默认 CSP 不保证可加载 worklet |
| GSAP / Anime.js | 复杂动画时间线、缓动、联动 | 后续固定版本本地适配器 | 第三方库，非浏览器标准；审核许可证与包大小 |
| PixiJS | 高性能二维场景和精灵 | 后续独立引擎 | 与 Canvas/Three 按用例取舍，避免为列表堆依赖 |
| ECharts / Vega-Lite 等 | 复杂统计图、交互筛选 | 后续声明式数据适配器 | 首期内置轻量 SVG 柱／折线；禁用字符串函数等执行入口 |
| Lottie / Rive | 设计工具导出的动画与状态机 | 后续资源型适配器 | 需运行库、资源格式和离线打包评估 |
| KaTeX / Mermaid / 代码高亮 | 公式、流程、语法阅读 | 保持现有通道 | 现有能力，不因新引擎重复实现 |

已查阅的主要官方资料：[滚动动画](https://developer.mozilla.org/en-US/docs/Web/CSS/Guides/Scroll-driven_animations)、[WebGPU](https://developer.mozilla.org/en-US/docs/Web/API/WebGPU_API)、[Three.js WebGLRenderer](https://threejs.org/docs/pages/WebGLRenderer)、[Three.js WebGPURenderer](https://threejs.org/docs/pages/WebGPURenderer.html)、[GSAP Timeline](https://gsap.com/docs/v3/GSAP/Timeline/)、[PixiJS 渲染器](https://pixijs.download/dev/docs/rendering.html)、[Electron 导航事件](https://www.electronjs.org/docs/latest/api/web-contents#event-will-frame-navigate)。核对日期：2026-10-30。后续引擎实施前继续核对其官方 API 与许可证。

## 2. 代码落点与拆分方案

下表中的现有路径是真实入口；推荐子目录是后续拆分目标，不代表已经创建。

| 路径 | 实施内容 |
| --- | --- |
| `src/markdown.js` | 保留原解析、净化、公式、Mermaid 与附件逻辑；只调度已识别的增强块 |
| `src/markdown-s.js` | 当前原型入口；逐步缩为统一导出，避免继续堆积全部实现 |
| `src/markdown-s/registry.js`（规划） | 引擎注册、别名、能力声明、输入限制 |
| `src/markdown-s/runtime.js`（规划） | 状态机、并发控制、取消、停止、节点销毁 |
| `src/markdown-s/sandbox.js`（规划） | CSP、srcdoc、消息校验、权限与资源策略 |
| `src/markdown-s/engines/`（规划） | HTML/SVG/Canvas、Three、图表适配器；共用逻辑不重复复制 |
| `src/markdown-s.css` | 宿主卡片样式；适配窄窗、主题、缩放和键盘焦点 |
| `src/editing.js` | 序列化回围栏，防止工具栏、错误信息和 iframe 替代源码 |
| `src/rich-editor.js` | 编辑组件源码、保存／取消、撤销／重做、复制粘贴 |
| `scripts/build-markdown-s.mjs` | 用固定依赖生成离线运行库；开发和生产构建都必须调用 |
| `scripts/platform-build.mjs` / `package.json` | 所有平台构建入口统一执行生成步骤，干净克隆可构建 |
| `scripts/notices.mjs` | 新依赖许可证与实际分发内容对应 |
| `desktop/main.cjs` | 导航拦截和宿主权限约束；不扩大组件系统访问权 |
| `android/` | 先增加共享 fixture 的保真测试，再实现受限执行适配 |
| `tests/markdown-s*.mjs`（规划） | 解析、真实运行、编辑保存、隔离与回归 |
| `docs/<platform>/guide/` | 实际教程及相邻截图，注明能力与不可用项 |

不要为符合目录图一次搬迁既有桌面／Android 工程；先有接口和测试，再拆文件。

## 3. 分阶段任务与完成条件

### M0：规范与证据基线（本次文档工作）

- 固定首期语法、功能边界、模块职责、阶段与验收编号。
- 记录旧笔记、项目读写、编辑器、公式和 Mermaid 的现有回归入口。
- 记录已有原型，明确它没有经过 Markdown S 专项验收。
- 输出：本组四份文档、开发入口链接、CHANGELOG 记录。文档完成不代表运行功能完成。

### M1：可靠识别和不丢内容

1. 通过 Marked token 识别围栏，先保留原始源码；验证 `normalizeMath` 不改写围栏内容。
2. 建立引擎注册表，未知引擎回退为代码。
3. 实现卡片与显式源码编辑入口，不执行脚本。
4. 在 `htmlToMarkdown` 前还原增强围栏，覆盖复制、富文本编辑、切换和重载。
5. 建立包含 HTML、JS 模板字符串、数学符号、Unicode、长围栏的 fixture。
6. 输出：F01–F05、E01–E04 通过；普通笔记回归通过。尚未实现运行也可单独提交。

### M2：离线隔离预览

1. 根据架构配置独立 iframe；加载前校验源码大小与类型。
2. 实现运行／停止／重跑、错误提示、异步过期保护和最大运行数量。
3. 对 HTML、SVG、Canvas 和 JSON 图表分别验证正常输入、错误输入和清理。
4. Electron 导航限制覆盖自导航及更改 frame name 的情形；检查所有允许显示增强块的宿主窗口。
5. 编写针对父 DOM、宿主 API、网络、导航、弹窗、嵌套 frame 的测试；不得通过关闭 sandbox 让测试“通过”。
6. 输出：对应 R、S、L 用例证据；每个增强块的源码始终可找回。

### M3：Three.js 与能力降级

1. package 与 lockfile 固定版本；打包成本地资源，不运行时访问 CDN。
2. 构建生成物纳入干净构建流程，生成物不随意手改。补齐许可证、源代码包和生产包包含规则。
3. 提供一个可复制的旋转几何体示例：相机、灯光、材质、动画、用户停止入口齐全，尊重减少动态效果偏好。
4. 检测 WebGL 创建失败、上下文丢失；显示原因而不是卡在“启动中”。
5. 区分 Three 核心、额外插件、模型资源；不能宣称装了核心就包含所有 demo 和加载器。
6. 输出：离线构建与启动、真实 WebGL 场景、禁用 GPU 降级、停止重跑和资源回收证据。

### M4：平台交付与体验验收

1. Windows、macOS 分别运行受影响测试，采集真实运行截图；不能用 Windows 截图代表 Mac。
2. Android 验证读取与保存含增强块的笔记；未接入执行前标注“源码展示”。
3. 完成简洁性检查、窄窗、缩放、主题与键盘交互。
4. 补平台教程、功能覆盖表、已知问题和版本说明。单个平台通过可按现有政策独立发布，不等待全部规划平台。
5. 输出：平台验收记录、PR、受影响平台的发布候选制品；稳定晋级仍需明确批准。

### M5：后续扩展（逐项立项）

本地模型与纹理资源、WebGPU、其他动画／图表引擎、静态快照、独立 HTML 导出、Android 可执行预览。每项重复“能力声明 → 实现 → 权限／生命周期 → 测试 → 教程”的流程，不能只加入语言名就标为支持。

## 4. 接入一个新引擎的操作清单

1. 写出它解决的学习需求、现有引擎为何不足以及三个具体示例。
2. 定义围栏名、输入格式、示例、数据上限、错误提示和不支持时的表现。
3. 明确原生 API 或第三方库；固定版本、许可证、压缩包体积、离线资源和供应来源。
4. 实现 `validate/capabilities/prepare/mount/dispose` 对应责任；异步初始化支持取消。
5. 接入统一卡片和源码保存，不复制一套独立编辑器。
6. 不因方便加载而开放整个宿主网络或本地文件；额外权限单独审查。
7. 补合法／非法／极端输入、降级、停止、重开、保存与平台测试。
8. 更新能力表、平台教程、CHANGELOG，提交真实截图和已知限制。

## 5. 回归、集成与回退

现有可用命令包括 `npm run build`、`npm test`、`npm run test:bookshelf`、`npm run test:yuketang`、`npm run test:platforms`、`npm run format:check`。按实际影响选择；渲染／保存链路变化需覆盖公式、代码、富文本、连续阅读、项目重开。新专项脚本完成前，不能把计划中的 `npm run test:markdown-s` 当作已可执行验收。

开发从实际有效的 beta 分支开始；平台路由未完成时仍按根 beta 流程。共享格式变化须给出 Android 和桌面的影响说明。PR 只合入经过验证的阶段；源码合并不代表公开制品发布。

回退开关关闭增强执行后，围栏应按代码显示，保存不受影响；不得把增强源码批量删除来恢复旧 UI。发布前在项目副本上验证旧版打开、保存、再次回到新版。若旧版会丢失内容，要明确阻止该写入流程或提供只读提示，并记录具体版本。

## 6. 原型差距清单

当前工作区原型仍需审查并补齐：UTF-8 字节限制、统一引擎接口、完整的富文本源码编辑／撤销、停止与可见性清理、运行数量限制、GPU 降级、字体／主题、依赖许可证、生产包包含验证、全部宿主的导航限制、真实对抗测试及 Android 保真测试。

上述清单是实施任务，不是已发现全部存在缺陷的结论。不得引用之前的大合并回归结果证明这些新增原型通过。
