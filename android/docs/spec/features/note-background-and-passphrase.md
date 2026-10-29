# 项目纸张背景与笔记密码（移植批次 3 数据层）

- 状态：已实现（移植批次 3，分支 `codex/port-canvas-notes-features`）
- 范围：背景类型枚举与项目级持久化、阅读设置默认纸张背景/模板项、项目级笔记口令（哈希、打开校验、导出加密）。
- 边界：纯数据/设置层。背景**渲染**归画布重做批次；本批次不改任何画布/overlay 绘制代码。

## 1. 背景类型枚举（单一事实来源）

`NoteBackground`（`android/feature-pen/src/main/java/io/pagenote/feature/pen/NoteBackground.kt`）：

- 5 种 key（落盘契约，只增不删、不得改名）：`plain` / `four-line` / `grid` / `dot` / `restricted-margin`。
- `NoteBackground.fromKey` 容错解码：未知/空 key → `null`（读侧降级为"未设置"，不报错、不打不开项目）。
- `resolveNoteBackground(projectOverride, default)`：项目级覆盖优先，未设置回落全局默认；未知持久化 key 视同未设置。

解析优先级：`src/note-settings.json` 的 `background` → 阅读设置 `note_background`（默认 `plain`）。

## 2. 项目级持久化：`src/note-settings.json`

- 文件为 Android 端拥有的项目本地数据，桌面项目管线不读不写（与 `annotations.json` 同类的 src 下共存文件）。
- `ProjectNoteSettingsCodec`（`app/src/main/java/io/pagenote/android/ProjectNoteSettings.kt`）：
  - 形状：`{"version":1, "background":"grid"?, "passphrase":{...}?}`；省略的字段表示"未设置"，未设置的编码不写键。
  - `version` 必须为 1，否则拒绝；未知 root 键忽略（向前兼容）。
  - 未知 `background` key 解码为"未设置"（§1 容错策略）。
- `ProjectNoteSettingsStore`（`app/src/main/java/io/pagenote/android/ProjectNoteSettingsStore.kt`）：SAF 读写，仿 `ProjectPenStore` 模式——src 目录解析（懒缓存 Uri）、首写 `DocumentsContract.createDocument` 创建、覆盖写走 `SafRecoverableDocumentWriter`（保留 SAF 恢复副本）。
- `ProjectDocumentOpener` 打开项目时顺带解析 `src/note-settings.json` 的 Uri（`OpenedProject.noteSettingsUri`，可空，默认 null——旧调用方与 androidTest 兼容）。
- 损坏文件（非法 JSON / 非法版本 / 非法口令块）不阻塞项目打开：读取侧降级为默认值（`MainActivity.readProjectNoteSettings`）。

## 3. 阅读设置：默认纸张背景/模板

- `ReaderSettings.noteBackground: String = "plain"`（键 `note_background`，`ReaderSettingsStore` DataStore 持久化）。
- `ReaderSettingsPolicy`：合法值集合来自 `NoteBackground.ALL`；sanitize 未知值回落 `plain`；validate 拒绝未知值。
- `ReaderSettingsDialog` 新增"默认纸张背景"单选组（五种 + 双语标签 + `note_background_<key>` testTag），交互与 Markdown 主题组一致。

## 4. 笔记密码

### 4.1 口令哈希（项目级）

- 存储形态（`src/note-settings.json` 的 `passphrase` 块）：`{"algorithm":"PBKDF2WithHmacSHA256","iterations":120000,"salt":<b64>,"hash":<b64>}`。
- 为什么不用 AndroidKeystore 加密哈希：哈希必须随项目文件跨设备可校验（项目自带口令），Keystore 密钥设备绑定、不可导出，会破坏"项目走到哪口令跟到哪"。本实现沿用 **AndroidKeystore 先例的架构模式**（`AgentSecretCipher`/`AndroidKeystoreAgentSecretCipher`）：小接口（`NotePassphraseHasher`）+ 默认实现（`Pbkdf2NotePassphraseHasher`）+ 可注入参数（迭代数、salt 源），实现本身纯 JVM、无设备依赖即可单测。
- 参数：PBKDF2WithHmacSHA256、120,000 迭代、16 字节随机 salt、256-bit 派生；校验用 `MessageDigest.isEqual` 常数时间比较；持久化迭代数边界 1..10,000,000（越界读取返回不匹配而非异常）。
- 口令策略（`NotePassphrasePolicy`）：4..128 字符、拒绝 ISO 控制字符、两遍输入一致。

