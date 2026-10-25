# Audio Recording and Offline ASR（移植批次 8）

- 状态：已实施（分支 `codex/port-canvas-notes-features`，移植差距清单 §八·#19）
- 范围：录音/回放基础（MediaRecorder/MediaPlayer 封装、RECORD_AUDIO 权限流程）、
  sherpa-onnx（Apache-2.0）离线 ASR 集成（流式识别 + VAD 分段 + 短音频回退）、
  画布音频胶囊对象（录制落点/命中/播放）、转写列表 + 回听跳转、存储限额与设置。
  参考实现的闭源 native 识别引擎不可移植，全部替换为开源离线选型（sherpa-onnx +
  Silero VAD，与批次 6 的"闭源引擎 → 开源 ONNX"同一条红线）。

## 0. 参考结构映射（"18 类"）

参考实现（touchnotes）的录音/ASR 区为闭源，类清单不可得；本批次按功能分解组织为
`feature-audio` 的 15 个纯 JVM 类 + app 层 5 个 Android 封装/接线类，与参考能力的
对应关系如下（可对账的功能位）：

| 功能位 | 本批次承载 |
| --- | --- |
| 录音会话/状态机 | `AudioRecordingPolicy` + app `AudioMediaRecorderHost`（MediaRecorder） |
| 回放会话/进度 | `AudioPlaybackPolicy` + app `AudioMediaPlayerHost`（MediaPlayer） |
| 录音权限 | app `AudioWiring.rememberAudioRecordLauncher`（RECORD_AUDIO 流程） |
| 音频对象模型 | `AudioCapsule` / `TranscriptSegment` / `AudioTranscriptStatus` |
| 对象清单持久化 | `AudioCapsuleCodec`（audio.json）+ app `ProjectAudioStore`（SAF） |
| 落点/容量边界 | `AudioCapsulePolicy` |
| 画布几何/命中 | `AudioCapsuleGeometry` + app `AudioCapsuleOverlay` |
| 会话内索引 | `AudioCapsuleIndex` |
| 存储限额 | `AudioQuotaPolicy` |
| 识别语言 | `AsrLanguage` |
| 模型目录 | `AsrModelCatalog` |
| 引擎边界（流式/离线/VAD） | `AsrEngineContracts`（`StreamingAsrSession`/`ShortClipRecognizer`/`VoiceActivityDetector`/`AsrRuntimeBinding`） |
| VAD 分段驱动 | `VadSegmenter` |
| 转写编排/短音频回退 | `AsrTranscriber` |
| 转写文本格式化 | `AudioTranscriptFormatter` |
| PCM 载体 | `PcmAudio` |
| 会话状态中枢 | app `AudioSessionController` |
| 文件解码 | app `AudioPcmDecoder`（MediaExtractor/MediaCodec → 16k mono） |

## 1. 录音/回放基础

- **录音**：`AudioMediaRecorderHost`（app）包 MediaRecorder → AAC-LC 32kbps 16kHz
  单声道 MPEG-4（`<uuid36>.m4a`），输出直接写 SAF 文档描述符（`openFileDescriptor` +
  `setOutputFile(fd)`，与项目附件通道同盘）；`maxFileSize`/`maxDuration` 由配额策略
  在 prepare 前注入（§5）。每次录音独立宿主实例（会话代号失效时本地停止，不误伤
  后续会话）。策略类 `AudioRecordingPolicy`：IDLE→PREPARING→RECORDING 状态机、
  最短有效录音 500ms（更短丢弃）、60 分钟硬上限、maxAmplitude→0..1 对数电平。
- **回放**：`AudioMediaPlayerHost` 包 MediaPlayer（SAF 描述符 `setDataSource(fd)`），
  seek 夹取/进度换算/时钟标签走 `AudioPlaybackPolicy`；自然结束与错误回调复位。
- **权限流程**：`AudioWiring.rememberAudioRecordLauncher`——已授权直接开始，否则
  `RequestPermission` 结果回调续接；拒绝上报可恢复提示。Manifest 声明
  `android.permission.RECORD_AUDIO`。
- **录音交互**：阅读菜单 READING 组新增"录音"（仅 PDF 阅读，胶囊渲染在 PDF 页面
  overlay 上；Markdown 项目本批次不开放入口）；录音中为模态对话框（计时 + 电平条 +
  停止落点/取消）。落点 = 停止时刻的视口中心经 `mapPdfTapToAnnotationPoint` 映射到
  width-1000 文档坐标，锚点经 `AudioCapsulePolicy.clampAnchor` 夹取保证胶囊完整在页内。

## 2. 画布音频胶囊对象（不新增 annotations.json 类型）

