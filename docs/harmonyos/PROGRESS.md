# PageNote 鸿蒙版适配 · 工作进度

更新时间：2026-11-04 14:21（Asia/Shanghai）
分支：`harmonyos/beta`（本地，未推送远端）

## 当前状态一句话

鸿蒙原生应用骨架与核心阅读链路已完成并**编译通过**（未签名 debug HAP），已提交一次；手写批注功能已完成编译通过但**尚未提交**；笔记搜索刚开始接线（只加了状态字段，无功能逻辑）；公式渲染未开始。

## 已完成（已提交，commit 2721b1a）

- 工程：`harmonyos/` ArkTS/ArkUI Stage 工程（平板优先，兼容 API 20+，编译 API 26）
- 核心层 `entry/src/main/ets/core/`（与 Android core-project 逐条对齐）：
  - `PageNotes.ets`：`<!-- pagebreak -->` 分页切分/合并、SHA-256 revision
  - `ProjectManifest.ets`：manifest 解码/编码/校验（src/ 内相对链接、64 位 hex id）
  - `ProjectStore.ets`：沙箱项目库、原子写、revision 校验保存
  - `NoteHistory.ets`：历史快照（src/history/）
  - `Agent.ets`：OpenAI 兼容客户端 + 模型配置（密钥存应用偏好，不落项目目录）
- 界面：书架（新建/导入 PDF、MD）、阅读页（PdfView 阅读、逐页笔记编辑、Markdown 基础预览、历史恢复、AI 按页讲解）
- 基础设施：`build.cmd` / `setup-links.ps1` 离线构建；`.kimi/skills/harmonyos-dev/SKILL.md` 项目局部技能；`docs/harmonyos/README.md`；`platforms/harmonyos/version.json`（0.1.0-dev.1，未发布）；CHANGELOG Unreleased 已记

## 已完成但未提交（工作区中）

- 手写批注：`core/Annotations.ets`（annotations.json 契约，document/note 双侧、1000 比例坐标系）+ `components/InkCanvas.ets`（画布笔迹层）+ ReaderPage 接线（批注模式开关、四色、撤销、清除本页、翻页随页加载）
- 编译验证：BUILD SUCCESSFUL（DevEco 26.0.0.851 / API 26 SDK）
- ReaderPage 里已加 `showSearch / searchQuery / searchResults / SearchHit` 状态，**搜索方法、sheet 界面、入口按钮都还没写**

## 下一步（按序）

1. 笔记搜索：ReaderPage 加搜索按钮 + sheet，跨页全文检索 notes.md（MD 项目连同原文），结果点击跳页
2. 图片附件渲染：MarkdownView 支持 `![alt](assets/x.png)` 解析到项目 src/assets 沙箱路径
3. 公式渲染：Web 组件 + 本地 katex（需取 katex dist 到 rawfile；不依赖 CDN）
4. 全部完成后：重新编译、更新 docs/harmonyos/README.md 功能表与 CHANGELOG、提交

## 复现构建

```bash
powershell -File harmonyos/setup-links.ps1   # 首次一次
harmonyos/build.cmd                          # 或 Git Bash 手动命令见 SKILL.md
```

产物：`harmonyos/entry/build/default/outputs/default/entry-default-unsigned.hap`（未签名；装模拟器需在 DevEco 自动生成调试签名）。

## 已知边界

- 未做模拟器/真机运行验证；PDF Kit、文件选择器、AI 调用均为编译期确认
- router/showToast 等有 deprecated 警告（功能正常，后续可迁 Navigation API）
- 桌面级功能（OCR、Office 转换、mermaid、Markdown S）未在鸿蒙端计划内承诺
