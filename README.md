<div align=center>

<h1>Kazumi TV Fork</h1>

<img src="assets/images/logo/logo_rounded.png" width=200></img>

<a href="https://t.me/kazumi_app"><img src="https://img.shields.io/badge/Telegram-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white"></img></a>

<img src="https://img.shields.io/badge/Flutter-03A9F4?style=for-the-badge&logo=flutter&logoColor=white"></img>
<img src="https://img.shields.io/badge/Dart-00B4AB?style=for-the-badge&logo=Dart&logoColor=white"></img>
<img src="https://img.shields.io/badge/Android_TV-3DDC84?style=for-the-badge&logo=android&logoColor=white"></img>

<a href="https://trendshift.io/repositories/11432"><img src="https://trendshift.io/api/badge/trendshift/repositories/11432/yearly?language=Dart"></img></a>
<a href="https://hellogithub.com/repository/Predidit/Kazumi" target="_blank"><img src="https://abroad.hellogithub.com/v1/widgets/recommend.svg?rid=68d824ea55ee4b07aba6fe1dd61ac939&claim_uid=J9Qu6aDd8LT1nU0"/></img></a>

<p>使用 Flutter 开发的基于自定义规则的番剧采集与在线观看程序。使用最多五行基于 <code>Xpath</code> 语法的选择器构建自己的规则。支持规则导入与规则分享。支持基于 <code>Anime4K</code> 的实时超分辨率。绝赞开发中 (～￣▽￣)～</p>
</div>

