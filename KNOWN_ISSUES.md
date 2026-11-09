# 已知问题与未验证范围 / Known issues

Windows v1.0 已由发起人批准正式发布，macOS 仍为测试版。以下项目是风险与待办记录，不代表都已复现为产品缺陷，也不自动阻塞本次 Windows 发布。当前发布依据见 [发布基准](docs/RELEASE-POLICY.md)。

| 项目 | 状态与后续处理 |
|---|---|
| 历史长测约 54 分钟时窗口关闭 | 原因未明；重开后已保存内容恢复。历史中断不记作一小时通过，保留后续排查。 |
| Mac 全流程与环境覆盖 | 贡献者报告部分实机测试，本次维护者使用 Windows，未独立复验 Mac；继续测试版。 |
| 多种 Office、DPI、双屏和低配置设备 | 未穷尽环境；报告具体条件后逐项处理。 |
| Windows 签名与安全软件 | 当前未签名。早期 NSIS 安装包曾被火绒查杀，未取得厂商复核结论；不将其宣称为误报。新文件以实际检测为准。 |
| Mac 签名、公证和首次打开 | 当前构建未签名、公证；系统可能拦截，需后续完善分发体验。 |
| OCR 运行库与识别质量 | 部分 Windows 环境可能缺少 VC++ 运行库；OCR 不是无损识别，图表仍需结合原页图查看。 |
| 旧 2.0 测试版升级至 1.0 | 有意重置首个正式版本编号；完全退出旧版，手动安装或完整解压新 ZIP。若系统拒绝覆盖，可卸载应用后安装；先备份项目，保留用户数据。 |
| AI、Word/PPT 外部依赖 | 不内置大模型；AI 客户端、服务额度和本机 Office 授权需用户自行准备。 |

## HarmonyOS（鸿蒙，开发线 harmonyos/beta，未发布）

| 项目 | 状态与后续处理 |
|---|---|
| 模拟器镜像缺 PDF Kit（libpdfview.z.so） | MatePad Pro 13 模拟器（API 24）无 PDF Kit 原生库，PDF 项目自动降级为来源图片显示；PdfView 真实渲染需真机或完整镜像验证。 |
| 竖屏/旋转 | 阅读页已实现横竖屏自适应，但模拟器 rotate 命令无效、折叠屏模拟器调试通道未连通，竖屏分支未经上机验证。 |
| 文件/相册选择器可见性 | 选择器只显示媒体库已索引的文件；导入文档全链路、插图选图后闭环、导入笔记选文件均受此限制，设备上只验到选择器弹出。 |
| 雨课堂实时采集 | 协议与队列已实现并编译验证，未经真实课堂验收（重连/去重/笔记保护）。 |
| AI 真实调用 | 按页讲解与选区动作已实现；本机中转端点未运行时未做真实请求验证。 |
| 签名与分发 | 当前只产未签名 debug HAP；正式发布需 AGC 证书与发布流程，未排期。 |

遇到 Bug 请[提交 Issue](https://github.com/laihong06/pagenote/issues/new)，附版本、系统、操作步骤及脱敏截图；不要上传私人课件或连接密钥。联系：chennanfeng310@gmail.com。

Windows stable is founder-approved; macOS remains experimental. Pending work includes the unexplained interrupted soak, wider environment coverage, signing/notarization, antivirus review, and OCR prerequisites. These are tracked risks under the early-stage release policy, not claims that every environment has passed.
