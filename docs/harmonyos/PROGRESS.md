# PageNote 鸿蒙版适配 · 工作进度

更新时间：2026-11-04 16:00（Asia/Shanghai）
分支：`harmonyos/beta`（本地，未推送远端）

## 当前状态一句话

功能主体完成且**已在 MatePad Pro 13 平板模拟器（API 24）完成首轮运行验证**：书架→新建→阅读→笔记→搜索→批注主链路实测通过并留存截图；运行验证暴露并修复了三个真实缺陷（PDF Kit 崩溃、bindSheet 失效、批注 @Prop）；PDF 真实渲染受模拟器镜像限制（无 PDF Kit 原生库）走降级分支，待真机验证。

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

## 模拟器运行验证（2026-11-04，MatePad Pro 13 / API 24）

已验证通过：书架、新建项目、Markdown 阅读、笔记编辑/保存/脏标记/预览、历史快照列表（含恢复按钮）、跨页搜索+跳页、批注笔迹（四色/撤销/清除本页）、导入文档选择器弹出与目录导航。截图存 `docs/harmonyos/images/`。

验证中修复的三个真实缺陷（均已复测）：

1. **PDF Kit 静态 import 崩溃**：模拟器镜像缺 `libpdfview.z.so`，ReaderPage 顶部静态 import 导致任何项目打开即 SyntaxError 崩溃。修复：PDF 面板抽为 `components/PdfPane.ets`，ReaderPage 经 `canIUse('SystemCapability.OfficeService.PDFService.Core')` 检测 + 动态 import（WrappedBuilder 在 PdfPane 模块作用域包好导出，规避编译器 10905109；类型经 import type 桥接，规避 arkts-no-structural-typing），失败走降级文案。
2. **多 bindSheet 同节点全失效**：4 个 sheet 挂同一根节点导致全部无法弹出（书架单 sheet 正常）。修复：每个 sheet 挂不同常驻节点。
3. **InkCanvas 批注永不生效**：`penColor`/`drawingEnabled` 是普通字段，运行期父组件传值不更新。修复：改 @Prop。

未完成的验证：PDF 导入全链路（picker 只显示媒体库已索引文件，hdc 推送的 PDF 未索引，选文件一步受阻——环境限制非应用缺陷）；PdfView 真实渲染（需带 PDF Kit 的镜像/真机）；AI 真实请求；公式/图片预览的设备实测（代码路径与搜索同页，编译已过）。

## 雨课堂历史导入（2026-11-04，本批待提交）

对齐 Android feature-yuketang / RainProjectWriter 契约的鸿蒙实现：

- `core/RainProtocol.ets`：协议类型与解析（历史 type=3/14 语义、可见页过滤、LESSON_ON、当前课堂）
- `core/RainClient.ets`：应用内二维码登录（wss requestlogin → ticket 域名校验 → QR 图片 → UserID/Auth → web_login → basic-info），会话存应用偏好（不落项目目录；注意 Android 为 Keystore 加密，鸿蒙端当前与 AI 密钥同级明文偏好，差异已记录）；课程/历史课堂列表、课后报告课件下载
- `core/PdfBuilder.ets`：最小图片型 PDF 生成器（鸿蒙无 PDF 创建 API；JPEG DCTDecode 直接嵌入，页尺寸 ≤960×720）
- `core/RainProjectWriter.ets`：图片解码重编码（JPEG q88）→ PDF + 分页空笔记 + 来源清单 + manifest，失败整目录回滚
- 书架「雨课堂」面板：登录/二维码/课程列表/课堂列表/导入进度
- 模拟器实测：登录 WebSocket、ticket、QR 下载与渲染通过（截图 emulator-08）；修复响应头大小写与 image/jpg 归一问题
- 未验证：微信扫码后的完整登录、真实课程列表与导入（需用户账号）；实时课堂采集未实现（R4，下一阶段）

## 下一步（按序）

1. 雨课堂：用户扫码完成一次真实登录+历史导入验收；随后做实时课堂采集（checkin + WS timeline）
2. 行内公式 `$…$` 与图片/公式预览的设备实测
3. 真机或带 PDF Kit 镜像验证 PdfView 与 PDF 项目全链路
4. 其余 Android 模块按优先级继续（选区/Tag、OCR、闪卡等）

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
