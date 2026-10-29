# 参与 PageNote / Contributing

提交 PR 前请阅读 [PR 提交与界面审查标准](docs/PR-STANDARD.zh-CN.md)，并使用 [PR 模板](.github/pull_request_template.md)。

多平台源码、各平台 main/beta、独立版本与发布、共享同步及 README／教程／截图覆盖要求见 [多平台维护要求](docs/PLATFORM-MAINTENANCE.zh-CN.md)。目录和分支迁移尚未完成前仍使用现有 `beta` 入口；已有 PR 直接补充材料，不必重复新建。功能变化同步更新对应平台文档，不需要更新时说明理由。

- 用通俗语言说明问题、功能结构、每个模块的作用、入口与操作结果。
- 界面改动附真实应用的改前／改后截图，分别注明平台、环境和运行代码提交；有交互变化时附录屏或编号过程截图。
- 先审查内容空间、操作入口和视觉简洁性，再核对功能与测试。没有界面影响的改动说明免图原因。
- 新功能和常规修复从 `beta` 开始，PR 指向 `beta`；每次推送包含匹配的 `CHANGELOG.md` 条目。
- 测试记录如实区分通过、失败、跳过、未测。正式发布另按 [发布政策](docs/RELEASE-POLICY.md) 执行。

PRs must explain the problem and affected modules in plain language, including each module's purpose, entry point, user actions and results. UI changes need real before/after application screenshots for each affected platform, with environment and runtime commit information; interaction changes also need a recording or numbered screenshots. Explain any screenshot exemption. New features and routine fixes target `beta`, and every push includes a matching changelog entry. See the linked Chinese standard and template for the full review checklist. Release approval remains separate.
