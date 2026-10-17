# pagenote 1.1.0-beta.4

已知 Beta 限制：Mac 云端的 Paper 主题手写线条拖动定位断言未通过，Windows 同项通过；尚未确认是否为自动化命中或实际定位缺陷，列入非阻断待办，不计作验证通过。Known beta limitation: the Mac Paper-theme ink-drag regression assertion failed; it is tracked, not reported as passed.

本次整合 obroccolio 的 PR #6，增加 Mac 原生设置、明暗外观、纸张阅读主题与首次启动欢迎页；Windows 保留网页设置窗口。补齐原生设置中的入口、生成策略和用户画像提示词，并增加并发保存版本保护。Mermaid 临时测量节点不再挤占窗口布局。

提供 Windows x64 安装包、免安装 ZIP，以及 Apple Silicon arm64 DMG、ZIP、源码包与 SHA256SUMS.txt。Beta 独立安装、独立本地数据，不接管正式版文件关联；主动打开的项目文件仍需自行复制隔离。main 正式版不变。

Mac 仍是实验性版本，不提供 Intel 包，未签名、公证。物理触控板、Office 授权组合及多屏环境未穷尽验证。完整 MCP 套件的历史导航超时仍记在 KNOWN_ISSUES.md，本次不宣称全环境通过。

This beta integrates PR #6: native Mac settings, appearance themes, Paper reading theme and welcome screen, with current prompt modules and revision-safe saves. Matching Windows x64 and Apple Silicon arm64 packages share this source revision. macOS remains experimental, unsigned and not notarized; no Intel package is provided. Stable main is unchanged.
