# Android TV 开源播放器参考

这份记录用于约束 Kazumi TV 的播放器交互：保留 Kazumi 的 Material 风格和
libmpv/media-kit 播放内核，只吸收适合十英尺界面、遥控器和可验证播放状态的设计。

## 参考项目与采用结论

| 项目 | 可复用设计 | Kazumi TV 处理 |
| --- | --- | --- |
| [Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv/blob/master/app/src/main/java/org/jellyfin/androidtv/ui/playback/CustomPlaybackOverlayFragment.java) | 在播放层集中处理播放、暂停、快进、快退、停止、返回和手柄键 | 已采用集中语义动作，但继续由 Flutter 控件管理可见焦点 |
| [Nova Video Player](https://github.com/nova-video-player/aos-AVP/wiki/Keyboard-shortcuts) | 遥控器、键盘和电视菜单使用同一套动作；方向键与菜单显示状态有关 | 已采用“控件隐藏时显示、控件显示时交给焦点系统” |
| [Nova 发行记录](https://github.com/nova-video-player/aos-AVP/releases) | TV 音量键交回系统，以兼容 CEC、ARC/eARC 和功放 | 已采用；音量键仍在键表中说明，但不被播放器消费 |
| [mpv stats overlay](https://github.com/mpv-player/mpv/blob/master/DOCS/man/stats.rst) | 状态、帧时序、缓存、轨道和按键分页面展示 | 已采用轻量版本：状态、日志、遥控器三页，不叠加另一套播放器皮肤 |
| [AFinity](https://github.com/MakD/AFinity) | libmpv 播放统计、硬件解码与视频输出配置可见 | 已采用运行态解码通路、VO、GPU context、帧率、缓存和丢帧信息 |
| [Just (Video) Player](https://github.com/moneytoo/Player) | Android TV 自动帧率匹配、tunneled playback、简洁控制层 | 仅作为后续真机实验参考；不以 Media3 替换 libmpv |
| [SmartTube](https://github.com/yuliskov/SmartTube) | TV 键位重映射和手机遥控/投送交互 | 键位帮助已采用；手机联动留在第二阶段 |

Android 官方还要求 TV 播放控件遵循一致的媒体键行为：
[Playback controls on TV](https://developer.android.com/training/tv/playback/controls) 和
[Manage TV controllers](https://developer.android.com/training/tv/get-started/controllers)。

## 本轮补充

- `INFO` 或蓝色功能键打开“视频详情”的状态页；
- `HELP`、`MENU`、右键菜单或 `F1` 直接打开“遥控器”页；
- 状态页读取 mpv 的实际运行属性，而不是只显示设置值：
  `hwdec-current`、`hwdec-interop`、`current-vo`、`current-gpu-context`、
  像素格式、估算帧率、缓存时长和丢帧；
- 音量键交由 Android TV 系统处理，避免播放器吞掉 CEC/ARC 音量事件；
- 控制层沿用现有播放器配色和结构，只在 TV 增加焦点描边、紧凑半透明选集面板及
  显式的顶栏/底栏 D-pad 焦点拓扑。

## 硬件解码边界

Android 解码器仍使用 `auto-safe`。播放器根据 flavor/设备选择视频输出：TV 的
“自动”使用 `mediacodec_embed`，让 MediaCodec 直接输出到 Android Surface；手机版
继续按 Android 版本使用原有 `gpu` / `gpu-next` 逻辑，用户显式选择也始终优先。

直连 Surface 不支持 Anime4K 超分辨率，并会限制部分 mpv 视频滤镜/OSD 合成，因此
代码会阻止这组不兼容配置。保留 libmpv 是产品边界，不为 tunneled playback 等能力
替换成 Media3。实现依据还包括 mpv 的
[Android video output 文档](https://github.com/mpv-player/mpv/blob/master/DOCS/man/vo.rst)
和 media-kit 的 Android Surface/MediaCodec 问题记录
[#1243](https://github.com/media-kit/media-kit/issues/1243)、
[#1391](https://github.com/media-kit/media-kit/issues/1391)。

状态页中的 `hwdec-current=mediacodec` 或 `mediacodec-copy` 证明当前流进入
MediaCodec 解码通路；模拟器的 MediaCodec 仍可能由宿主机或软件组件实现，不能当作
实体电视芯片硬解证明。既有真机 A/B 只支持“直连 Surface 解决了所测 1080p 速度问题”
这一结论，4K/HDR、功耗和长时间稳定性仍需单独验收。

## 弹幕开放平台边界

弹弹 play 的[开放平台文档](https://doc.dandanplay.com/open/)说明 AppId/AppSecret、
时间戳和签名头是必要条件，HTTP 403 也覆盖缺失或错误的认证信息；接口可在
[官方 Swagger](https://api.dandanplay.net/swagger/index.html)查看。文档同时建议开源
客户端不要硬编码共享密钥，可使用构建时占位替换或自有服务端转发。

Kazumi TV 因此采用 `DANDANAPI_APPID` / `DANDANAPI_KEY` 的 `dart-define` 注入：
凭证不完整时在请求前失败并显示明确提示。时间轴调度另行解决暂停/慢速重复发送、
短暂快进漏发和 seek 后旧延迟任务污染新时间点的问题；这些行为由单元测试覆盖。

## 暂不直接移植

- 自动刷新率匹配：需要验证 libmpv 与各品牌电视的 display-mode 行为；
- tunneled playback：属于 Media3/设备能力路线，不应为了它替换 Kazumi 的播放内核；
- 强制拦截系统保留键：普通应用收不到的键不能靠 Flutter 或 Activity 强抢；
- 大型常驻统计叠层：会遮挡动画与弹幕，当前使用按需打开的详情页。