- **存储决策（对齐批次 5 materials.json 先例）**：胶囊清单是 Android 侧独立文件
  `<project>/audio.json`（version 1），录音二进制在 `<project>/audio/<uuid36>.m4a`；
  **完全不进 annotations.json**（D1：Android 不单方面新增 type 枚举值；桌面端对未知
  项目文件原样保留）。桌面端不读取也不解释两处文件——与贴纸库同一既定容忍点。
- **数据模型**：`AudioCapsule{id, page(1起), anchorX/anchorY(width-1000), asset,
  durationMs, createdAt, transcript, transcriptStatus(none/pending/ok/failed),
  segments[{startMs,endMs,text}], language, model}`。`AudioCapsuleCodec` 读侧容错
  （坏条目跳过、重复 id 保留首条且**非法条目不占用 id**）、写侧严格（OK 状态必须有
  片段与文本、片段按 startMs 升序、2000 段/20000 字上限）；清单写入对齐
  `ProjectMaterialStore`（SafRecoverableDocumentWriter 恢复写入）。
- **几何/命中**：`AudioCapsuleGeometry`——120×56 文档单位显示框、12 单位命中容差、
  多重命中取创建时间最晚（后放的上层）；overlay 绘制（`AudioCapsuleOverlay`）与
  视口点击路由共用同一矩形。坐标映射与 `PdfAnnotationOverlay` 同契约
  （`pageWidthScale = size.width / 1000f`）。
- **画布交互**：`PdfPageViewport` 新增胶囊 overlay 层与 `audioCapsuleTapEnabled`
  手势分支（镜像 annotateTap 分支的消费语义）；仅在**选中工具**且当前页有胶囊时
  接管点按，命中即播放并打开转写列表，未命中不影响既有手势。
- **容量**：单页 50 / 全库 500 上限（`AudioCapsulePolicy`），超限拒落并提示。

## 3. sherpa-onnx 离线 ASR（流式 + VAD + 短音频回退）

- **选型**：[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)（k2-fsa，Apache-2.0）：
  流式 Zipformer 转置识别器（zh+en 双语）+ 离线 Paraformer（短音频）+ Silero VAD
  （MIT）。未采用 Maven 上的第三方封装构件（无官方 `com.k2fsa` 构件，社区封装来源
  不可控），与批次 6 pix2tex 同姿态：**引擎边界契约先行、运行时二进制不随仓库捆绑**。
- **引擎边界**（`AsrEngineContracts`）：接口形状镜像 sherpa-onnx Kotlin API 子集
  （`OnlineRecognizer.Stream` → `StreamingAsrSession`；`OfflineRecognizer` →
  `ShortClipRecognizer`；`Vad` → `VoiceActivityDetector`，含 `flush()` 收尾语义）；
  真实绑定按 `AsrRuntimeBinding` 实现约 60 行粘合（模型文件配置映射 + JNI 装载）随
  运行时落地批次接入。本批次缺省绑定 `MissingAsrRuntimeBinding` 报告"运行时未捆绑"，
  转写折叠为 FAILED 可恢复状态，**录音/回放链路完全不受影响**。
- **模型目录**（`AsrModelCatalog`，3 个捆绑）：`zipformer-paraformer-zh-en`（默认，
  流式+离线+VAD）、`zipformer-streaming-zh-en`（轻量，仅流式）、`paraformer-zh`
  （低内存，仅离线中文）。文件名契约冻结（tokens.txt / encoder|decoder|joiner /
  model.int8.onnx / silero_vad.onnx），放置于 `<filesDir>/models/asr/<捆绑>/...`。
- **转写管线**（`AsrTranscriber`）：录音停止后（或列表手动重试）app 层
  `AudioPcmDecoder`（MediaExtractor 选音轨 + MediaCodec 解码 + 下混 + 线性插值重采样，
  `PcmAudio.resampleToTarget` 纯 JVM 已测）产出 16k 单声道 float PCM，然后：
  1. 时长 ≤ 30s（`DEFAULT_SHORT_CLIP_MAX_MS`）→ **短音频回退**：直接离线一次性识别
     （捆绑无离线组件时走流式整段）；
  2. 长音频 → `VadSegmenter` 按 100ms 窗驱动 VAD（静音边界闭合 + `flush()` 收尾）
     → 每段一个流式会话（喂入→inputFinished→收文），段级绝对毫秒时间戳来自 VAD
     跨度；词级时间戳可用时按 2s 聚桶成行；
  3. VAD 空结果 → 整段按短音频路径再试一次（防漏检）；
  4. 引擎缺失/异常折叠为 FAILED（不抛出，对齐批次 6 OCR 姿态）。
- **触发**：设置"录音后自动转写"（缺省开）在落胶囊时以 PENDING 状态排队；列表对
  none/failed 提供重试。语言经 `AsrLanguage.resolve`（system 跟随 locale）；所选
  捆绑不支持目标语言时回落 `AsrModelCatalog.resolveForLanguage`。
