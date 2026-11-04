# PageNote 鸿蒙版适配 · 工作进度

更新时间：2026-11-04 15:00（Asia/Shanghai）
分支：`harmonyos/beta`（本地，未推送远端）

## 当前状态一句话

鸿蒙原生应用骨架、核心阅读链路、手写批注、笔记搜索、图片附件渲染与本地 KaTeX 公式渲染均已完成并**编译通过**（未签名 debug HAP）；上一批（骨架+批注）已提交，本批（搜索/图片/公式）编译验证后随本文档一起提交。

## 已完成（已提交，commit 2721b1a / e9b2e20）

- 工程：`harmonyos/` ArkTS/ArkUI Stage 工程（平板优先，兼容 API 20+，编译 API 26）
- 核心层 `entry/src/main/ets/core/`（与 Android core-project 逐条对齐）：
  - `PageNotes.ets`：`<!-- pagebreak -->` 分页切分/合并、SHA-256 revision
  - `ProjectManifest.ets`：manifest 解码/编码/校验（src/ 内相对链接、64 位 hex id）
  - `ProjectStore.ets`：沙箱项目库、原子写、revision 校验保存
  - `NoteHistory.ets`：历史快照（src/history/）
  - `Agent.ets`：OpenAI 兼容客户端 + 模型配置（密钥存应用偏好，不落项目目录）
  - `Annotations.ets`：annotations.json 契约（document/note 双侧、1000 比例坐标系）
- 界面：书架（新建/导入 PDF、MD）、阅读页（PdfView 阅读、逐页笔记编辑、Markdown 预览、历史恢复、AI 按页讲解、手写批注层 InkCanvas：四色/撤销/清除本页/翻页随页加载）
- 基础设施：`build.cmd` / `setup-links.ps1` 离线构建；`.kimi/skills/harmonyos-dev/SKILL.md` 项目局部技能；`docs/harmonyos/README.md`；`platforms/harmonyos/version.json`（0.1.0-dev.1，未发布）；CHANGELOG Unreleased 已记

## 已完成（本批，待提交）

- 笔记搜索：ReaderPage「搜索」按钮 + sheet，跨页全文检索 notes.md（Markdown 项目连同原文），每页取首个命中做摘要，点击结果跳页
- 图片附件渲染：MarkdownView 支持块级 `![alt](assets/x.png)`，经 `resolveImage` 解析到项目 `src/` 沙箱路径（拒绝 `..` 逃逸与绝对路径）；外部 http(s) 图片占位提示，保持离线；行内图片以 `[图：alt]` 占位
- 公式渲染：块级 `$$ … $$` 与 ` ```math/```latex ` 交给 `components/MathView.ets`（Web 组件加载 rawfile `math.html` + 本地 KaTeX 0.16.22 dist，含字体共 1.5MB，不依赖 CDN）；渲染后按内容高度自适应
- 顺手修复：`ProjectManifest.ets` 中一个字面 NUL 字节改为 `\x00` 转义（内容等价，便于工具链读取）
- 编译验证：BUILD SUCCESSFUL ×2（hvigor 手动命令；`build.cmd` 在 GBK 控制台下因 UTF-8 中文注释解析失败，属已知脚本限制，SKILL.md 手动命令为准）

## 下一步（按序）

1. 模拟器验证：DevEco 自动生成调试签名 → 装 MatePad Pro 13 模拟器，跑书架→导入→阅读→笔记→搜索→批注→公式/图片预览全链路，截图存档
2. 行内公式 `$…$` 与连续滚动阅读（如需）评估
3. 全部完成后：更新 docs/harmonyos/README.md 验证状态与 CHANGELOG、推送 `harmonyos/beta`

## 复现构建

```bash
powershell -File harmonyos/setup-links.ps1   # 首次一次
# GBK 控制台下 build.cmd 中文注释会乱码，建议直接用 Git Bash 手动命令（见 .kimi/skills/harmonyos-dev/SKILL.md 第 2 节）
```

产物：`harmonyos/entry/build/default/outputs/default/entry-default-unsigned.hap`（未签名；装模拟器需在 DevEco 自动生成调试签名）。

## 已知边界

- 未做模拟器/真机运行验证；PDF Kit、文件选择器、AI 调用、批注层、公式与图片渲染均为编译期确认
- router/showToast 等有 deprecated 警告（功能正常，后续可迁 Navigation API）
- 桌面级功能（OCR、Office 转换、mermaid、Markdown S、行内公式）未在鸿蒙端计划内承诺
