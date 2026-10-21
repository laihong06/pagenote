# 雨课堂全端接入任务

关联 SPEC：

- [需求](requirements.md)
- [实现设计](design.md)
- [调研](research.md)

状态：实现分支 `codex/yuketang-cross-platform`。任务按下列顺序执行，完成项更新为 `[x]` 并附当前验证依据。

## A. 公共协议边界

- [x] A1 建立脱敏共享 JSON fixtures，覆盖扫码 ticket、课程、历史课件、index 0 页面与课堂消息；fixture 使用虚构 ID/资源，不含会话凭据。
- [x] A2 定义跨端来源清单 schema 与版本化错误/数据边界；桌面和 Android 各自遵守该契约。
- [x] A3 固化 endpoint、请求头、响应归一化及图片 URL 主机校验；处理字符串 ID、index 0 和未知协议形态。

## B. 桌面共用实现（Windows + macOS）

- [x] B1 增加 Electron safeStorage 加密会话与应用内扫码登录服务；按主机校验并显示雨课堂返回的 QR 图片，兼容 `image/jpg`，支持状态轮询、过期、取消、状态校验和退出。
- [x] B2 接入课程、历史课堂、课件页接口；凭据只发往标准站点，签名资源请求不携带 Cookie。
- [x] B3 实现历史图片下载、PDF 构建、标准项目创建和来源清单；新增页沿用阅读器现有的文本/OCR 能力。
- [x] B4 实现签到副作用告知、实时握手、消息归一化、去重、指数退避重连和停止采集队列。
- [x] B5 追加 PDF/空笔记页/来源清单并回滚失败写入；刷新阅读器时保留页码、笔记草稿与缩放锚点。
- [x] B6 接入 pagenote 文件侧栏和 Electron 原生文件菜单，提供登录、课件选择、导入和采集状态面板。
- [x] B7 新增桌面服务模块并沿用 Electron 现有 `desktop/**` 打包规则；只构建 macOS arm64。
- [x] B8 独立发现正在进行的课堂，不依赖历史活动列表；按 `status=1` 展示，要求全部已提供的课程/课堂 ID 一致后才关联。Node 矛盾 ID 与空选项保护测试通过；真实响应待账号验证。
- [x] B9 课堂重连按最后成功写入的游标回放缺失 timeline；失败页进入待处理队列并可显式重试，停止后保留队列并阻止切换课堂/退出账号。Node fake-WebSocket 服务测试覆盖首连、跨课件补采、写入失败、停止后重试。

## C. Android 原生实现

- [x] C1 新增 `feature-yuketang` Kotlin 模块、原生 OkHttp 协议客户端与 URL 主机校验。
- [x] C2 实现原生扫码界面及 Keystore AES-GCM 会话加密；下载并显示服务端 QR 图片而非重编码图片 URL，支持 `image/jpg`、过期、取消、退出和启动时会话校验。
- [x] C3 实现课程/历史课件选择、逐页下载和 SAF 标准项目导入。
- [x] C4 使用 PDFBox Android 追加页面，保存来源清单、图片和空笔记页；失败时回滚 PDF/笔记。
- [x] C5 接入课堂状态机、指数退避 WebSocket 重连、幂等写入与停止采集；OCR 继续由阅读器的现有离线页面功能按页触发。
- [x] C6 接入 Android 文件侧栏；追加后按阅读检查点重开 PDF，保留页码、草稿、批注和笔迹状态。
- [x] C7 独立发现正在进行的课堂，按共享 fixture 将 ID 转为字符串并过滤非活动项；所有已提供的课程/课堂 ID 必须一致，Compose 入口和 API 34 debug 包构建通过，真实课堂列表待账号验证。
- [x] C8 Android 消费共享 reconnect timeline fixture，按游标补采，保留失败事件并提供显式重试与可能缺页状态；停止后保留队列并阻止切换课堂/退出账号；JVM 单元测试和 debug 包构建通过。

## D. 集成与交付

- [ ] D1 汇总 Windows、Mac arm64、Android API 34 的精简验证记录，并区分模拟协议与真实账号/课堂证据。
- [ ] D2 已刷新 `release/mac-arm64/pagenote.app`，包内 `app.asar` 时间戳晚于源码；需解锁后正常退出旧实例并冷启动验证本次恢复/重试 UI。Android API 34 arm64 debug APK 编译通过。Windows 包仍待 CI/Windows 主机，未声称已验证。
- [x] D3 更新 Unreleased changelog 与 Android 雨课堂模块第三方协议/许可证清单；公开 release 尚未发布。
- [ ] D4 提交实现分支，合并个人仓库 main，推送并创建 laihong 上游 PR。
