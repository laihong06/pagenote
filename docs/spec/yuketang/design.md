# 雨课堂全端接入设计

日期：2026-10-22（Asia/Shanghai）

状态：实现设计；产品实现进行中。需求见 [requirements.md](requirements.md)，协议证据和限制见 [research.md](research.md)。

## 1. 产品入口与流程

所有入口都叫“雨课堂”，放在现有“文件”菜单组中，保持桌面与 Android 的菜单分组语言一致。

- 桌面原生“文件”菜单增加“从雨课堂导入课件…”和“开始实时采集…”命令；应用侧栏的“文件”组同步加入相同命令。
- Android 自适应侧栏的“文件”组加入“雨课堂课件…”命令。平板侧栏与手机抽屉共用同一命令模型。
- “雨课堂课件”面板统一承载登录状态、课程/历史课件选择、当前进行中课堂选择、历史导入和实时采集。当前课堂列表与历史课件分开请求、分开呈现；课堂返回的所有可用课程/课堂标识必须关联到同一账号课程，缺失或矛盾时明确显示未关联状态并禁用采集。活动采集期间，在面板及阅读器标题栏显示连接状态、新页数和停止按钮。
- 登录流程在 pagenote 的原生面板显示雨课堂扫码二维码、等待确认、成功、过期和失败状态。扫码由用户使用雨课堂/微信官方客户端确认；pagenote 不打开系统浏览器、不嵌入网页、不读取 Chrome/Edge 登录态，也不要求粘贴 Cookie。
- 实时采集单独列为明确动作。启动前显示“进入课堂可能记录签到”；只有用户确认并选择一个目标项目后才调用课堂进入接口。

## 2. 模块边界

### 桌面：Windows 与 macOS

- 在 `desktop/` 放置 Rain Classroom 协议服务与安全存储适配器；Windows 和 macOS 共用请求归一化、导入、实时队列及标准项目更新逻辑。
- Electron 主进程提供操作系统加密能力（`safeStorage`），协议服务只接收加密存储接口，不把明文 Cookie 交给网页渲染层。
- `server.mjs` 暴露本机 API：登录开始/状态/取消/退出、课程与历史课件、导入、实时采集开始/停止/状态。登录票据和 Cookie 只在主进程服务内存及加密本机存储流转。
- Electron 原生“文件”菜单与 Web 侧栏入口都调用同一组窗口命令；Web UI 通过现有本机 API 打开导入/采集面板。
- 桌面新增协议模块必须加入 Electron `build.files`，确保 Windows 与 arm64 macOS 安装包均带有实现。

### Android

- 新增原生 Kotlin `feature-yuketang` 模块，使用现有 OkHttp 进行 HTTPS 与 WebSocket；不通过通用 WebView 登录或系统浏览器。
- 二维码面板、课程选择、导入与采集状态使用 Compose。入口加入现有 `ReaderMenuGroup.FILE` 命令模型。
- 会话用 Android Keystore 派生/持有 AES-GCM 密钥，并以认证加密形式写入应用私有存储。退出账号时清除密文、内存 Cookie 与所有课堂连接。
- 项目文档由 SAF 访问；下载先落入应用私有暂存区，完成 PDF 校验后再写入项目。失败时保留可恢复的原项目文件。

## 3. 协议适配与会话状态

首版固定标准站点 `https://www.yuketang.cn`。服务端点集中定义在各端协议适配器，UI 和阅读器不得直接拼接雨课堂 URL。

| 领域方法 | 标准站点协议线索 | 结果 |
| --- | --- | --- |
| 开始扫码 | `wss://www.yuketang.cn/wsapp/`，发送 `requestlogin` / `qrcode` 消息 | 应用内短时请求 ID；`ticket` 是受限来源上的二维码图片资源，不是 QR 内容 URL |
| 完成登录 | 收到扫码授权消息后 `POST /pc/web_login` | Cookie；随后用 `/api/v3/user/basic-info` 校验 |
| 课程列表 | `GET /v2/api/web/courses/list?identity=2` | 课程、课堂标识与可显示名称 |
| 当前课堂 | `GET /api/v3/classroom/on-lesson` | 读取 `data.onLessonClassrooms` 中 `status=1` 的正在上课项；结构来自公开逆向实现，需真实账号验证 |
| 历史记录 | `GET /v2/api/web/logs/learn/{classroomId}` | 用户可访问的课件活动 |
| 获取课件页 | `GET /api/v3/lesson/presentation/fetch?presentation_id=...` | 有序页面 ID 与图片地址 |
| 进入正在进行的课堂 | `POST /api/v3/lesson/checkin` | 课堂 Token / identity ID；该请求可能登记签到 |
| 观察翻页 | `wss://www.yuketang.cn/wsapp/`，发送 `hello` | 统一化课件切换与页号事件 |

桌面和 Android 都下载并在应用面板内显示 `ticket` 返回的二维码图片；仅允许 HTTPS 雨课堂域名或精确的 `mp.weixin.qq.com/cgi-bin/showqrcode` 端点，响应大小和图片类型均受限，并把上游 `image/jpg` 别名归一化为标准 `image/jpeg`。票据 URL 不传给渲染层、不写日志或项目文件。实现时只把用户选中的课件/课堂交给协议适配器。会话校验失败返回 `AUTH_REQUIRED`，权限拒绝返回 `FORBIDDEN`，网络错误返回 `NETWORK`，响应结构不识别返回 `PROTOCOL_CHANGED`；不得把异常压成空列表。

协议 ID 一律按字符串处理，避免 64 位 ID 精度丢失。页号 0 是合法上游值，不得以真假值判断缺失。消息处理先归一化 `showpresentation`、`presentationupdated`、`presentationcreated`、`showfinished`、`slidenav` 以及初始 timeline，再进入同一页面队列。

