# 早期阶段发布基准 / Early-stage release policy

## 分支与晋级

`main` 为正式版分支，`beta` 为测试集成分支。新功能和常规修复先在 `beta` 更新；实测并经发起人明确批准后，通过 PR 晋级 `main`，再执行正式发布流程。CI 通过不能代替本次晋级的批准。测试期间，正式版下载入口保持不变。

2026-10-17，由项目发起人确认。本文是当前发布决策依据；旧审计报告、RC 模板中的更严格要求保留为历史记录，不自动成为本次发布阻断项。

pagenote 采用分级质量门槛。正式版冻结表示功能范围和发布基线已经确定，不表示零 Bug，也不表示所有硬件、Office 版本或系统环境全部验证。

1. 平台核心主流程须可复现。干净虚拟机快照可作为全新裸机环境的等效验证；不强制追加全量真机矩阵。
2. 功能冻结后不新增功能，仅修复阻断性、致命问题，例如无法启动、项目无法打开、确认保存的数据丢失，以及核心内容无法阅读。
3. 非阻断边缘问题和未验证充分的环境统一记录到根目录 `KNOWN_ISSUES.md`，作为后续待办，不阻塞本次发布。
4. 项目发起人拥有最终发布决策权。测试提供风险评估依据，不作为无限期延迟发布的理由。

## 本次平台决策

- **Windows：v1.0 正式版。** LTH0001 提供的 Windows 测试结果（见 PR #4），加上项目发起人在本机的补测，共同作为发布依据；发起人综合两部分结果批准冻结发布。这是本次放行依据；维护者不据此虚构独立虚拟机、其他设备或厂商复核结果。
- **macOS：测试版。** 集成贡献的适配代码，构建包版本为 `1.0.0-beta.1`。尚不将 Windows 的验收结论推广至 Mac。后续按同一分级原则单独决定正式放行。
- 本次从此前的 `2.0.0-beta.*` / RC 开发编号统一为首个正式版本 `1.0.0`。升级需退出旧程序后手动安装，不依赖版本数字大小自动升级。不替换历史包。

签名、多环境兼容、长时间压力测试等未完成工作按实际状态记录，不因未覆盖所有环境自动阻塞本次 Windows 发布。安全软件检测结果如有变化须如实披露，禁止将未复核的检测宣称为误报。

## English

Release gates are proportional to this early-stage project. Stable does not mean zero bugs or exhaustive environment validation. Clean VM snapshots are acceptable fresh-machine evidence. Freeze features and fix release-blocking defects; track other issues and unverified environments in `KNOWN_ISSUES.md`. The founder makes the final release decision.

Windows v1.0 is approved based on LTH0001's Windows tests plus the founder's supplementary local tests, jointly accepted by the founder. No additional VM results are claimed. macOS remains experimental with version `1.0.0-beta.1`. Historical audit checklists do not override this policy. Earlier 2.0 beta numbering is intentionally reset to the first stable 1.0 release; use manual installation after exiting the previous app.
