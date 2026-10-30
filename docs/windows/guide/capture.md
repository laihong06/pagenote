# Windows 图片来源与本轮边界

[平台入口](../README.md) · [教程](README.md) · [功能表](../features.md)

本轮在 macOS 上整理文档，没有本机重新运行 Windows。以下图片均沿用 laihong 仓库已有实际 Windows 图，原始字节和 provenance 保持不变，不写成当前提交重新拍摄。

| 图组 | 原采集环境 | 源码状态 | 原记录 |
| --- | --- | --- | --- |
| current：书架、阅读、编辑、画笔、工作台 | 2026-10-29；Windows 10.0.26200 x64；逻辑1426×898，1.5倍；浅色／中文；开发版1.1.0-beta.5-dev.2 | 基线8daaf15e7f4fcee44f13864c98d260d89d2fba15；原工作区有修改 | [provenance](images/current/provenance.json) |
| markdown-native：SVG／Canvas／图表／Three.js 等 | 2026-10-30；同系统；普通逻辑1426×898，全屏1707×1067，1.5倍；作品纸色／中文；开发版1.1.0-beta.5-dev.2 | 基线ddc0384bde7d1cc4f2cf538e349a9c6501bc0ba3；原工作区有修改 | [provenance](images/markdown-native/provenance.json) |

原图的修改工作区没有逐文件补丁存档，不能追溯成精确干净提交；本轮不修改原记录来制造该结论。旧 `images/` 与 `images/markdown-s/` 另有历史截图／沙盒原型，未作为本轮原生作品或当前功能通过证据。

基础图由仓库 `node scripts/capture-platform-guide.mjs` 采集合成学习项目；原生模块见[已有操作与保存教程](markdown-s.md)。每个模块独立图片，未重新拼接或美化。没有给本轮伪造历史 UI 改前图；本轮只改文档。

当前共享基线 `6388245` 的 [Windows CI](https://github.com/laihong06/pagenote/actions/runs/41019223250/job/123942471901) 构建、单测、原生模块／导入／保存重启及基础截图任务通过。云端任务有新产物不等于本页仓库图片已经替换；本页继续注明原出处。安装包、真实 Office、账号与 AI 请求、实体笔、其他 DPI／双屏未在本轮作者自测中覆盖，维护者布局结论待审。
