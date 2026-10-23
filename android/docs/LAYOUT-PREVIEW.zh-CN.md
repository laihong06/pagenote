# 安卓布局预览版

这是供体验和调试的独立构建，不替换已经发布的 Android beta.5，也不读取其私有应用数据。

- 安装标识：`io.pagenote.android.beta.preview`。
- 构建命令：在 `android` 目录执行 `./gradlew :app:assemblePreview`。
- Windows Intel/AMD 模拟器使用 `x86_64` APK；常见 ARM64 平板使用 `arm64-v8a` APK。
- Android 最低版本为 14（API 34）。

预览版收拢原有常驻侧栏。项目、选择、手形、书写、编辑、AI、更多共用顶部工具栏；书写工具按需展开；笔记默认显示渲染结果，点击编辑后修改。底层仍使用现有项目、保存、撤销和 AI 操作。尚未实现的 Office 编辑不再常驻展示。

验收重点：打开项目、左右对照、切页、文字选择与 Tag、手形移动、手写与擦除、笔记编辑及保存、横竖屏切换。模拟器可以检查这些交互和排版，但不能证明真实手写笔的压感、倾斜、侧键和延迟表现。

本地环境可将 Android Studio、SDK、AVD、Gradle 缓存分别设置到非系统盘。Android 官方支持通过 `ANDROID_HOME`、`ANDROID_AVD_HOME`、`ANDROID_USER_HOME`、`GRADLE_USER_HOME` 和 Studio 自定义属性指定目录。不要把本机绝对路径写入公共项目配置。

本地验证：预览 APK 构建成功，Android 14 x86_64 平板模拟器的五项工具栏测试通过；已检查 PDF/笔记切页、Mermaid、公式、代码高亮与笔迹保存。应用 JVM 测试为 342/345 通过，三项 Windows 主机 DataStore 重命名失败仍待处理，见 `KNOWN_ISSUES.md`。ARM64 包已构建，尚未进行 ARM64 真机验收；公开下载入口保持既有发布版本。

对预览构建执行仪器测试时，使用 `-PpagenoteTestBuildType=preview :app:connectedPreviewAndroidTest`。默认仪器测试仍针对 debug 构建。
