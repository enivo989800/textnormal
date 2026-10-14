# 文字卡匣（Text Caddy）Android 项目

一款以触摸机械卡片浏览为核心的本地文字收纳 APP。项目采用原生 Android Java + 自定义 Canvas，不依赖联网服务，也不引入第三方运行库。

## 已实现的首版功能

- 左侧文字组别：全部、灵感、工作、重要、待办、收藏；可新建、重命名、换色、排序和删除自定义组别。
- 纵向卡片转轮：上下拖动、弧形位移、轻微角度与缩放、惯性减速、中央吸附；关闭弧形效果后改为直线卡片切换。
- 机械声音：包含本地合成的低音量旋转声、经过定位点的轻响、最终定位声，可独立关闭和调节音量。
- 文字记录：新建、粘贴剪贴板、编辑标题与正文、调整组别和卡片颜色、完整阅读、复制全文、分享、收藏、置顶、移动组别、删除。
- 搜索：按标题或正文全局搜索。
- 本机保存：记录保存在应用私有 SharedPreferences 中，不需要注册或网络权限。
- 备份：导出与导入 JSON 文件。导入会替换当前数据，应用内会先提示用户。
- 可选触觉反馈和独立动画设置。

## 在 Android Studio 中构建

1. 在安装了 Android Studio 的电脑上打开本文件夹（`text-caddy-android`）。
2. 使用 JDK 17；SDK Manager 安装 Android SDK Platform 35 和 Android Build Tools。
3. 等待 Android Studio 完成 Gradle 插件同步。
4. 选择 **Build > Build APK(s)**，或在终端执行 `gradle :app:assembleDebug`。
5. 调试 APK 的预期位置为 `app/build/outputs/apk/debug/app-debug.apk`。

项目当前使用 Android Gradle Plugin 8.7.3、compileSdk 35、targetSdk 34、minSdk 23。没有添加第三方库。

## 当前交付环境说明

本项目是在没有既有源码包的情况下从零创建的。交付运行环境未安装 Android SDK、Gradle 或 Gradle Wrapper JAR，且不能解析 Google Maven 仓库域名，因此无法在该环境里产出或验证 APK。源代码项目本身已打包；不要把源代码 ZIP 误认为可直接安装的 APK。

## 使用与数据说明

- 新建卡片时可直接粘贴文字；应用不会在没有用户操作时主动读取剪贴板。
- 删除卡片后不会自动恢复；重要记录建议定期导出 JSON 备份。
- 导入 JSON 会覆盖现有组别和卡片，导入前建议先导出当前备份。
