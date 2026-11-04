---
name: harmonyos-dev
description: 本机 HarmonyOS（鸿蒙）ArkTS/ArkUI 应用开发与构建技能。适用于本项目中 harmonyos/ 工程的开发、编译、调试与模拟器运行。基于本机实际安装的 DevEco Studio 6.1 与 HarmonyOS 6.1.1（API 24）SDK 整理。
---

# HarmonyOS 本地开发技能（PageNote 鸿蒙版适配专用）

本技能是针对本机环境核实过的操作手册。所有路径均为本机真实路径，命令在 Git Bash 或 PowerShell 中执行。

## 1. 本机工具链位置

| 工具 | 路径 |
| --- | --- |
| DevEco Studio | `D:\Program Files\DevEco Studio` |
| HarmonyOS SDK（API 24 / 6.1.1 Release） | `D:\Program Files\DevEco Studio\sdk\default\openharmony` |
| HMS SDK | `D:\Program Files\DevEco Studio\sdk\default\hms` |
| hvigor CLI（6.24.4） | `D:\Program Files\DevEco Studio\tools\hvigor` |
| ohpm（6.1.2.285） | `D:\Program Files\DevEco Studio\tools\ohpm\bin\ohpm.bat` |
| node（v18.20.1，供 hvigor 用） | `D:\Program Files\DevEco Studio\tools\node\node.exe` |
| hdc（设备/模拟器调试） | `D:\Program Files\DevEco Studio\sdk\default\openharmony\toolchains\hdc.exe` |
| 模拟器系统镜像（平板 tablet_x86，6.0.0–6.1.1） | `D:\Program Files\Huawei\Sdk\system-image\` |
| hvigor 缓存（离线可用，已含 6.24.4） | `~/.hvigor/project_caches/` |

## 2. 命令行构建（不依赖 DevEco GUI）

```bash
cd harmonyos
# 一键脚本（等价于下面的手动命令）：
./build.cmd

