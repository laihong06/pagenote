# Android 图片来源与复现

[平台入口](../README.md) · [教程目录](README.md) · [逐图来源和 SHA-256](images/current/provenance.json)

本轮 30 张图片采于 2026-10-30，全部来自实际应用和系统窗口，整屏原图，无拼接、缩放或界面修饰。自编两页 PDF 与蚁群算法笔记不含私人材料；AI 配置使用无效示例域名和演示模型名，没有密钥或在线请求。

| 项目 | 实际环境 |
| --- | --- |
| 系统与设备 | Android 14／API 34；Pixel_Tablet_API34 AVD；ARM64；设备模型 sdk_gphone64_arm64 |
| 应用 | io.pagenote.android.debug；1.0.0-beta.6-dev.1；本地 Debug APK，非公开下载 |
| 运行源码 | 6388245cbc919ca7b1335fbf59f36abc6cf5a5be |
| 工作区 | 文档、采集辅助与构建生成文件有修改；应用运行源码无改动 |
| 尺寸 | 横屏 2560×1600；320dpi（2倍，逻辑1280×800） |
| 主题和语言 | 应用浅色、zh-CN；系统窗口／悬浮服务保留英文文案；22–23 展示夜览滤镜 |
| 采集 | Instrumentation 的系统整屏截图；包含状态栏、导航栏、键盘和原生弹窗 |

## 在专用模拟器复现

以下辅助脚本会重设 Debug 应用的阅读／演示模型配置、绑定测试书架并写入合成项目。只能用于无个人数据的专用 API 34 模拟器。它在文档目录保存，默认不进入正式测试集；没有为截图修改运行界面。

1. 启动 ARM64 的 Android 14／API 34 平板模拟器，先确认 `adb devices` 只有目标设备。
2. 从项目根目录临时复制辅助文件到 Android 仪器测试目录：

```sh
cp docs/android/guide/capture/GuideEvidenceCapture.kt android/app/src/androidTest/java/io/pagenote/android/GuideEvidenceCapture.kt
cd android
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.pagenote.android.GuideEvidenceCapture --console=plain
```

3. 三个方法分别采集阅读与工具、键盘与闪卡、悬浮窗。可在 class 参数后加 `#captureReaderAndStudyModules`、`#captureEditingAndReview` 或 `#captureFloatingWindows` 单独运行。它们是采集流程，只断言指定状态，不能代替完整功能回归。
4. 从项目根目录取出本轮图片，人工核对后再更新来源记录。设备目录可能保留以前的图，应使用干净设备或先核对文件时间。

```sh
adb pull /sdcard/Download/pagenote-review-evidence/. docs/android/guide/images/current/
rm android/app/src/androidTest/java/io/pagenote/android/GuideEvidenceCapture.kt
```

5. 悬浮场景通过专用设备的 appops 授权显示在其他应用上层，并写入自编剪贴板短句。使用后可撤销该测试授权：

```sh
adb shell appops set io.pagenote.android.debug SYSTEM_ALERT_WINDOW default
```

本轮三个流程分别完成；没有把截图数量当成功能数量。形状创建不能撤销的场景专门保留禁用菜单证据，删除对象后的撤销则独立验证。OCR／ASR 只打开配置，合并导出只打开范围窗口；没有录音、实际识别、付费 AI 请求、系统截屏授权或完整新源码回归。[完整结果](../../review/PR13-evidence-2026-10-30.md)。
