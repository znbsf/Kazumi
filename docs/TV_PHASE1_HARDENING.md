# 第一阶段收尾：本轮证据与接口登记

2026-09-12，A 线独立工作树 `4b4d/Kazumi`，私有候选分支
`codex/tv-phase1-hardening`。起点 `95a629f9bcccc5c898412ac712b14a68132af1f1`，
不含 P2。本文保留过程记录；其中“待做”“尚未 rebase”是阶段快照。
本轮最终结果、当前 SHA、覆盖边界及交付物以 [TV_PHASE1_DELIVERY.md](TV_PHASE1_DELIVERY.md) 为准。

## 当前基线

- 全量 `flutter test --no-pub`：264 项通过（本轮重新执行，45 秒）。
  原始日志：`artifacts/phase1-hardening/baseline-tests.log`。
- 本工作树生成 Android local.properties，复用现有 Flutter 3.47.2 / Android SDK；
  未升级共享工具链。`pub get` 解析依赖成功，但桌面插件 symlink 阶段提示需要 Developer Mode。
  Android `--no-pub` 构建已进入 Gradle；该提示不等于构建成功或全局配置已修复。
- A 独占 `Kazumi_Focus_UI_API36 / emulator-5562`，Google TV API 36、x86_64、
  1920×1080、density 320、2048 MiB RAM，headless、无音频、SwiftShader。
  未连接真机；不使用其他任务的 APK 作为本轮产物。

后续纠正：旧 AVD 的 versionCode 203028 拒绝安装默认 20300 的新包（未覆盖）。
已正常停止本任务启动的旧 AVD并保留数据，改建 `Kazumi_Phase1_A_API36 / emulator-5562`，
数据放在本工作树 `artifacts/phase1-hardening/avd`。以下本轮验证均使用新 AVD。

第一次 TV x64 release 构建成功（244.9 秒）但**启动黑屏，不可作为 UI 验收包**：
`baseline-tv-95a629f.apk` SHA-256
`9c29f809e9ded9c0feed87f7dae5ea05bfbe0423f0e5e50ea93be344ac68cc9e`。
logcat 确认 `GeneratedPluginRegistrant` 缺失，原因为 pub 的桌面 symlink 步骤提前失败。
根据当前 Flutter 工具实现，仅为工作树 windows/linux 的 ephemeral 插件目录创建 junction，
再次 `pub get --offline` 成功且 Android registrant 存在；没有修改共享 SDK或系统设置。
原始业务源码重建与实际启动复核仍在进行。

重建成功（48.5 秒）：正常入口、纯 `95a629f` 业务源码，versionCode 20300，
`baseline-tv-95a629f-registered.apk`，SHA-256
`774561ed264ee9a74b2310f40c7e273fd09ee75ace09b0404350b0ce5192eb5b`。
构建时暂存并还原了未提交的 menu/search 修复；修复没有进入此包。
registrant 存在，独立 AVD 安装成功、实际完成四步初始化并进入首页。

### 正常包的实际截图与按键观察

以下路径位于 `artifacts/phase1-hardening/`；每张截图有同名 JSON 记录 serial、版本、时间与按键。

| 截图 | 观察与边界 |
| --- | --- |
| baseline-onboarding-registered.png | 首次启动正确绘制，OK 可继续；黑屏生成问题已排除 |
| baseline-update-source.png | 实际仍显示 GitHub/F-Droid，UI-12 文案待修，与 fork 更新机制不一致 |
| baseline-cold-ok.png | 正常包冷启动后直接 OK 无动作；方向键 LEFT/UP 后才能进入搜索 |
| baseline-search-page.png | 搜索入口有可见描边，进入页面不主动弹键盘，OK 才进入编辑 |
| baseline-search-result.png | 日志为 12014ms connectionTimeout，页面却显示“什么都没有找到”，复现 UI-10 |
| baseline-history.png | 空历史布局可见，返回按钮存在；不代表非空历史验收 |
| baseline-timeline-dialog-back.png | 时间机器对话框 BACK 可关闭，页面显示网络失败；内容未加载 |
| baseline-collect-verified.png | 追番空态布局；页面切换后的直接 DOWN 没有按预想继续侧栏，仍需焦点出口复核 |
| baseline-settings-list-touch-entry.png | 通过点击进入设置后，用方向键操作；此图不证明设置入口全程 D-pad 可达 |
| baseline-settings-scroll.png | 设置内容区 DOWN 会滚动显露下方项目；未激活开关 |
| baseline-home-720-large-light.png | 1280×720 / 240dpi / 1.3 字体，重启后浅色首页布局；在线列表未加载 |

