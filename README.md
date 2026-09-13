<div align="center">
  <img src="static/tv-mark.svg" alt="Kazumi TV 几何电视标识" width="144">
  <h1>Kazumi TV</h1>
  <p>保留 Kazumi 原有风格，适配 Android TV / Google TV 的遥控器与大屏。</p>
</div>

基于 [Predidit/Kazumi](https://github.com/Predidit/Kazumi) 的非官方 TV fork，源码沿用 GPL-3.0。

**当前版本：Preview 5（2026-09-13），公开测试版，不是稳定版。**
本版来自 A 线 `codex/tv-phase1-hardening`；未包含 B 线后续改动，不提供手机到电视接力承诺。

[下载 Preview 5](https://github.com/znbsf/Kazumi/releases/tag/v2.3.1-tv-preview.5) ·
[反馈问题](https://github.com/znbsf/Kazumi/issues) ·
[更新说明](docs/TV_PREVIEW_5.md) · [详细验证记录](docs/TV_PHASE1_DELIVERY.md)

## 下载与安装

| 设备 | APK |
| --- | --- |
| 32 位 ARM 电视，包括本轮小米测试机 | [armeabi-v7a](https://github.com/znbsf/Kazumi/releases/download/v2.3.1-tv-preview.5/Kazumi-TV-2.3.1-tv-preview.5-armeabi-v7a.apk) |
| 64 位 ARM Android 系统 | [arm64-v8a](https://github.com/znbsf/Kazumi/releases/download/v2.3.1-tv-preview.5/Kazumi-TV-2.3.1-tv-preview.5-arm64-v8a.apk) |
| x86_64 模拟器 / 设备 | [x86_64](https://github.com/znbsf/Kazumi/releases/download/v2.3.1-tv-preview.5/Kazumi-TV-2.3.1-tv-preview.5-x86_64.apk) |

版本名 `2.3.1-tv-preview.5`，独立包名 `com.znbsf.kazumi.tv`，可与手机版共存。
以 Android 系统实际支持的 ABI 选包，不能只按芯片型号判断；三种包不需要都装。
同签名旧 TV 版可覆盖安装保留数据。发行页提供 `SHA256SUMS.txt`；源码压缩包不是 APK。

## 本次更新

- 分类栏增加独立底色，左右移动焦点时自动滚动，完整显示选中的分类；节目编号角标改为半透明。
- TV 首页按可用高度展示两排完整海报与标题；标题最多两行，超长省略。移除遮挡标题的首页悬浮按钮。
- 分类内容使用内存缓存：有效期 10 分钟、最多 8 类、每类最多 240 条。切回已浏览分类可复用结果。
  顶部分类名称本来就是本地常量。此缓存不跨应用重启，也未新增图片磁盘队列。
- 低内存设置统一保留“跟随网络 / 始终开启 / 始终关闭”。仅 TV 初始默认“始终开启”，已有选择优先。
- 设置内容区可用左键回分类栏，右键进入内容，修复部分设置之间无法导航的问题。
- 播放线路菜单打开后自动聚焦当前线路；上下移动、确认选择、关闭恢复焦点，并明确显示焦点边框。
- 测试包未配置评论镜像凭据时直接提示原因，不发送注定失败的评论请求，也不自动切换网络路径。
- 吸收上游至 `2624e0c0` 的 UI 基础，包括设置卡片和初始化流程，并保留 A 线焦点与播放进度修复。

## 评论、弹幕与验证边界

评论镜像需要构建时注入 `KAZUMI_APPID` 和 `KAZUMI_KEY`，它们是接口凭据，不是 APK 签名证书。
本次 APK 未包含这些凭据，分集评论显示“测试包未配置评论镜像凭据”。播放和选集不受影响。
手动关闭 Bangumi 镜像后仍可尝试官方接口，但本轮电视直连超时，不能承诺评论可用。
本版也不包含在线弹幕私有凭据，不把示例弹幕当成真实在线结果。

- 发布前全量 Flutter 测试 331 项通过。分类、设置及两排布局已有小米 Android 9 真机短程验证。
- 播放线路菜单真机确认能向下聚焦线路 2；上下、确认和焦点恢复另有组件测试。
- 自动测试和 ADB 键码不等于实体遥控器全部按键、全部设备均已验收。
- 没有全机型、全部来源、4K/HDR、长期音画同步或内存稳定性验收结论。
- 部分来源验证码兼容性、次级页面焦点和播放器恢复边界仍需继续验证。

## 遥控操作

方向键移动焦点，OK 确认；返回退出当前层级。首页数字键按当前分类节目编号定位，编号不是永久频道号。
设置页左右切换分类与内容区域；播放线路菜单上下选择，OK 生效。
保留原有颜色和卡片风格，本次不重做整套 TV 交互。

## 开发与构建

使用与仓库兼容的 Flutter / Android SDK。TV 与 mobile 为独立 flavor。

```sh
flutter pub get
flutter test
flutter analyze
flutter build apk --flavor tv --release --target-platform android-arm,android-arm64,android-x64 --split-per-abi --build-number 20312 --build-name 2.3.1-tv-preview.5
```

发行 APK 使用项目配置的签名；重新构建须使用自己的安全签名配置。不要提交密钥或私有接口凭据。
普通贡献 PR 与本 fork 的完整 TV 开发分支分开整理；本次未向上游提交 PR。

## 历史记录

[Preview 4](docs/TV_PREVIEW_4.md) · [A 线交付记录](docs/TV_PHASE1_DELIVERY.md) ·
[首页滚动回归](docs/TV_HOME_SCROLL_REGRESSION.md) · [路线图](docs/TV_ROADMAP.md)

历史文档中的冻结 SHA、旧测试数量、旧包与待办属于当时快照，以本版说明为准。

## 上游、许可证与资源署名

感谢 [Predidit/Kazumi](https://github.com/Predidit/Kazumi) 及其
[贡献者](https://github.com/Predidit/Kazumi/graphs/contributors) 提供项目基础。
本 fork 继续遵守 [GPL-3.0](LICENSE)，对应发行源码保留在各 Release 标签中；
上游官网、社区、奖项、赞助和 Windows 签名服务不代表本 fork 获得相同支持。

TV APK 使用本 fork 原创几何电视标识（GPL-3.0），不打包上游人物图标。
源码保留的上游图标来自 [Yuquanaaa](https://www.pixiv.net/users/66219277) 的
[Pixiv 作品](https://www.pixiv.net/artworks/116666979)，版权属于原作者，不能据上游的使用许可推定
本 fork 或其他分发者也获得授权。字体 Mi Sans 由 Xiaomi 开发并拥有相关权利，沿用其资源许可。

感谢继承使用的开源项目与服务，包括 media-kit / libmpv、XpathSelector、Hive、avbuild、
Bangumi、弹弹 Play、Anime4K、Syncplay 与 trace.moe。依赖或代码的存在不代表相关功能
已经在 TV 预览版完成验收。软件许可不授予第三方视频、评论、图片或台标的内容使用权；
请遵守相应服务条款和资源许可，软件按 [LICENSE](LICENSE) 所述提供。
