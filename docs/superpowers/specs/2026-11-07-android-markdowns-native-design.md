# Android 原生 Markdown S 组件设计

## 意图

让 Android 阅读器能够在笔记正文中原生显示并交互编辑当前已定义的 `markdowns` 声明式组件，交互状态写回同一 Markdown 笔记草稿，并沿用现有逐页保存流程。

## 目标

- Android 能识别当前格式 `version: 1` 的 `markdowns` 围栏，并按现有桌面原生组件模型呈现首批遗传算法教学实验。
- SVG、Canvas 2D、图表及可选 WebGL 视图由 PageNote 组件直接绘制在 Android 笔记视图层级；不通过 iframe、WebView 整页或嵌入式 HTML 执行。
- 支持的用户操作更新声明式状态，并精确替换当前页对应围栏；其他 Markdown 和其他页面保持不变。更新进入 Android 当前笔记草稿，由既有保存操作持久化。
- 未知类型、格式版本、非法数据、重复 ID 与冲突状态均保留源码并给出可读降级提示。
- 对每个实际实现的模块提供 Android 真实运行截图、交互证据和设备/源码来源。

## 非目标

- 在 Android 执行 `mds-html`、`mds-svg`、`mds-canvas`、`mds-three` 等任意 HTML/JavaScript 围栏。
- 宣称与桌面视觉或功能完全等同；不在本设计中增加新组件类型或改变共享文件协议。
- 将 Mermaid、普通 HTML 标签、富 HTML 批注误认为 Markdown S 执行能力。

## 约束与现状

- Android 阅读渲染基于 Markwon，入口为 `android/app/src/main/java/io/pagenote/android/NoteMarkdownRenderer.kt`，阅读和编辑接线在 `MainActivity.kt`。现有笔记变更先进入 `noteDraft`，再通过 `saveCurrentNote` 与 `PageNoteEditor.replacePage` 保存；本设计不增加独立自动保存路径。
- 笔记按逐页内容维护，`PageNoteEditor.replacePage` 与 `PageNotes` 是现有页面范围和保存基础；修改必须保持围栏外源码与其他页不变。
- 桌面权威格式与数据规则见 `docs/markdown-s/NATIVE-MARKDOWN.zh-CN.md`。Android 兼容测试目前仅验证六种旧式可执行围栏源码保真。
- UI 使用 Android 原生视图/Compose。Canvas/SVG 可用 Android 原生绘制；Three.js WebGL 不视为必需能力，若不适配必须显示明确降级。实现范围须依据设备实测确定。

## 方案

推荐将桌面 `markdowns` v1 格式解析为受校验的 Android 模型，再由原生组件视图按模块绘制。把交互回调转换为带组件 ID 和源码版本的状态更新请求，交由当前页面编辑控制器验证并调用既有逐页替换/自动保存。保留 Markwon 处理普通 Markdown 与既有内容，使用有界的自定义节点/span 或相邻 Compose/Android View 宿主承载组件，不接管整篇 Markdown。

备选方案是用 WebView 执行任意 HTML/JS 围栏。该方案不符合项目原生渲染要求，安全和数据生命周期也偏离现有格式，因此不采用。

## 尚由实施者决定

在代码勘察与设备能力确认后，选择 Markwon 自定义 span、Markdown token 预处理加原生嵌入视图，或现有 Compose 阅读层中的最小接入点；组件算法与视觉尽量复用共享格式约定，但不得移植桌面 DOM/WebGL 代码作为 Android 运行时。

## 验收边界

- 解析与降级：合法数据可解析；未知版本/类型、非法字段、重复 ID、超长或无法定位源码时不崩溃、不写错块，源码仍可读可保存。
- 交互与保存：修改组件状态后当前 Markdown 草稿对应围栏更新；围栏外文本及其他页逐字节/逐字符保持；既有保存操作写回笔记文件，取消或冲突不覆盖新内容；完整关闭重开可恢复已保存状态。
- 生命周期：翻页、离开视图、切项目和销毁时释放绘制资源及监听器；不存在 iframe/WebView 执行上下文。
- 回归：普通 Markdown、公式、代码、图片、表格、Mermaid、编辑和逐页保存不退化。
- 证据：每个广告支持的 SVG/Canvas/图表等模块单独实机截图及交互结果；注明设备、Android/WebView 版本（若涉及）、窗口/密度和源码 SHA。未实测能力写未验证。