较早的 `baseline-collect.png`、`baseline-settings.png` 等尝试名称不对应最终页面，
不得据名称计为通过。动态修改分辨率后的设置截图不替代重启后稳定尺寸验收。

### 网络与本地媒体边界

本轮 AVD 正常包的首页、规则、搜索均连接超时。宿主现有代理只读 HEAD 返回 200；
该结果不能证明 AVD 网络恢复。专用 AVD 的 Android http_proxy 设置为 `10.0.2.2:10646`
后 Flutter 请求仍超时；后续“清除该设置并以进程代理参数重启”的命令被执行策略拒绝，
命令未执行，没有换路径绕过。当前 AVD 仍保留该测试设置，其他设备/宿主配置未改。

本地媒体包已运行：测试入口在忽略目录，复用正式 appModule、VideoPage、MediaKit 和历史仓库；
Hive 使用 `phase1-hardening-native-only`，不改正常应用历史。
新生成 90 秒 640×360 / 30fps / H.264 / 48kHz AAC 测试素材，SHA-256
`371dd27908aaca09ff91c12a3ae0d3fda630421ee543fb4d45da136c8604c1d5`。
该素材不代表真实来源、实际扬声器音画同步或长播验收。日志同时记录乐观 UI 位置与原生位置。

原版本地媒体 APK 为 `baseline-local-95a629f.apk`，版本 20300，SHA-256
`490c211d1ab426ab8ac0b798e1fc09864986fe2643f8938d5b9bb8c6db69abe7`。
构建时正式源码恢复为 95a629f，仅使用独立测试入口；不等同正常发行 APK。
`baseline-local-runtime.log` 复现 UI-13：05:44:38 历史保存 21500ms，
05:44:39 暂停时原生位置 21800ms，05:44:55 两次快进到原生 41800ms；
持续暂停未再写历史，退出后 05:46:52 重开从原生 21000ms 起播。
`baseline-local-controls.png` 同时可见测试帧 41.800、暂停控件与 0:41。
Google TV 系统设置遮罩曾打断一次操作，遮罩截图不算播放器验收。

## 本轮新增自动回归

- 冷启动：原始 shell 首焦点落在 `_ModalScopeState`，搜索按钮未持焦点，新增探针失败。
  仅在启动下一帧仍为内容区空作用域时补焦点；不从用户已到达的控件抢焦点。
- UI-10：真实搜索控制器配合 Dio 请求边界替身，成功空结果与失败后成功空结果均复现旧错误态。
  分开请求失败与空结果，分页失败保留已有卡片与重试入口。
- 两项修复合计 27 项焦点/搜索专项通过，5 个相关 Dart 文件静态分析无问题。
  本轮网络替身测试不证明源站在线可用；修复尚未经过实际 APK 逐屏验收。
- 上游最新搜索控制器已经提供请求代次保护；后续 rebase 必须保留，不重复实现。
- UI-13：每个原生播放器会话独立记录是否曾播放，定时、后台和销毁时捕获原生位置。
  只在初始化成功后启用；闭包固定该会话的历史身份，保存串行化且允许暂停后倒退到零。
  新增 8 项录制器测试和既有 5 项历史仓库测试通过，全量 276 项通过。
  这些单元测试不替代修复 APK 的原生暂停 seek / 退出重开回归。
- UI-12：TV 引导更新页改为 fork 发行页手动下载说明，手机原选项保留。
- 首次全量分析误扫证据目录内独立上游 Dart 副本，产生 21 项报告；副本已改为
  `.dart.txt` 后重新分析，不通过修改正式分析规则隐藏问题。
  重跑结果：0 error / 0 warning，5 条既有 `avoid_print` info（logger 三处、WebView 两处）；
  分析命令因 info 返回非零，不能写成“所有 lint 清零”。

### rebase 前本地修复提交

| 提交 | 范围 | 验证状态 |
| --- | --- | --- |
| 41f1b62 | 冷启动首焦点与对应组件用例 | 原版 AVD 复现；修复组件通过，修复 APK 待验 |
| 5b5ff60 | 搜索失败/空结果/分页失败分离 | 原版 AVD 超时错空态；控制器与界面测试通过 |
| 61159fd | 暂停原生进度、会话身份与最终保存 | 原版原生播放复现；录制器单元通过，修复 APK 待验 |
| 6317c3d | TV 更新渠道说明 | 原版引导截图确认；静态分析通过，修复布局待验 |

