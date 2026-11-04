# PageNote 鸿蒙版（HarmonyOS）

PageNote 的鸿蒙版是 **ArkTS／ArkUI 原生应用**（Stage 模型，平板优先），围绕平板触控与系统文件授权设计。它不是 Windows／Mac 桌面程序的移植界面，功能与操作以本页为准。[返回项目总览](../../README.md)。

## 下载与版本

- 尚无公开下载；开发版本 **0.1.0-dev.1**，见 [独立版本配置](../../platforms/harmonyos/version.json)。
- 目标设备：**HarmonyOS 6 平板（兼容 API 20 起，编译 SDK 6.1.1 / API 24）**；deviceTypes 含 tablet、2in1。
- 尚无获批稳定版与测试版发布；当前产物为未签名 debug HAP，仅供开发机验证。

## 当前已实现（0.1.0-dev.1）

| 模块 | 说明 | 限制 |
| --- | --- | --- |
| 书架 | 列出应用沙箱项目库，新建项目，重名报错不覆盖 | 项目仅存在应用沙箱 `filesDir/projects/` 下 |
| 导入文档 | 经系统文件选择器导入 PDF／Markdown，自动建项目并复制进项目 `src/` | 复制进沙箱后与原文件无关 |
| 阅读 | PDF 走 PDF Kit `PdfView`；Markdown 原文直接显示 | 无文本选择复制 |
| 手写批注 | 笔迹层覆盖源文档，四色、撤销、清除本页，翻页随页加载；1000 比例坐标系与桌面互通，存 `src/annotations.json` | 仅笔迹（pen），无橡皮/图形 |
| 逐页笔记 | Markdown 编辑，`<!-- pagebreak -->` 分页，翻页加载，保存做 revision 校验 | — |
| 笔记搜索 | 跨页全文检索笔记（Markdown 项目连同原文），结果点击跳页 | 每页取首个命中做摘要，不支持正则 |
| 笔记预览 | 基础 Markdown 渲染（标题/列表/引用/代码块/粗斜体/行内代码）、图片附件（`src/assets` 沙箱路径）、KaTeX 公式（$$ 与 ```math 块级 + $…$ 行内，本地 rawfile，不依赖网络） | 外部 http(s) 图片占位提示；行内图片以 `[图：alt]` 占位；mermaid 不渲染 |
| 笔记历史 | 保存时自动生成快照（`src/history/`），可按页查看与恢复，恢复带 revision 校验 | — |
| AI 讲解 | OpenAI 兼容接口按页讲解；BaseURL/Key/模型可配，密钥只存本机应用偏好 | 缺 Key 时提示配置；不内置模型、Key 不硬编码 |
| 雨课堂 | 应用内二维码登录（官方 WebSocket 流程，凭据只存本机应用偏好）、课程/历史课堂列表、历史课件导入为标准项目（图片型 PDF + 分页笔记 + `yuketang-source.json` 来源清单，契约与 Android 逐条对齐）、当前课堂实时逐页采集（checkin + WebSocket timeline 回放、断线重连、按内容哈希去重、追加重建 PDF 并重映射笔记/批注页码）；PDF Kit 缺失的设备用来源图片降级显示课件页 | 实时采集尚未在真实课堂验证；服务器已删除的页面会使整次导入失败并回滚（与 Android 一致） |

项目格式与桌面／Android 完全一致：`project.pagenote` + `src/left.*` + `src/right.md` + `src/assets/`，manifest 校验、分页规则、revision 语义逐条对齐共享契约（`android/docs/spec/contracts/`），核心层在 `harmonyos/entry/src/main/ets/core/`。

**不具备桌面功能对等性。** Office 转换、OCR、Markdown S 交互与 mermaid 渲染均未实现。

## 验证状态

- 已通过：`hvigor assembleHap` 完整构建（DevEco Studio 26.0.0.851，API 26 SDK），产物 `entry/build/default/outputs/default/entry-default-unsigned.hap`。
- 已在 **MatePad Pro 13 平板模拟器（HarmonyOS 6.1.1 / API 24）** 实际运行验证（2026-11-04，截图见 `images/`）：书架、新建项目、Markdown 原文阅读、笔记编辑/保存/脏标记、历史快照列表、跨页搜索与跳页、批注笔迹（四色/撤销/清除）、导入文档选择器弹出与目录导航。
- 运行验证中修复的三个真实缺陷：PDF Kit 静态 import 在缺失原生库的设备上导致整页崩溃（改为 canIUse 检测 + 动态 import 降级）；同一节点挂多个 bindSheet 全部失效（改为每个 sheet 挂不同节点）；InkCanvas 的运行期参数未用 @Prop 导致批注永远不生效。
- 雨课堂：模拟器上实测登录 WebSocket 连接、ticket 获取、二维码图片下载与渲染全链路（截图 `images/emulator-08-yuketang-qrcode.jpeg`）；用户完成真实扫码登录后，已实测课程列表、课堂列表与一次 134 页历史课件的完整导入（生成 PDF 经 PyMuPDF 渲染校验逐页正常；缺页服务器 404 时整次导入失败回滚，与 Android 语义一致），PDF Kit 缺失的模拟器上以来源图片降级阅读并翻页验证（截图 `emulator-09/10`）；实时课堂采集未经真实课堂验证。
- 已知边界：本模拟器镜像不含 PDF Kit 原生库（`libpdfview.z.so` 缺失，syscap 无 `OfficeService.PDFService.Core`），PDF 项目在此镜像上自动降级为来源图片显示（雨课堂项目）或提示（普通 PDF），PdfView 真实渲染需真机或带 PDF Kit 的镜像验证；文件选择器只能看到媒体库已索引的文件，hdc 推送的 PDF 未被索引，导入→选中→打开全链路未完成；AI 讲解未做真实请求。

## 开发入口

- 分支：`harmonyos/beta`（本地测试线，尚未推送远端）。
- 构建：先运行一次 `harmonyos/setup-links.ps1`（把 DevEco 自带的 hvigor 链接进项目，离线可构建），再执行 `harmonyos/build.cmd`。需要本机安装 DevEco Studio 6.1（默认路径 `D:\Program Files\DevEco Studio`）。
- 签名与安装：未配置签名。要装到模拟器／真机，在 DevEco Studio 中打开 `harmonyos/` 目录，用 File → Project Structure 自动生成调试签名。
- 更完整的本机环境说明见项目局部技能 `.kimi/skills/harmonyos-dev/SKILL.md`。
