# Mac 截图环境与来源

[平台入口](../README.md) · [教程目录](README.md) · [基础与设置逐图记录](images/current/provenance.json) · [原生作品逐图记录](images/markdown-native/provenance.json)

20 张图均为 2026-10-30 真实 macOS 应用截图。5 张基础内容图由仓库采集脚本生成 PNG；15 张完整窗口、AppKit 设置和原生操作图通过实际系统 UI 采集为 JPEG，保留原始字节，未重新编码、裁切或美化。系统窗口图包含原生标题栏，专注图按实际全屏状态保存。

| 项目 | 实际环境 |
| --- | --- |
| 系统 | macOS 26.5.1（25F80），Darwin 25.5.0，Apple Silicon arm64 |
| 运行应用 | 本地源码构建，Electron 44.7.2，AppKit 设置辅助进程；非 DMG 安装包 |
| 运行代码 | 6388245cbc919ca7b1335fbf59f36abc6cf5a5be；应用运行源码没有修改 |
| 版本 | Mac 目标配置 1.1.0-beta.5-dev.1；未打包源码窗口显示公共版本 1.1.0-beta.5-dev.2 |
| 比例 | Retina 2倍；基础内容图2880×1676；完整窗口2880×1738；原生设置1520×1316；全屏尺寸见逐图记录 |
| 主题／语言 | 系统浅色、中文；普通笔记经典主题；原生作品使用自编示例的纸色布局 |
| 材料 | 临时隔离目录的自编学习示例、仓库 examples/markdown-s-native.md；无账号、密钥、在线 AI 调用 |

## 基础采集

从根目录安装依赖、构建后，已有脚本使用独立用户数据与合成项目采集书架、阅读、编辑、画笔和工作台，不读取个人项目：

```sh
npm ci
node scripts/ensure-electron.mjs
npm run build
node scripts/capture-platform-guide.mjs
```

## 原生窗口与交互复现

1. 在独立测试配置中打开合成学习项目，从书架进入双栏；使用 macOS 设置入口打开外观、阅读、AI 的“表达与交互”模块。
2. 在测试项目的笔记中使用仓库 [原生样例](../../../examples/markdown-s-native.md)，进入作品后按[编号教程](markdown-s.md)依次修改基因、采样位置、世代，并启动／暂停三维旋转。
3. 每个状态通过系统窗口截取保留原图；从实际 `src/notes.md` 读回首个基因、position、generation，文件副本见同目录 saved-notes.md。

仓库另有原生自动采集入口，但**本轮该命令超时**，不能把它当成已通过的复现保证：

```sh
PAGENOTE_GUIDE_NATIVE=1 PAGENOTE_GUIDE_MARKDOWN=examples/markdown-s-native.md node scripts/capture-platform-guide.mjs docs/macos/guide/images/markdown-native
```

失败发生在快速退出全屏的等待；实际 UI 手动“返回笔记”成功。完整重启保真、所有控制项和真实外部服务未测；CI 的旧沙盒最小化失败另列在[审查记录](../../review/PR13-evidence-2026-10-30.md)。