这些 SHA 是私有检查点，尚非 B 集成基线。rebase 后须重新报告 SHA。

### 修复 APK 本轮 AVD 对照

源码 `bbc7523a6f0d9ccb5f44f3ebc00061dcb5f939c1`，versionCode 20300，注册器存在。

- 正常入口 `fixed-tv-bbc7523.apk`，SHA-256
  `bfc0a9738d0e01c0dcc514e4cd0e430c083de4ed5ee23e82ccad765fed22e559`。
  `fixed-normal-cold-ok.png`：冷启动直接 OK 进入搜索，无 IME，有焦点描边。
  `fixed-normal-search-failure.png`：真实超时显示请求失败；DOWN 到重试、OK 重新加载
  见 `fixed-normal-retry-start.png`。原版与修复版网络均未恢复。
- 本地原生入口 `fixed-local-bbc7523.apk`，SHA-256
  `79fbcf6bc3eeebd6b4d01ebe034037e2c17158181d3cfd27c6a6d5109f472506`。
  06:11:29 暂停 38400ms；两次快进后原生与历史依次 48400/58400ms，退出保留。
  06:12:21 重开原生起始 58000ms（现有整秒 offset）；不再恢复旧 38 秒进度。
  但重开 58 秒时黑屏/位置停滞，换回原版同素材同进度也复现
  （`original-local-same-resume.log`）。素材完整解码无错，设备副本 SHA 与源相同。
  因此暂停快进写入及重开偏移有证据，完整连续续播、回退零、切集/后台仍未通过本轮原生验收。
  本轮通过点击历史卡片进入，不把这一路径宣称为全程 D-pad。
- 离线正式页面：`baseline-ui-v2-95a629f.apk` SHA-256
  `31ad49cb43e4b06bdd6248c73cca5bfc08f318ee4decb02b1e4a467923bfac0f`；
  `fixed-ui-v3-bbc7523.apk` SHA-256
  `bffaff51394427273b9274f66c938c184083bd8eb49f35eeed2fef065cdf1335`。
  原版/修复均可数字 55 + OK 打开正式详情；普通方向键滚动到 61，再 UP 回 55 时整卡显露。
  详情返回恢复在此前可见卡片内有效，但直接数字跳到未显示卡片后返回会回原有视口，
  不将该路径误写成返回第 55 项通过。选源空面板可 BACK，真实来源不在离线响应中。
  原版非空时间表首卡可遥控进入；修复 720p/240dpi/1.3 字体浅色时间表布局有截图。
  V1 夹具误读 offset 导致重复页，已修正并重建为 V2；V1 不作长列表证据。
  V3 仅增独立路由文件以选择测试页，正常默认路由与合成数据不变。
- V3 测试入口直接打开引导时 bundled rules 初始化出现 null 断言，未到达更新页。
  此入口不等同正常 main 的初始化流程，不能据此认定正常首次启动失败，亦不能认定更新页 AVD 已验收。
- AVD 显示设置已恢复 1080p/默认 density/字体 1/深色，测试路由恢复首页，已停止并交回重资源。

首次修复包构建包装脚本因 PowerShell 将原生 stderr 提醒当成终止错误而退出；
Gradle 仍完成。随后修正包装脚本并补跑，22.8 秒成功退出后才复制和安装修复 APK。
每个构建 JSON 分别登记 source/target/targetSha256/apkSha256，失败记录不替代成功记录。

### 上游小型 PR 拆分草案（事实材料，不是可直接提交的描述）

| 顺序 | 问题与候选范围 | 依赖与排除 |
| --- | --- | --- |
| 1 | 搜索请求失败与成功空结果区分，保留分页卡片与重试 | 保留上游已有代次/取消保护；移除对 TV 测试夹具的测试依赖后可单独审查 |
| 2 | 暂停 seek 后历史丢失，播放器会话绑定原生最终进度 | 通用播放器修复；不含遥控/品牌/P2；需原生退出与切集证据 |
| 3 | Android TV 构建/平台探测/导航基础 | flavor、manifest、MainActivity、TvMode/导航服务、基础焦点组件；默认 mobile 行为须回归 |
| 4 | 首页、搜索、历史、详情的逐页 D-pad 导航 | 依赖 3；LCN/焦点恢复与布局测试随所属页面提供，拆分按维护者反馈调整 |
| 5 | 播放器遥控与面板焦点 | 依赖 3；与通用历史修复分开，需原生键盘/返回/选集回归 |