- **会话纪律**：全部音频宿主调用（MediaRecorder/MediaPlayer/解码）串行在单线程
  调度器（`IO.limitedParallelism(1)`）上；录音会话代号防御 PREPARING 期取消竞态。

## 4. 转写列表 + 回听跳转

- `AudioTranscriptListDialog`：全部胶囊按页号/创建时间排序；行内状态 chip
  （未转写/转写中/已转写/失败）、语言·模型元信息、预览（80 码点截断）；已转写胶囊
  展开至多 6 行带时间戳片段。
- **回听跳转**：点片段行 → `seekPlayback(capsule, segment.startMs)`（未在播则从该
  句起播）；播放行内进度条可拖拽 seek；时钟标签 mm:ss/h:mm:ss。
- **跳页**：行内"跳页"关闭对话框并切换阅读页（1 起页号 → 0 起阅读器索引，与批次 7
  跳转策略同向）。
- **画布回听**：点画布胶囊 = 播放 + 打开列表（正在播的胶囊在 overlay 上高亮）。

## 5. 存储限额 + 设置

- `AudioQuotaPolicy`：配额值域 50..4096 MB（缺省 200）；按 AAC 32kbps 假设
  4000 B/s 折算——录制开始前"剩余预算 → maxFileSize/maxDuration"双闸，剩余不足以
  录满最短有效录音（500ms）时拒绝开始；单条硬上限 60 分钟。用量统计
  `<project>/audio/` 下合法 `*.m4a` 字节和（设置对话框展示已用量）。
- **ReaderSettings 新增 4 字段**（DataStore 持久化，阅读设置对话框新增"录音与转写"
  节）：`asrModel`（目录 id，越界回落默认）、`asrLanguage`（system/zh/en）、
  `asrAutoTranscribe`（缺省开）、`audioQuotaMb`（滑杆，25MB 步进、sanitize 夹取）。
  桌面端不解释这些设置（应用本地 DataStore，不进项目文件）。

## 6. Verification

- 命令：`cd android && ./gradlew :feature-audio:testDebugUnitTest :app:testDebugUnitTest
  :core-search:testDebugUnitTest :feature-annotations:testDebugUnitTest
  :feature-pen:testDebugUnitTest :feature-notes:testDebugUnitTest
  :core-project:testDebugUnitTest :app:assembleDebug
  :app:compileDebugAndroidTestKotlin :feature-audio:compileDebugAndroidTestKotlin`
- 批次结果：feature-audio 59/59（全新增：Codec 12、Policy/Geometry/Index 8、
  Quota/Recording/Playback 14、Pcm/Language/Catalog/Formatter 16、ASR 管线 9——
  覆盖编解码往返与容错、命中几何、限额双闸、录音/回放状态机、重采样、VAD 分段
  换算、短音频/长音频/VAD 空回落/语言回落/失败折叠路由）；app 473/473（含新增
  ReaderAudioSettingsTest 6）；core-search 45/45、feature-annotations 74/74、
  feature-pen 109/109、feature-notes 25/25、core-project 22/22 回归全绿；
  `:app:assembleDebug`（4 ABI APK）与 androidTest 源码编译通过。
- 过程中修复两处实现缺陷（由新增测试暴露）：audio.json 解码时非法条目错误占用
  id 导致同 id 合法条目被去重吞掉；PREPARING 期取消的在途录音竞态（会话代号 +
  每会话独立宿主实例）。

## 7. 未覆盖（Not covered）

- **sherpa-onnx 运行时与模型二进制未随仓库捆绑**：编排/分段/回退/格式化全部经注入
  式 JVM 测试，但真实推理需按 §3 契约落地——实现 `AsrRuntimeBinding` 粘合（约 60
  行，映射 sherpa-onnx Kotlin API）并放入 `models/asr/` 模型文件；真机推理数值与
  延迟未验证。运行时未捆绑时转写为 FAILED 可恢复状态（录音/回放不受影响）。
- 真机/AVD 交互回归未运行（JVM 无法覆盖麦克风采集、SAF 文档写入与触摸手势）：
  录音落点、画布点按、回放 seek 的端到端验收沿用 AVD 验证模式留给后续批次。
- MediaRecorder/MediaPlayer/MediaCodec 封装（app 层 Android API）仅有编译验证，
  无 JVM 单测（对齐批次 5 StickerPackImporter 的边界测试姿态）。
- 音频转写文本**未入批次 7 全文索引**：`search_docs` 的 (project, page, kind) 唯一
  键与手写转写冲突（每页仅一条 transcript 槽位），需要按键扩展（如 capsule 维度）
  后接入，本批次不动批次 7 schema。
- Markdown 项目暂无录音入口（胶囊 overlay 渲染在 PDF 页面层）；Note 面侧胶囊与
  胶囊拖拽/编辑归画布重做批次。
- 桌面端不解释 audio.json/audio/ 与 asr 设置（既定容忍点，与贴纸库一致）。
