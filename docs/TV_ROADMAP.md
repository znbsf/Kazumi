# Kazumi TV 开源路线图

本文件是 Kazumi TV 分支的唯一阶段计划与验收台账。项目继续遵守上游
GPL-3.0；发布前还需要确认上游图标的单独授权，或替换为本项目自有图标。

## 产品目标

在同一个开源仓库内保留 Kazumi 的配色、排版和内容结构，同时让手机与
Android TV/Google TV 各自拥有适合其输入方式的界面。共享规则解析、历史记录、
播放器和数据模型，不复制出两套不可同步的业务代码；Android 端通过 product
flavors 分别发布手机与电视安装包。

## 阶段总览

| 阶段 | 目标 | 当前状态 |
| --- | --- | --- |
| 第一阶段 | 可独立安装和使用的 Android TV 版本 | 代码完成；发布候选验收中 |
| 第二阶段 | 手机发现电视并从当前节目、来源和时间点接力播放 | 待第一阶段验收后开始 |

## 第一阶段：Android TV 版本

### 构建结构

| Flavor | 安装包 | Application ID | 启动入口 |
| --- | --- | --- | --- |
| `mobile` | Kazumi Mobile | `com.predidit.kazumi` | `LAUNCHER` |
| `tv` | Kazumi TV | `com.znbsf.kazumi.tv` | `LEANBACK_LAUNCHER` |

两个 flavor 共享 `lib/`、播放器、规则和数据层，但 Manifest、包名、应用名称和
TV Banner 分开。它们可以同时安装，也可以独立发布和更新。

本机构建命令：

```powershell
flutter build apk --debug --flavor mobile
flutter build apk --debug --flavor tv
```

Android Studio 的 Build Variants 中选择 `mobileDebug` 或 `tvDebug`；运行目标分别
使用手机 AVD 或 Google TV AVD。

### 范围

- 同一仓库分别产出手机 APK 与 TV APK，不再把两类启动入口合并到一个安装包。
- 原生识别电视设备；只在电视上锁定横屏并启用电视交互模式，不改变手机版行为。
- 保持 Kazumi Material 3 视觉语言，为十英尺观看距离增加明确的焦点描边、适度放大和自动滚动可见性。
- 首页、时间表、追番、历史等主要内容卡片可用 D-pad 聚焦和确认键打开。
- 侧边导航和搜索入口可以通过 D-pad 连续访问，返回键行为可预测。
- 播放器支持电视遥控器的确认键、方向键和媒体键；控制层显示时，方向键用于控件导航。
- 保留规则、弹幕、字幕、超分辨率和播放源能力，不通过删功能换取 TV 兼容。

### 第一阶段验收门槛

- [x] `tv` Manifest 声明 Leanback 入口且不要求触摸屏，`mobile` Manifest 只声明普通启动入口。
- [x] 真机/模拟器能识别 TV 模式；手机模式回归不被锁定横屏。
- [ ] 启动后无需鼠标即可进入推荐页、搜索、详情页、选择来源并开始播放。
- [x] 所有主要聚焦项都有清晰焦点状态，长列表移动焦点时目标保持可见。
- [x] 播放器遥控器键位和事件分发已实现并通过 widget/unit tests；真实视频源操作并入完整路径冒烟测试。
- [x] `flutter analyze` 无新增 warning，相关 widget/unit tests 通过。
- [x] Android TV APK 构建成功并安装到 TV AVD。
- [ ] 在至少一个 1080p Android TV/Google TV AVD 完成完整路径冒烟测试。
- [ ] 真实电视或盒子验证：待用户设备测试；模拟器通过不能替代这一项。

### 2026-09-04 验证记录

已观察并确认：

- 基于上游提交 `5d1569b`，开发分支为 `codex/android-tv-phase1`。
- API 36 Google TV x86_64 AVD `Kazumi_TV_API_36` 以 1920×1080 横屏识别为
  `television`；系统能通过 `LEANBACK_LAUNCHER` 查询并启动主 Activity。