> [!IMPORTANT]
> 这是基于 [Predidit/Kazumi](https://github.com/Predidit/Kazumi) 开发的非官方、开源
> Android TV 分支。当前默认分支包含 TV 第一阶段能力，同时保留独立的手机版构建；
> 手机向电视接力播放属于第二阶段，尚未在这里标记为完成。

## 这个 Fork 做了什么

目标是在不改变 Kazumi 原有视觉语言、规则系统、弹幕和 libmpv/media-kit 播放内核的
前提下，让它可以作为真正的 Android TV / Google TV 应用使用。

| 范围 | 当前能力 |
| --- | --- |
| 独立安装 | `mobile` 与 `tv` 两个 product flavor；TV 包名为 `com.znbsf.kazumi.tv`，可与手机版共存 |
| TV 入口 | Leanback launcher、TV banner、非必需触摸屏声明和横屏窗口 |
| 十英尺界面 | 首页、时间表、追番、历史、设置和播放页支持 D-pad 焦点与确认键，沿用原有配色和卡片样式 |
| 初始化与来源 | 保留首次初始化、规则目录、规则安装、详情、播放源和选集流程 |
| 播放器 | 播放/暂停、快进快退、上下集、选集、收藏、弹幕、详情、返回和退出均可由遥控器操作 |
| 播放信息 | `INFO` 可查看实际解码通路、视频输出、GPU context、编码、像素格式、帧率、缓存和丢帧 |
| 硬件解码 | Android 默认使用 `auto-safe`；支持 MediaCodec、`gpu/gpu-next` 以及手动选择 `mediacodec_embed` |
| 手机兼容 | 手机版继续使用原包名和手机布局；TV 专属诊断、遥控器帮助和焦点行为不会改变手机界面 |

更完整的阶段边界、验证记录和第二阶段计划见
[TV 路线图](docs/TV_ROADMAP.md)；采用的开源项目设计及取舍见
[Android TV 开源播放器参考](docs/TV_OPEN_SOURCE_REFERENCES.md)。

## TV 遥控器键位

| 遥控器按键 | 播放器动作 |
| --- | --- |
| D-pad / OK | 控制层隐藏时显示控制层；显示后进行焦点导航和确认 |
| Play / Pause | 播放、暂停或切换状态 |
| Fast Forward / Rewind | 快进、快退 |
| Channel Up / Down | 下一集、上一集 |
| EPG / Guide / Top Menu / 黄键 | 打开选集面板 |
| CC / Captions / Audio Track / 红键 | 切换弹幕；不切换内嵌字幕轨 |
| Favorite / Bookmark / 绿键 | 标记为“在看”或取消追番 |
| INFO / 蓝键 | 打开视频详情与实时播放诊断 |
| HELP / MENU / F1 / Context Menu | 打开遥控器帮助页 |
| Back / Escape | 关闭面板或返回上一层 |
| Stop / Exit / Media Close | 退出播放器 |
| Volume / Mute | 交由 Android TV 系统处理，以兼容电视、CEC、ARC/eARC 和功放 |

部分电视会在应用收到事件前拦截 EPG、音量等系统保留键。Kazumi TV 会处理设备实际
下发给应用的标准键，同时提供黄键、Top Menu 等兼容入口。

## 硬件解码状态

“启用硬件加速”只表示允许选择硬解，并不保证每个视频都一定使用硬件单元。因此 TV
播放页通过 `INFO` 展示 mpv 的实际运行值，而不是只显示设置项：

- `hwdec-current`：当前解码通路；`no` 表示软件解码；
- `hwdec-interop`：硬解帧与视频输出的互操作方式；
- `current-vo` / `current-gpu-context`：当前渲染器和 GPU context；
- 视频编码、像素格式、估算帧率、缓存时长和两类丢帧计数。

当前 Google TV AVD 的在线播放验证读到 `mediacodec-copy + gpu-next + android`。这能
证明 MediaCodec 通路已经接入，但模拟器结果不能替代实体电视芯片上的 4K/HDR、功耗、
解码器组件和长时间播放验证。

`mediacodec_embed` 是功耗优先的手动选项，但它不支持 Anime4K 超分辨率，并会限制
部分 mpv 视频滤镜和 OSD 合成能力，因此没有被强制设为 TV 默认值。

## 构建 TV 与手机版

推荐使用 Android Studio 打开仓库根目录，通过 Device Manager 创建 Android TV 或
Google TV AVD。项目同时保留两个 Android flavor：

```bash
# TV Debug APK
flutter build apk --debug --flavor tv

# Mobile Debug APK
flutter build apk --debug --flavor mobile
```

构建产物：

- `build/app/outputs/flutter-apk/app-tv-debug.apk`
- `build/app/outputs/flutter-apk/app-mobile-debug.apk`

如果 TV AVD 无法直接访问网络，可在开发时显式复用宿主机代理：

```bash
flutter build apk --debug --flavor tv \
  --dart-define=KAZUMI_DEV_PROXY=10.0.2.2:PORT
```

该参数只用于本地开发环境，不应写死到公开发行包。

常用验证命令：

```bash
flutter test
flutter analyze --no-fatal-infos --fatal-warnings
flutter build apk --debug --flavor tv
```

## 支持平台

- Android TV / Google TV（本 Fork 的 `tv` flavor）
- Android 10 及以上
- Windows 10 及以上
- MacOS 10.15 及以上
- Linux (实验性)
- iOS 13 及以上 (需要 [侧载](https://kazumi.app/docs/misc/how-to-install-in-ios))
- HarmonyOS 5.0 及以上 (位于 [分支仓库](https://github.com/ErBWs/Kazumi/releases/latest)，需要 [侧载](https://kazumi.app/docs/misc/how-to-install-in-ohos))

## 屏幕截图

<table>
  <tr>
    <td><img alt="homepage" src="static/screenshot/img_1.png"></td>
    <td><img alt="timetable" src="static/screenshot/img_2.png"></td>
    <td><img alt="details" src="static/screenshot/img_3.png"></td>
  <tr>
  <tr>
    <td><img alt="selection-page" src="static/screenshot/img_4.png"></td>
    <td><img alt="rules-mange" src="static/screenshot/img_5.png"></td>
    <td><img alt="rules-edit" src="static/screenshot/img_6.png"></td>
  <tr>
</table>

## 功能 / 开发计划

- [X]  规则编辑器
- [X]  番剧目录
- [X]  番剧搜索
- [X]  番剧时间表
- [X]  番剧字幕
- [X]  分集播放
- [X]  视频播放器
- [X]  多视频源支持
- [X]  规则分享
- [X]  硬件加速
- [X]  高刷适配
- [X]  追番列表
- [X]  番剧弹幕
- [X]  在线更新
- [X]  历史记录
- [X]  倍速播放
- [X]  配色方案
- [X]  跨设备同步
- [X]  无线投屏 (DLNA)
- [X]  外部播放器播放
- [X]  超分辨率
- [X]  一起看
- [X]  番剧下载
- [ ]  番剧更新提醒
- [ ]  还有更多 (/・ω・＼)

## 下载

### Kazumi TV Fork

当前 TV 版以源码和 Debug 构建验证为主，暂未发布正式签名 APK。后续发行包会放在本
Fork 的 [Releases](https://github.com/znbsf/Kazumi/releases)；现在可按上面的 `tv`
flavor 命令自行构建。

### 上游 Kazumi 正式版

手机版稳定发行请通过上游
[Predidit/Kazumi Releases](https://github.com/Predidit/Kazumi/releases/latest) 下载：

<a href="https://github.com/Predidit/Kazumi/releases">
  <img src="static/svg/get_it_on_github.svg" alt="Get it on Github" width="200"/>
</a>

### Android

<a href="https://f-droid.org/packages/com.predidit.kazumi">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-zh-hans.svg"
  alt="Get it on F-Droid" width="200">
</a>

### GNU/Linux

<a href="https://flathub.org/apps/io.github.Predidit.Kazumi">
  <img src="https://flathub.org/api/badge?svg&locale=zh-Hans" alt="Get it on Flathub" width="175"/>
</a>

#### Arch Linux

可以从 [AUR](http://aur.archlinux.org) 安装。

##### AUR

```bash
[yay/paru] -S kazumi # 从源码构建
[yay/paru] -S kazumi-bin # 二进制包
```

## 贡献

欢迎向我们的 [规则仓库](https://github.com/Predidit/KazumiRules) 提交您的自定义规则。您可以自由选择是否在规则中留下您的ID。详细的规则编写教程可以参考 [规则开发文档](https://kazumi.app/docs/rules/develop-rules)

## Q&A

<details>
<summary>使用者 Q&A</summary>

#### Q: 为什么少数番剧中有广告？

A: 本项目未插入任何广告。广告来自视频源, 请不要相信广告中的任何内容, 并尽量选择没有广告的视频源观看。

#### Q: 为什么我启用超分辨率功能后播放卡顿？

A: 超分辨率功能对 GPU 性能要求较高, 如果没有在高性能独立显卡上运行 Kazumi, 尽量选择效率档而非质量档。对低分辨率视频源而非高分辨率视频源使用超分也可以降低性能消耗。

#### Q: 为什么播放视频时内存占用较高？

A: 本程序在视频播放时, 会尽可能多地缓存视频到内存, 以提供较好的观看体验。如果您的内存较为紧张, 可以在播放设置选项卡启用低内存模式, 这将限制缓存。

#### Q: 为什么少数番剧无法通过外部播放器观看？

A: 部分视频源的番剧使用了反盗链措施, 这可以被 Kazumi 解决, 但无法被外部播放器解决。

#### Q: 为什么下载的 Linux 版本缺少图标和托盘功能？

A: 使用 .deb 版本进行安装, tar.gz 版本仅为方便二次打包, 这一格式先天缺乏图标和托盘功能支持。

</details>

<details>
<summary>规则编写者 Q&A</summary>

#### Q: 为什么我的自定义规则无法实现检索？

A: 目前我们对 `Xpath` 语法的支持并不完整, 我们目前只支持以 `//` 开头的选择器。建议参照我们给出的示例规则构建自定义规则。

#### Q: 为什么我的自定义规则可以实现检索, 但不能实现观看？

A: 尝试关闭自定义规则的使用内置播放器选项, 这将尝试使用 `webview` 进行播放, 提高兼容性。但在内置播放器可用时, 建议启用内置播放器, 以获得更加流畅并带有弹幕的观看体验。

</details>

<details>
<summary>开发者 Q&A</summary>

#### Q: 我在尝试自行编译该项目, 但编译没有成功。

A: 本项目编译需要良好的网络环境, 除了由 Google 托管的 Flutter 相关依赖外, 本项目同样依赖托管在 MavenCentral/Github/SourceForge 上的资源。如果您位于中国大陆, 可能需要设置恰当的镜像地址。

</details>

## 开发

欢迎您提交 PR！在开始之前, 请阅读 [贡献指引](static/doc/CONTRIBUTING.md) 以了解我们对 PR 和 AI 参与辅助开发的规定。

## 美术资源

本项目图标来自 [Yuquanaaa](https://www.pixiv.net/users/66219277) 发表在 [Pixiv](https://www.pixiv.net/artworks/116666979) 上的作品。

此图标由其原作者 [Yuquanaaa](https://www.pixiv.net/users/66219277) 拥有版权。我们已获得原作者的授权和许可, 可以在本项目中使用这一图标。这一图标不是自由使用的, 未经原作者明确授权, 任何人不得擅自使用、复制、修改或分发这一图标。

本项目内嵌字体为 [Mi Sans](https://hyperos.mi.com/font/zh/details/sc/) 字体, 由 [Xiaomi](https://www.mi.com/index.html) 开发和拥有版权。

## 免责声明

本项目基于 GNU 通用公共许可证第 3 版（GPL-3.0）授权。我们不对其适用性、可靠性或准确性作出任何明示或暗示的保证。在法律允许的最大范围内, 作者和贡献者不承担任何因使用本软件而产生的直接、间接、偶然、特殊或后果性的损害赔偿责任。

使用本项目需遵守所在地法律法规, 不得进行任何侵犯第三方知识产权的行为。因使用本项目而产生的数据和缓存应在24小时内清除, 超出 24 小时的使用需获得相关权利人的授权。

## 隐私政策

我们不收集任何用户数据, 不使用任何遥测组件。

## 代码签名策略

提交者: [贡献者](https://github.com/Predidit/Kazumi/graphs/contributors)
审阅者: [所有者](https://github.com/Predidit)

## 赞助


| ![signpath](https://signpath.org/assets/favicon-50x50.png)                                                                                                                      | Free code signing on Windows provided by[SignPath.io](https://about.signpath.io/), certficate by [SignPath Foundation](https://signpath.org/) |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| <img src="https://kilo.ai/favicon/favicon.svg" width="50">                                                                                                                      | **Automatic PR review provided by [Kilo Code](https://kilo.ai/), sponsored by the [Kilo OSS Program](https://kilo.ai/oss)**                   |
| <a href="https://m.do.co/c/0062035db3e4"><img src="https://opensource.nyc3.cdn.digitaloceanspaces.com/attribution/assets/SVG/DO_Logo_icon_blue.svg" width="50" height="50"></a> | **Cloud infrastructure is supported by [DigitalOcean](https://m.do.co/c/0062035db3e4)**                                                       |

## 致谢

特别感谢 [XpathSelector](https://github.com/simonkimi/xpath_selector) 这个优秀的项目是本项目的基石。

特别感谢 [弹弹play](https://www.dandanplay.com/) 本项目使用了 弹弹play开放平台 以提供弹幕交互。

特别感谢 [Bangumi](https://bangumi.tv/) 本项目使用了 Bangumi 开放 API 以提供番剧元数据。

特别感谢 [Anime4K](https://github.com/bloc97/Anime4K) 本项目使用 Anime4K 进行实时超分。

特别感谢 [SyncPlay](https://github.com/Syncplay/syncplay) 本项目使用 SyncPlay 协议并通过 SyncPlay 公共服务器实现一起看功能。

特别感谢 [所有贡献者](https://github.com/Predidit/Kazumi/graphs/contributors) 本项目因为你们变得更好。

特别感谢 [trace.moe](https://trace.moe) 本项目使用了 trace.moe 提供的图片识别番剧功能。

感谢 [media-kit](https://github.com/media-kit/media-kit) 本项目跨平台媒体播放能力来自 media-kit。

感谢 [avbuild](https://github.com/wang-bin/avbuild) 本项目使用了来自 avbuild 的树外补丁实现非标准视频流播放。

感谢 [hive](https://github.com/isar/hive) 本项目持久化储存能力来自 hive。
