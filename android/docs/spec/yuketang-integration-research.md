# Android 雨课堂接入补充

更新日期：2026-10-22（Asia/Shanghai）

状态：原生接入代码已实现。API 34 平板 AVD 已验证 82 页历史导入，并经用户明确授权验证中途加入真实课堂补采 18 页；观察窗口未收到后续真实翻页。多页追加/去重/笔记保留通过本地 SAF 回归，详见[实时采集新建项目 SPEC](features/yuketang-live-project.md)。

## 公共范围

雨课堂接入覆盖 Windows、macOS、Android。候选开源项目、协议线索、来源契约、更新规则与公共验收统一维护在 [全端调研](../../../docs/spec/yuketang/research.md)。本文件只记录 Android 平台差异。

必须满足 [公共需求](../../../docs/spec/yuketang/requirements.md) 的应用内登录与逐页实时采集要求。

## Android 约束

- 原生 Kotlin / Android 14，重点适配小米平板 5 Pro；日常运行验收使用 API 34 平板 AVD，不扩展其他 Android 设备矩阵。
- 实现与桌面相同的数据契约，使用同一批脱敏接口样本验证。远端 ID 保存为字符串，页面 ID / 页序 / 版本与桌面保持一致。
- 复用 `ProjectCreator` 创建标准项目，通过现有 SAF 目录授权写入本地 PDF、笔记与非敏感来源清单；不将外部临时 URI 或登录凭据写入项目。
- 登录为应用内必备流程，由应用自身页面承接雨课堂官方认证与二维码确认状态，不依赖外部浏览器、插件或手工 Cookie。登录凭据保留在应用私有安全存储并由 Keystore 保护；阅读器与手写仍使用原生实现。
- 下载和 OCR 采用受限并发，支持取消、重试、进度和缓存复用。后台任务生命周期与系统限制在 Android 实现设计中明确。
- 当前课堂通过标准站点 `GET /api/v3/classroom/on-lesson` 获取；该接口缺少 `status` 的项仍为当前课堂，显式非进行中状态不展示。课程匹配优先使用唯一班级号；班级号缺失时才按唯一课程号匹配。
- 首次进入课堂回放全部已发布 timeline 页面，不再只取最后一页；兼容 `si` 页序和 `sid` 页面 ID，按真实课件元数据定位页面，不猜测未展示页。WebSocket 重连按最后成功写入的课件/页序或页面 ID 游标回放；未匹配游标时只补当前页并标记“可能缺页”。写入失败页在停止后仍保留在内存队列，雨课堂弹窗提供显式重试；补齐前阻止切换课堂和退出账号。Android JVM 测试消费公共 timeline fixture。
- 实时跟页保持可开关；进入课堂的潜在签到副作用、换版、笔迹与草稿保护遵循公共契约。
- 页面准备、OCR 和 AI 复用现有 Android 模块；不通过嵌入完整 Python 运行时复用桌面代码。

## 精简验收

- JVM 与桌面 Node 测试消费同一当前课堂脱敏样本，验证过滤进行中状态并将远端 ID 规范为字符串；JVM 还覆盖 timeline 游标回放与缺页状态。桌面 fake-WebSocket 测试另覆盖服务编排与失败重试。
- 应用内完成一次官方登录、取消或过期处理与正常退出账号；验收不借用系统浏览器登录状态。
- API 34 平板 AVD 验证一条下载 -> 项目 -> PDF -> OCR -> AI 流程，覆盖重试和已有笔记保留。
- 与桌面互开同一示例项目，核对来源标识、页面与笔记对应；不将项目互开宣称为跨设备实时同步。
- API 34 debug 包构建、雨课堂 JVM 协议测试和应用内面板启动检查已通过。标准站点实际历史导入、可见课件和实时课堂分别记录，不能以面板启动替代接口验证。

## 历史导入 401 修复

- 用户截图确认失败端点为 `/api/v3/lesson/presentation/fetch`。真实历史活动只提供 `type / id / courseware_id`，旧实现将 `courseware_id` 当成 `presentation_id`，且丢失课堂身份。
- 官网 1.2.334 的历史列表将 `type=3/14` 的 `courseware_id` 作为 `lessonId`，`id` 作为活动 ID。Android 按该语义解析，不把测验、通知等活动当成课件。
- 历史课堂先读取 `GET /api/v3/classroom-report/student/lesson-info?lesson_id=...` 的 `presentationIds`，再逐份读取 `GET /api/v3/classroom-report/student/ppt?lesson_id=...&presentationId=...` 的 `slideList`。参数 `presentationId` 为驼峰写法；这条历史导入流程不调用 checkin 或课堂 WebSocket。
- 按课件顺序与源页序生成 PDF；只导入明确标记可见的页面，缩略图不进入页清单。已开放页缺少必要数据时停止导入，不静默跳页。来源清单的顶层课件 ID 使用第一份真实课件 ID，各页分别保留实际课件与页面 ID，不输出历史列表的内部占位 ID。
- JVM 回归覆盖上述 ID 分离、报告请求编排、页序、可见性、空课件、不完整页及 v2 `errcode` 失败。应用仪器测试 `RainClassroomImportSmokeTest` 仅在显式传入 `rainLiveSmoke=true` 后使用当前账号，通过 `rainCourseIndex` / `rainLessonIndex` 指定列表索引，验证完整 PDF/笔记/来源清单一致性，并清理测试项目。独立 SAF 仪器回归覆盖多份课件的真实 ID 保存。
- 已登录的 AVD 使用 `adb install -r` 和 `am instrument` 执行定向验证；不对保留用户登录态的 AVD 使用会在结束时卸载被测 APK 的 Gradle `connectedDebugAndroidTest`。

### 2026-10-22 实际验证记录

- 用户在应用内重新扫码登录；课程列表和课堂报告均使用该登录态读取，没有借用外部浏览器。
- 第一个课程报告的 `presentationIds` 为空，明确提示没有已开放课件，不创建空项目，也不退回实时课堂接口。
- 第二个课程的首个课堂有两份课件（51 + 31 页）。API 34 平板 AVD 完整下载并生成 82 页 PDF、82 页笔记和 82 条来源记录；自动校验通过后删除测试专用 SAF 目录。没有修改用户项目，没有调用签到或课堂 WebSocket。
- 最终 debug APK 的真实导入与多课件来源测试均通过。复测曾在读取用户信息前出现一次 TLS 握手断开；未改代码原样重试后通过，保留网络故障记录，不关闭证书校验。
- 后续已按用户明确授权进入真实课堂，接收首张课件并创建新项目，PDF/笔记/来源各 1 页；停止连接并清理测试项目。30 秒观察窗口没有新翻页事件，真实连续逐页采集仍未完整验收。
- 再次按中途加入流程验证后，已补采该课堂时间线中的 18 页真实课件，PDF/笔记/来源均为 18；不将此次历史回放宣称为新发生的实时翻页验证。

官网协议证据：[历史列表](https://fe-static-yuketang.yuketang.cn/fe/static/web/1.2.334/js/16621.49827055.js)、[报告入口](https://fe-static-yuketang.yuketang.cn/fe/static/web/1.2.334/js/2453.2905e1dc.js)、[课件页请求](https://fe-static-yuketang.yuketang.cn/fe/static/web/1.2.334/js/6858.d20f776f.js)。公开脚本仅静态读取，未执行。
