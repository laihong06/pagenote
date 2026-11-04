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
| 笔记预览 | 基础 Markdown 渲染（标题/列表/引用/代码块/粗斜体/行内代码）、图片附件（`src/assets` 沙箱路径）、KaTeX 公式（$$ 与 ```math，本地 rawfile，不依赖网络） | 外部 http(s) 图片占位提示；行内 $…$ 与 mermaid 不渲染 |
| 笔记历史 | 保存时自动生成快照（`src/history/`），可按页查看与恢复，恢复带 revision 校验 | — |
| AI 讲解 | OpenAI 兼容接口按页讲解；BaseURL/Key/模型可配，密钥只存本机应用偏好 | 缺 Key 时提示配置；不内置模型、Key 不硬编码 |

项目格式与桌面／Android 完全一致：`project.pagenote` + `src/left.*` + `src/right.md` + `src/assets/`，manifest 校验、分页规则、revision 语义逐条对齐共享契约（`android/docs/spec/contracts/`），核心层在 `harmonyos/entry/src/main/ets/core/`。

**不具备桌面功能对等性。** Office 转换、OCR、Markdown S 交互与 mermaid 渲染均未实现。

## 验证状态

- 已通过：`hvigor assembleHap` 完整构建（DevEco Studio 26.0.0.851，API 26 SDK），产物 `entry/build/default/outputs/default/entry-default-unsigned.hap`。
- 未验证：模拟器／真机运行、PDF Kit 实际渲染、文件选择器与 AI 调用的真机行为。截图待补，不用其他平台截图替代。

## 开发入口

- 分支：`harmonyos/beta`（本地测试线，尚未推送远端）。
- 构建：先运行一次 `harmonyos/setup-links.ps1`（把 DevEco 自带的 hvigor 链接进项目，离线可构建），再执行 `harmonyos/build.cmd`。需要本机安装 DevEco Studio 6.1（默认路径 `D:\Program Files\DevEco Studio`）。
- 签名与安装：未配置签名。要装到模拟器／真机，在 DevEco Studio 中打开 `harmonyos/` 目录，用 File → Project Structure 自动生成调试签名。
- 更完整的本机环境说明见项目局部技能 `.kimi/skills/harmonyos-dev/SKILL.md`。