## 4. 跨端数据契约

各端使用同名脱敏 JSON 样本校验归一化行为，产品代码仍按平台惯例实现。建议新增 `docs/spec/yuketang/fixtures/`，样本不得包含账号、Cookie、二维码票据、课件签名 URL 或课堂 Token。

每个导入项目在 `src/yuketang-source.json` 写入如下版本化来源清单：

```json
{
  "format": "pagenote-yuketang-source",
  "version": 1,
  "provider": "www.yuketang.cn",
  "courseId": "string",
  "classroomId": "string",
  "lessonId": "string",
  "presentationId": "string",
  "pages": [
    {
      "pageId": "string",
      "sourceIndex": 0,
      "projectPage": 1,
      "sha256": "hex",
      "asset": "src/assets/yuketang/<stable-name>.jpg",
      "firstSeenAt": "ISO-8601"
    }
  ]
}
```

清单只写稳定来源标识、源页号、项目页号、内容散列、本地资源相对路径和时间；不写 QR ticket、Cookie、Token、签名 URL、用户隐私字段或学习统计。项目 PDF 使用原始页序；实时新页按首次成功采集顺序追加，并在清单中保留原课件页号。项目 `src/right.md` 页数必须与 PDF 页数同步，新增笔记页为空。

页面去重键为 `provider + classroomId + presentationId + pageId + sha256`。同 ID 同散列视为回翻或重复消息；内容散列变化时追加新的来源版本，不覆盖已有项目页、笔记、批注或笔迹。课件结构映射不确定时停止自动更新并给出可读状态。

## 5. 历史导入和实时队列

### 历史导入

1. 列出已登录账号的课程与历史活动，用户选择一个课件和目标目录/项目。
2. 读取课件清单，保留每个有效页面的源页序；移除同页重复和懒加载占位图。
3. 校验图片主机为 HTTPS 且落在允许的雨课堂资源域名；签名 URL 仅在当前下载请求内存中使用。跨域资源请求不携带雨课堂 Cookie。
4. 顺序下载页面图片到暂存目录；限制并发和单页大小；确认每张图片完整可解码后再构建 PDF。
5. 创建标准 `project.pagenote` 项目、按页空笔记、页面图片资源及来源清单。只在所有页成功写入并 PDF 校验通过后发布最终 manifest；失败时清理临时项目或标为可恢复的部分导入。

### 实时采集

- 用户确认进入课堂后，执行课堂认证并连接 WebSocket。客户端不加入作答、投票、考试、签到以外的自动交互。
- 每个新页先进入串行项目写入队列：资源下载与图片验证 -> 页面散列 -> 去重 -> 原子生成新 PDF -> 保存图片和来源清单 -> 添加空笔记页 -> 通知当前阅读器刷新页数。
- OCR 在页面落盘后异步入队，独立于 WebSocket 和项目串行写入；失败时保留原始图片，显示重试状态，不能二次插入页面。
- 用户翻页或重复消息不自动控制阅读器当前页。实时采集和自动跟页是独立偏好；首版默认为不自动跟页。
- 断线后显示重连状态并退避重连。以课堂会话的最后一个成功写入位置为游标，重连后从服务端 timeline 回放其后的页面；首次连接只采当前可见页，不把此前整段课堂历史误当作新页。
- 若服务端 timeline 不再包含最后游标，只补采当前页并持续显示“可能有缺页”，不猜测或静默补造页面。待写入事件按课堂、课件和页序保存在内存队列，写入失败保留队列，面板提供显式重试；重复重试依靠来源清单去重。停止采集后失败队列继续保留，补齐前不允许启动新课堂或退出账号。
- 停止按钮关闭课堂 WebSocket、等待已接收事件完成原子写入；失败页面仍可在已停止状态重试。结束队列后项目保持离线可用。

## 6. PDF 与编辑状态保护

- 桌面使用 `pdf-lib` 在同一项目 PDF 末尾追加页面，原有 PDF 页内容保持不变。Android 使用已有 PDFBox Android 写入已有 PDF，并以原子暂存结果替换 SAF 文件。
- 来源清单的每页散列用于稳定缓存身份。对雨课堂增量 PDF，不因新增末尾页而令所有旧页 OCR/页面准备缓存失效。
- 每次追加前，桌面先提交当前笔记编辑，再以 revision 校验写回新空白页；Android 先保存当前页草稿和笔迹并记录 reader checkpoint。任何并发修改冲突都停止追加并保留下载资源待重试。
- 刷新阅读器保留项目 ID、当前页、视口、笔迹、批注、未保存草稿和选区；新页不触发自动跳页。
- PDF 写入、笔记追加、来源清单更新构成一个可恢复事务：先生成完整临时文件，再替换源 PDF；更新清单时以去重键重复检查。启动恢复时清理孤立临时文件，不删除已提交资源。

## 7. 测试与完成证据

- 单一共享 fixture 契约覆盖扫码事件、课程/课件列表、32 位以上 ID、页号 0、重复/回翻、课件切换、无效图像、认证失效和实时消息归一化。
- 使用本机模拟 HTTPS/WebSocket 仅验证状态机、页面队列、去重、失败重试、增量 PDF 和笔记/草稿保护，不把模拟结果当作雨课堂真实联通证据。
- 产品验证按需求中的精简矩阵：Mac arm64 打包应用真实扫码/历史导入/一堂真实课程；Windows 当前应用最小闭环；Android API 34 平板 AVD 最小闭环与 SAF 保存。
- 若真实课堂暂不可用，代码和模拟覆盖可报告，但 R4 的真实联通仍标为未验证，不能声称已完成。
