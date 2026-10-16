# 文档教学工作流（beta）

入口为 `page-notes://prompts/index`，包含可编辑入口正文、模块版本、场景路由、MCP 能力及材料位置。上层原厂 Agent 自主管理会话、上下文、记忆与子代理；软件不限定一次装载量、会话数量或一页一个请求。逐页约束的是对应、充分且连贯的成果。

## 初次生成

读取 generation 策略，按需使用 learning、page-tutor、output 与 learner/profile。`page_notes_prepare_task({scenario:"generation", documentId, page})` 本地准备当前页和相邻源页文字、OCR 状态、图片入口及当前笔记版本。`neighbors:false` 只取本页。Agent 自行决定拆分、并行、补读或共享材料，`max(1, floor(总页数/10))` 仅为起步建议。

从用户基础出发，直接陈述并逐步解释概念、条件、符号、推导和例子。流程实际制作 Mermaid，算法给代码，机制可调用上层生图，推导给公式；按内容选择，不每页凑齐全部形式。上下页依据源材料衔接，不编造其他 Agent 的讲稿。

## 按意见修改

读取 tag-edit 和共享画像，重讲时使用教学技能。`page_notes_prepare_task({scenario:"revision", documentId, page, reviewIds, instruction})` 提供当前原文、已有笔记、版本和选定意见；instruction 支持直接文字需求，不要求先创建 Tag。整体 Tag 可应用到当前页，局部 Tag 必须属于该页。左侧意见指原文，右侧意见指笔记，不可混淆。

## 数据与提交

软件按原文版本缓存提取、OCR 和图像结果，不让模型重复整理。文字分页返回时，检查 hasMore、nextOffset、continueWith；局部笔记不能整页替换。prepare_page(output=image) 返回原图，图表与公式按需核对，不宣称 OCR 无损。

保存前用 page_notes_validate_note 检查格式，带 expectedRevision 提交。每页完成即静默保存，不跳转阅读位置。只修复发现的具体问题，不规定反复截图审查，不保证绝对一次无错。Tag 对应修改成功保存后才标 applied。

## 用户偏好与设置

教学规则与偏好分开保存。修改成功后，AI 可用 page_notes_update_profile 从已完成 Tag 提炼候选偏好，提供来源及 profile revision。候选只作软参考，本次要求及用户确认偏好优先；不从课件推断身份或敏感属性。

**设置 → AI 设置** 可编辑入口、两种策略、教学技能、输出格式、画像规则和用户偏好，支持保存与恢复默认；清空偏好文本即可删除偏好。模块修改在下次读取时生效，无需复制提示词；系统提示更改后需重连。默认全文见 [提示词清单](PROMPTS.zh-CN.md)。模型、子代理执行及生图能力来自上层 Agent。

直接文字修改需求也能提炼偏好：prepare_task 返回 evidenceId，保存对应页笔记或原文后，update_profile 可携带该凭据代替 reviewIds。软件校验内容已变化，并保留原始意见作为来源；未保存的修改不能更新画像。凭据在本次软件进程内有效，已保存偏好跨重启保留。
