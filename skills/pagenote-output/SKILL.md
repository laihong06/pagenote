---
name: pagenote-output
description: 笔记的Markdown、公式、Mermaid、代码和图片输出规范。
---
# 输出格式

表达选择统一读 representation，本模块只规定格式、保存和证据。以本次索引实际能力为准，不从旧样例推断所有引擎／平台都可用。

原生 Markdown S 使用标注 markdowns（兼容 mds-native）的 JSON 围栏；这是数据，不是 HTML 或可执行脚本。当前只支持 version:1、kind:"genetic-lab"、当前笔记内唯一的非空 id（最多80字符）及12个0/1组成的 genes。可选 position:0–100、generation:整数0–40、cut:整数1–11、mutationRate:0–0.3、selectedIndividual:整数0–35、seed:0–4294967295整数、paused:布尔、angle／wheelAngle:有限数值。单块最多200KiB UTF-8。完整最小例：

````markdown
```markdowns
{"version":1,"id":"lesson-example-1","kind":"genetic-lab","genes":[0,0,0,0,0,1,0,1,0,0,0,0],"generation":0,"paused":true}
```
````

PageNote 直接渲染原生组件，交互状态经原保存链写回同一 Markdown，不需要另存 HTML。解释和公式留在围栏外；不要把任意 HTML、CSS、JavaScript 或外部脚本塞进数据字段，不编造新 kind。没有适用原生模板时使用可读静态替代或实际计算绘图，不为无关知识强套 genetic-lab。旧 mds-html/svg/canvas/three/chart 仅历史兼容，不是主动生成的默认路径。

生成时遵循格式，不等发布后集中修复。行内公式用$...$；独立公式的两个$$各自单独占行，整个公式块与正文之间留空行。正常写LaTeX反斜杠，由工具处理JSON序列化，不把双重转义存进正文；矩阵行分隔保留合法双反斜杠。

Mermaid用标注mermaid的代码围栏，节点ID简短，中文标签用双引号，不在围栏里混入解释。普通代码围栏注明语言，成对闭合，代码与解释分开。图片必须实际取得，插入可访问的项目附件地址，不引用虚构文件。表格列数一致。

提交前调用page_notes_validate_note做一次本地确定性预检，检查公式、代码围栏、Mermaid、图片及原生组件数据与标识；检查 visualBlocks 中的错误、警告和 runtimeVerified。通过后提交 normalizedMarkdown，使用读取到的 expectedRevision；失败只修对应问题，不能全局替换代码反斜杠或重写无关知识。校验没有运行用户演示，不能将 supported／valid 冒充已运行。保存后按工具返回核对文件与版本，未做重开／真实交互检查则如实说明。

目标是首次提交即可正常展示，不把反复人工截图审查作为每页固定步骤。预检不证明知识正确，也不能保证所有设备一致；原图看不清、复杂图表或特殊布局才按需截图复核。工具不可用时说明未验证项，不声称一遍过已保证。