### 4.2 打开校验对话框

- `openProjectTree` 在加载正文前读取项目设置；若存在 `passphrase` 且未携带已验证口令，**不切换阅读状态**，仅弹 `NotePassphraseVerifyDialog`（取消 = 不打开该项目，保持原界面）。
- 验证成功后携带口令重入 `openProjectTree`；口令保留在会话内存（`CharArray`，换项目/清除密码时清零），供导出加密复用。
- 会话恢复（checkpoint 重放）同样过门：口令门在任何项目装载路径之前。

### 4.3 导出加密

- 项目存在口令时，`prepareNoteExport` 生成的导出内容整体加密：
  - 容器（自包含 JSON）：`{"format":"pagenote-encrypted-note-export","version":1,"kdf":{algorithm,iterations,salt},"iv":<b64>,"payload":<b64 AES-GCM>}`。
  - 密钥 = PBKDF2（**每次导出新随机 salt**，绝不复用存储哈希的 salt）→ AES-256-GCM（12 字节 IV，128-bit tag）。
  - 目标文件名：原导出名去扩展 + `.encrypted.json`（`NoteExportPayload.encryptedFileName`）；MIME `application/json`，复用现有 JSON 导出 picker 与 pending-export 快照管线（快照存密文容器，恢复流程不变）。
  - `NoteExportCipher.decrypt` 供测试与后续导入批次使用：错误口令/篡改 payload/异构输入一律异常。
- 未设口令项目导出行为完全不变（明文，与桌面一致）。

## 5. 已知边界（如实记录）

1. **应用层口令门，不是文件加密**：`src/notes.md`、`annotations.json` 等项目文件本身不加密；能直接读写项目文件夹的一方可以绕过口令（包括删除/损坏 `note-settings.json`——此时读取降级为"无口令"，门失效）。这与桌面应用层口令同级，属既定威胁模型。
2. **背景渲染未接线**：本批次提供数据与设置 UI，画布仍按原样渲染；`shape`/背景的消费归画布重做批次。
3. **口令输入框的中间态**：Compose `TextField` 的字符串中间态无法主动清零（平台限制）；提交后立即转 `CharArray` 并在用后清零。
4. **桌面端不消费本文件**：桌面读项目时忽略 `src/note-settings.json`；未来若桌面实现同款口令，容器与哈希格式以本文档为准。

## 6. 测试与验证

- `:feature-pen:testDebugUnitTest`（`NoteBackgroundTest` 7 用例：目录顺序、key 往返、未知 key 降级、key 拼写护栏、覆盖/默认/未知覆盖解析）。
- `:app:testDebugUnitTest` 新增：
  - `NotePassphraseCryptoTest`（11）：哈希验证/拒绝、随机 salt、JSON 往返、非法存储拒绝、损坏哈希安全返回 false、导出加密往返（Unicode）、错误口令/篡改/异构输入拒绝、容器契约字段与随机性。
  - `ProjectNoteSettingsTest`（10）：默认形状、空形状、往返、省略字段、未知背景降级、未知 root 键忽略、非法版本/JSON/口令块拒绝、清除口令编码。
  - `NotePassphrasePolicyTest`（4）：长度边界、控制字符、确认匹配。
  - `ReaderSettingsTest`/`ReaderSettingsStoreTest`/`NoteExportPayloadTest` 扩展：默认值、全目录 key 校验、sanitize 回落、持久化往返、加密文件名替换。
- `:app:compileDebugAndroidTestKotlin` 通过（`OpenedProject` 新字段带默认值，兼容既有 androidTest 构造）。
- 命令（`android/` 目录下）：`./gradlew :feature-pen:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`。
