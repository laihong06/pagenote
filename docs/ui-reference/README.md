# pagenote HTML 界面样板

直接打开 `index.html`，无需安装依赖、运行后端或编译。此目录是独立的交互设计原型，
不是另一个正式客户端，也不会被 Electron 或 Android 自动加载。

## 范围

- 书架：PDF / Note 封面、四种纸本配色、搜索、排序、网格/列表、新建及封面自定义。
- 阅读器：安卓侧栏与桌面菜单、原文/笔记双栏、拖动分界线、Markdown 编辑和预览、公式。
- 书写工具：左侧象形钢笔/圆珠笔/荧光笔/激光笔/橡皮擦，颜色和粗细预设、自定义颜色、形状与笔迹设置预览。
- 页面预览：点击右上角页码打开右侧缩略图栏，当前页高亮，搜索页码/标题、点选跳页，点栏外或 Esc 关闭。
- 页面与表单：新建项目（可选文档）、雨课堂历史导入/采集确认、通用/阅读/AI 设置。
- 基础规范：颜色、字号、控件尺寸、封面比例和对应生产源码。
- 参考图：本地选择 PNG/JPEG/WebP，并排或透明叠加；截图不上传、不写入浏览器持久存储。
- 截图模式：隐藏样板工具栏，单独呈现应用界面。

所有数据均为示例。笔记、书名、配色和普通设置仅保存在浏览器的
`pagenote-ui-reference-v1` localStorage 中；与实际 pagenote 的配置、登录态和项目无关。
API Key 不保存。雨课堂、AI、OCR、文件夹绑定和 PDF 选取只展示交互，不执行真实服务、
文件系统授权或 PDF 导入。笔工具展示选中状态与笔迹样例，不实现原生手写引擎。
可实际导出当前示例 Markdown。

## 后续维护

1. 收到参考截图后，先放到 `references/`，记录对应页面、平台及改动要求。
2. 在本目录迭代并确认视觉和交互，不同时改 Compose / Electron 生产代码。
3. 通用颜色、尺寸修改 `tokens.css`；布局修改 `reference.css`；页面和交互修改 `reference.js`。
4. 经确认后，再把已确定的设计同步到正式端及相应 SPEC。

这个版本是从当前界面提炼出的基线，不宣称逐像素复刻原生 Material 或 AppKit 控件。
平板和桌面分别沿用现有绿/蓝主色，书架和纸本封面保持共同视觉语言。

## 结构与来源

| 文件 | 用途 |
| --- | --- |
| `index.html` | 可直接打开的入口及参考图工具 |
| `tokens.css` | 可编辑的颜色和尺寸基线 |
| `reference.css` | 样板布局与响应式样式 |
| `reference.js` | 示例数据和交互，完全本地运行 |
| `references/` | 后续截图及逐页设计记录 |
| `assets/lecture-page*.png` | 本样板三页原创讲义的封面/页面缩略图，不含用户课件 |
| `assets/pdf-cover.png` | 仓库 `tests/fixtures/sample.pdf` 第一页的渲染 |
| `assets/vendor/` | 离线浏览所需的既有开源库及许可证 |

基线读取自 `BookshelfScreen.kt`、`ReaderSettingsDialog.kt`、`RainClassroomDialog.kt`、
`RainProjectDialog.kt` 以及桌面 `src/bookshelf.css`、`src/style.css`、`src/theme.css`。
数学排版复用 KaTeX，Markdown 复用 Marked 和 DOMPurify，工具图标复用 Lucide。
未引入新的应用框架、远程 CDN、统计脚本或账号服务。

## 离线依赖

从当前工作区既有依赖复制浏览器构建，保留原文件和许可证，不改写第三方源码：

| 库 | 版本 | 许可证文件 |
| --- | --- | --- |
| Lucide | 1.61.0 | `assets/vendor/LICENSE-lucide.txt` |
| Marked | 16.4.2 | `assets/vendor/LICENSE-marked.txt` |
| DOMPurify | 3.4.17 | `assets/vendor/LICENSE-dompurify.txt` |
| KaTeX | 0.18.7 | `assets/vendor/LICENSE-katex.txt` |

KaTeX 字体仅保留现代浏览器使用的 WOFF2。更新依赖时同步版本和许可证。

## 精简验证

```sh
node tests/ui-reference.mjs
```

验证直接打开 HTML、离线资源、书架/笔记交互、拖动双栏、表单门控及桌面/平板/窄屏布局。
截图保存在忽略的 `release/ui-reference-*.png`；不运行 Android 或整个桌面回归矩阵。