# 手动命令（Git Bash）：
export DEVECO_SDK_HOME="D:\Program Files\DevEco Studio\sdk"
export NODE_PATH="$PWD/node_modules"
export PATH="/d/Program Files/DevEco Studio/jbr/bin:$PATH"   # 打包器需要 java
node "D:\Program Files\DevEco Studio\tools\hvigor\hvigor\bin\hvigor.js" --mode module -p product=default assembleHap --no-daemon
```

- `hvigor/hvigor-config.json5` 与 `oh-package.json5` 的 `modelVersion` 必须一致（DevEco 26 用 `"6.1.1"`）；config 里**不要**写 `hvigorVersion` 字段（schema 不允许）。构建依赖通过 `node_modules/@ohos/` 下的目录联接指向 DevEco 自带的 hvigor 6.26.8 与 hvigor-ohos-plugin，首次运行 `harmonyos/setup-links.ps1` 建立，离线可构建。
- 未配置签名时只能产出未签名 HAP；安装到模拟器需要在 DevEco 里自动生成调试签名，写入 `build-profile.json5` 的 `signingConfigs`。
- 语法/类型检查可只跑 `node ... hvigor.js assembleHap` 的前半段输出，ArkTS 编译错误会中断构建。

## 3. 模拟器与 hdc

```bash
HDC="D:\Program Files\DevEco Studio\sdk\default\openharmony\toolchains\hdc.exe"
"$HDC" list targets          # 查看已连接设备/模拟器
"$HDC" install entry-default-signed.hap
"$HDC" shell aa start -a EntryAbility -b io.pagenote.harmonyos
"$HDC" shell hilog | grep -i pagenote
```

模拟器由 DevEco Studio 的 Device Manager 启动（镜像已就绪：MatePad Pro 13 等 tablet_x86）。

## 4. 工程结构约定（Stage 模型，API 20+ 兼容）

```
harmonyos/
├── build-profile.json5        # app 级：products、signingConfigs、模块列表
├── oh-package.json5           # 工程级依赖
├── hvigorfile.ts              # export { system: appTasks }
├── hvigor/hvigor-config.json5 # 固定 hvigor/插件版本，保证离线可构建
├── AppScope/app.json5         # bundleName io.pagenote.harmonyos、versionName
├── AppScope/resources/base/   # app_name、app_icon（media 下需 PNG）
└── entry/
    ├── build-profile.json5    # targets
    ├── oh-package.json5
    ├── hvigorfile.ts          # export { system: hapTasks }
    └── src/main/
        ├── module.json5       # abilities、deviceTypes [tablet, 2in1]、pages profile
        ├── ets/entryability/EntryAbility.ets
        ├── ets/pages/*.ets    # @Entry 页或 Navigation 目的地
        └── resources/base/{element,media,profile}/
```

要点：
- `module.json5` 的 `pages` 指向 `$profile:main_pages`，对应 `resources/base/profile/main_pages.json`。
- 新增页面必须同时加入 `main_pages.json`，否则路由不到。
- 资源引用：`$string:xxx` / `$color:xxx` / `$media:xxx` / `$profile:xxx`。

## 5. 本项目用到的关键 Kit API（已在 SDK d.ts 中核实）

- **PDF 阅读**：`@kit.PDFKit`（API 12+）。
  `PdfView` 组件 + `pdfViewManager.PdfController`：
  `loadDocument(path, password?, initPageIndex?, onProgress?)`、`goToPage(pageIndex)`、
  `getPageCount()`、`registerScrollListener(cb)`（回调 `ScrollParam`，含当前页信息）、
  `releaseDocument()`。
- **文件读写**：`@kit.CoreFileKit` 的 `@ohos.file.fs`（同步 API：`fs.openSync/readSync/writeSync/renameSync/mkdirSync/listFileSync`）。文本注意 UTF-8 与 `buffer` 转换。
- **用户文件选择**：`@kit.CoreFileKit` 的 `picker.DocumentViewPicker`（`documentSelectOptions`），选中的文件用 `fs.copyFileSync` 复制进应用沙箱后再用路径访问。
- **SHA-256**（笔记 revision、项目 ID）：`@kit.CryptoArchitectureKit` 的 `cryptoFramework.createMd('SHA256')`，`update` + `digest`，输出转小写 hex。
- **路由**：`Navigation` + `NavDestination` + `NavPathStack`（API 12 推荐），跨页传参用 `pathStack.pushPath({ name, param })`。

## 6. 鸿蒙平台约束（与桌面/Android 的差异，适配时必须遵守）

- **没有任意文件路径访问**。用户文件只能经 `DocumentViewPicker`/`AudioViewPicker` 授权并复制进沙箱，或走 `fileshare` 持久授权（需另验）。项目统一存 `context.filesDir/projects/<项目名>/`。
- 沙箱路径：`EntryAbility` 里 `this.context.filesDir`。PDF Kit 的 `loadDocument` 需要真实沙箱路径，不能直接用 `datashare://` URI。
- 设备形态按平板优先（对标 Android 平板版），`deviceTypes` 含 `tablet`；横竖屏自适应用 `Row/Column` + `layoutWeight`，不要写死宽度。
- 网络访问需 `ohos.permission.INTERNET`（AI 功能后续用）。
- ArkTS 强类型：禁用 `any` 隐式用法，JSON 解析结果要先收窄再访问字段。

## 7. PageNote 共享格式契约（跨平台必须一致）

实现位置：`entry/src/main/ets/core/`。与 Android `core-project` 逐条对齐：

- 项目目录：`<项目>/project.pagenote` + `src/{left.*, right.md, assets/}`。
- manifest：`format` ∈ {`pagenote-project`, `page-notes-project`}，`version`=1，
  `id` 为 64 位小写 hex，`left/right/preview` 必须是 `src/` 内相对路径（拒绝绝对路径与逃逸 `..`）。
- 笔记分页：分隔符 `<!-- pagebreak -->`，切分正则
  `/(?:\r?\n){2}<!--[ \t]*pagebreak[ \t]*-->(?:\r?\n){2}|^[ \t]*<!--[ \t]*pagebreak[ \t]*-->[ \t]*$/gm`，
  合并用 `\n\n<!-- pagebreak -->\n\n`，空页保留，页码从 1 开始。
- revision = 整页笔记 UTF-8 的 SHA-256 hex；写操作带 `expectedRevision`，不一致拒绝覆盖。
- 写文件用"临时文件 + rename"原子写，避免半写状态。

## 8. 测试与验证纪律（项目要求）

- 不宣称未执行的验证。模拟器/真机截图只能来自鸿蒙端实际运行。
- 每次推送必须更新根 `CHANGELOG.md`（Unreleased 段）。
- 平台独立版本配置：`platforms/harmonyos/version.json`，未发布前 `published` 保持 null。