不进入通用候选补丁：fork README/下载链接/品牌图、个人 release workflow、
预览发行文案/截图、开发代理覆盖、演示弹幕及凭据、fork 自动更新跳转、P2 接力。
跨文件混合改动不能按整文件照搬；后续以 rebase 后逐 hunk 审查产出补丁。

## 优先复核项

| 项目 | 本轮源码事实 | 尚需验证 |
| --- | --- | --- |
| 冷启动首焦点 | NavigationRail 的搜索 FAB 已声明 TV autofocus | 普通 APK 冷启动是否显露，是否被异步数据或对话框抢走 |
| UI-10 | SearchPageController 将请求失败与空结果合并为 isTimeOut | 网络失败/空结果/分页失败的实际界面、重试与焦点 |
| UI-13 | PlayerItem 定时器只在 playerPlaying 时写历史；dispose 无最终保存 | 暂停 seek、退出、切集、后台与实际原生位置，不以乐观 currentPosition 替代 |

全部页面仍需本轮截图/按键基线和 rebase 后二次验收；旧截图不作为本轮通过证据。

### 覆盖矩阵（进行中）

| 场景 | 原版实际 AVD | 自动化/准备 | 修复版及 rebase 后 |
| --- | --- | --- | --- |
| 冷启动直接 OK / 搜索进入不弹键盘 | 首焦点失败已复现；手动恢复后搜索入口可达 | 原版失败、修复组件通过 | 待做 |
| 搜索失败 / 空 / 分页失败 | 真实超时误显示为空已复现 | 请求边界及正式 SearchPage 用例通过 | 待做 |
| 首页分类 / LCN / 长列表双向滚动 | 正常包网络未加载，不能验收列表 | 现有组件回归通过；独立合成响应入口已通过分析 | 待做 |
| 详情 / 选源 / 返回恢复 | 正常包网络未加载 | 离线正式路由入口可供布局检查；不提供真实来源 | 待做 |
| 非空历史续播 / 播放暂停 seek | 正式历史服务与原生播放器已运行，UI-13 复现 | 会话录制器 8 项通过 | 待做 |
| 设置 / 时间表 / 追番 | 设置滚动、时间对话框 BACK、追番空态有截图；不等于全程 D-pad 通过 | 组件/源码复核；非空列表待合成入口 | 待做 |
| 分辨率 / 字体 / 主题 | 720p/240dpi/1.3 字体浅色首页有稳定截图，1080p 深色多页 | 动态改尺寸的过渡图不纳入通过项 | 待做 |

UI-05～UI-09（侧栏命名、续播文案、底栏精简、下载入口、双栏选源）和 UI-11
仍按既有路线图作为体验建议登记，不因本轮缺陷修复自动宣称完成。
UI-12 的更新文案修复仅覆盖其中一项；次级页面系统回归仍单独记录。

## 上游核验与提交前门槛

本轮 `git ls-remote upstream refs/heads/main` 为
`2624e0c0f4a0be2be658f833a510090cde8b5cf5`，尚未执行 rebase。
已通过 GitHub API 读取该 SHA 的 `static/doc/CONTRIBUTING.md` 和 Issue #2500 全部评论。
Issue 仍开放，仅有 znbsf 的 fork 认领，未见维护者批准。

材料按通用缺陷、TV 平台基础、逐页 D-pad、播放器控制分别组织；
排除 fork 品牌/README 下载/个人发行配置/演示弹幕/QuickSR/P2。
最终 PR 描述由用户本人填写，并披露确切 AI 模型；不猜测历史模型。
维护者批准及用户评审为提交前门槛，不阻止本地修复。本任务不提交 PR 或发布。

## 给 B 的兼容约定

新增可选 `PlaybackInitParams.onHistoryProgress`，默认 null，已有调用方兼容；
`OnlineVideoPlaybackArgs` 未改。VideoPage 为线上/离线入口绑定会话身份回调，
由原生播放器所有者捕获位置并在销毁前留存最终快照。
A 主责通用播放器、历史与 TV UI；B 独立开发配对/传输。
后续如改变 VideoPage 或历史接口，记录默认行为、迁移方式、用例和提交 SHA。
P2 的可选显式起播需保留 0 进度有效、1-based episode / 0-based road，
实际播放确认仍必须绑定原生会话；不得因准备参数或乐观 seek 写历史/成功回执。
