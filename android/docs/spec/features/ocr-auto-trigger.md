# OCR 自动触发规则

## Scope

Task 9 切片：明确何时自动跑离线 OCR、何时不跑。只定触发判定纯语义，不改引擎与缓存；调用方（page tutor、Agent 任务、页面准备）统一走同一判定，避免各处各一套启发式。

## Behavior

- 输入：原生抽取状态（AVAILABLE/UNAVAILABLE）+ 原生全文（可空）+ 页图是否为扫描/位图（调用方传入 `hasBitmap`，默认 false）。
- 判定 `OcrTrigger.shouldRun(nativeAvailable, nativeText, hasBitmap)`：
  - 原生不可用 → 跑（扫描页/老 PDF）。
  - 原生可用但去空白后空 → 跑（纯图页）。
  - 原生可用但非空且 `hasBitmap=true` 且去空白后长度 < 20 → 跑（疑似图多字少，补 OCR）。
  - 无论是否含位图，原生文本含 Unicode 替换字符 `U+FFFD` 或字面 UTF-8 乱码标记码点 `U+00EF U+00BF U+00BD` → 跑（明确编码损坏信号）。
  - 无论是否含位图，文本至少有 2 个非空白控制符、私用区或孤立代理码点，且这类码点占非空白码点至少 10% → 跑（保守的疑似乱码启发式）。单个私用区符号不触发，以避免常见自定义字形造成不必要 OCR。
  - 其余 → 不跑，返回 `not-needed` 语义（调用方不得把空 OCR 结果当成功）。
- 乱码检测只决定是否补跑 OCR；不改写或丢弃原生文字，OCR 失败仍保留原图并返回既有警告。
- 触发后仍走既有超时（90s）、失败标记、原图保留、低置信度警告语义；自动触发不得弹多余提示，warnings 随结果返回。
- Agent 任务循环内不自动触发 OCR（沿用 agent-task-run 约定：只读可用文本，不在循环内跑推理）；page tutor 仍可按本规则回退 OCR。
- 飞行模式：判定本身不请求网络，引擎走 bundled 模型，沿用 offline-ocr 约定。

## Acceptance (EARS)

- When 原生不可用或空时，shall 自动跑 OCR。
- When 原生有足够文本时，shall 不跑 OCR，直接用原生。
- When 图多字少（hasBitmap 且原生 < 20 字）时，shall 补跑 OCR。
- When 原生文本包含明确替换字符/乱码标记或达到疑似乱码阈值时，shall 补跑 OCR，即使页面不含位图。
- When 原生文本只有一个私用区符号或正常中英文时，shall 不因乱码启发式触发 OCR。
- When Agent 任务循环内时，shall 不自动触发 OCR。

## Verification

- JVM：覆盖不可用/空/图多字少/充足文本/明确乱码标记/控制或私用区比例阈值/正常中英文及单个私用区字符。
- `cd android && ./gradlew :feature-ocr:testDebugUnitTest :app:testDebugUnitTest --offline` 通过。