- 清除应用数据后，只使用遥控器确认键完成四步引导并进入主界面；主界面焦点会恢复到
  搜索入口，确认键进入搜索页。方向键、确认键和 Back 也完成了侧栏→历史记录→返回的
  实机事件冒烟测试。
- API 35 手机 AVD `Medium_Phone_API_35` 以 1080×2400 竖屏启动最初的通用 APK，
  保留原有底部导航和手机版布局。
- TV 卡片确认键、焦点包装器、播放器确认键和媒体键事件分发有自动化测试覆盖；完整
  上游测试套件、静态分析和 Debug APK 构建通过。
- flavor 拆分后，`app-mobile-debug.apk` 与 `app-tv-debug.apk` 分别构建成功；ABI
  拆分的 mobile/TV release APK 也分别完成真实构建。
- APK 最终 Manifest 已验证：mobile 包名为 `com.predidit.kazumi`，只有普通
  `LAUNCHER`；TV 包名为 `com.znbsf.kazumi.tv`，只有 `LEANBACK_LAUNCHER`，并且
  Leanback 为必需、触摸屏为非必需。
- 独立 TV 包在 `Kazumi_TV_API_36` 冷启动并仅用确认键完成引导进入搜索；独立 mobile
  包在 `Medium_Phone_API_35` 冷启动并保持 1080×2400 竖屏。两个包没有复用彼此的
  Application ID 或启动入口。

仍待确认：

- 当前 AVD 网络无法连接规则目录和 Bangumi API，日志为 12 秒连接超时；因此详情→
  来源→真实视频播放的在线完整路径没有被误记为通过。需要在网络可用的 TV AVD 或
  真实设备补测。
- 真实电视/盒子的遥控器键码、解码能力、焦点观感和长时间播放尚未验证。

### 第一阶段非目标

- 不实现手机发现电视、配对或远程控制。
- 不实现跨公网播放接力。
- 不为 TV 单独维护规则格式或复制播放器实现。

## 第二阶段：手机与电视联动

### 用户路径

1. 手机与电视位于同一局域网，手机发现 Kazumi TV。
2. 首次通过电视配对码或二维码建立本地信任。
3. 手机播放页点击“在电视播放”。
4. 手机发送规则来源、节目、剧集、临时播放信息和当前位置。
5. 电视准备成功并 seek 到目标位置后返回确认，手机才暂停本地播放。
6. 电视持续回传播放状态；停止或切集时同步最新历史进度。

### 协议边界

播放接力消息使用可版本化的数据模型，至少包含：

- `protocolVersion`、`requestId`、配对设备标识和短期授权令牌；
- `ruleId/sourceId`、节目稳定 ID、剧集稳定 ID、标题和封面；
- 来源页面、视频/音频 URL、必要且最小化的请求头及失效时间；
- `positionMs`、时长、发送时间、弹幕与字幕标识；
- TV 的 `accepted`、`ready`、`playing` 或结构化失败响应。

电视优先按语义信息重新解析来源；手机传来的临时播放 URL 作为快速路径。不得在
日志中记录 Cookie、Authorization 或完整签名 URL。电视确认播放前手机不得停止，
避免出现两端都不播放的失败状态。

### 第二阶段验收门槛

- [ ] 同一局域网自动发现、显式配对、取消配对和令牌轮换。
- [ ] 从手机当前节目、当前来源、当前集和当前时间点接力到电视。
- [ ] DASH 分离音视频、HLS、需要 Referer/Cookie 的来源都有失败回退。
- [ ] 手机可控制电视播放/暂停、seek、切集和停止。
- [ ] 断线重连、重复请求幂等、URL 过期和电视忙状态均有明确行为。
- [ ] 电视退出播放时将最终进度同步回手机，冲突按更新时间和播放会话处理。
- [ ] 敏感请求头不落盘、不进日志，局域网未配对设备不能发起播放。

## 维护约定

- 上游远端使用 `upstream`，个人 fork 使用 `origin`。
- TV 改动优先做成可检测、可复用的适配层，避免长期偏离上游。
- 每个验收项只在有构建、测试或人工路径证据后勾选。
- 模拟器结果、真实设备结果和仅静态审查结果必须分开记录。
